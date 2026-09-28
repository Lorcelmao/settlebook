package dev.settlebook.gateway.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.settlebook.TestcontainersConfiguration;

/**
 * Payment start, IPN and browser return against a real PostgreSQL container. Callbacks are signed with
 * the test secret exactly as VNPay would sign them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VnpayPaymentFlowIT {

	private static final long AMOUNT_VND = 150_000;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	VnpaySigner signer;

	@Test
	void startedPaymentReturnsUrlSignedForVnpay() {
		UUID orderId = createOrder();
		MvcTestResult started = mvc.post().uri("/api/orders/{id}/payments/vnpay", orderId).exchange();

		assertThat(started).hasStatus(HttpStatus.CREATED);
		String paymentUrl = json(started, "$.paymentUrl");
		assertThat(paymentUrl).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
		assertThat(paymentUrl).contains("vnp_TxnRef=" + json(started, "$.txnRef"), "vnp_Amount=15000000",
				"vnp_SecureHash=");
	}

	@Test
	void successfulIpnPaysOrderAndDuplicateCallbackChangesNothing() {
		UUID orderId = createOrder();
		String txnRef = startPayment(orderId);
		Map<String, String> callback = signedCallback(txnRef, AMOUNT_VND * 100, "00", "00");

		assertIpnReply(callback, "00");
		assertThat(attemptStatus(txnRef)).isEqualTo("SUCCEEDED");
		assertThat(orderStatus(orderId)).isEqualTo("PAID");
		String firstUpdate = attemptUpdatedAt(txnRef);

		// Duplicate callback: VNPay delivers the same event again. Acknowledged, nothing changes.
		assertIpnReply(callback, "02");
		assertThat(attemptStatus(txnRef)).isEqualTo("SUCCEEDED");
		assertThat(attemptUpdatedAt(txnRef)).isEqualTo(firstUpdate);
	}

	@Test
	void distinctSecondChargeForPaidOrderIsNotAcknowledgedOrSilentlyRecorded() {
		UUID orderId = createOrder();
		String firstTxnRef = startPayment(orderId);
		String secondTxnRef = startPayment(orderId);
		assertIpnReply(signedCallback(firstTxnRef, AMOUNT_VND * 100, "00", "00"), "00");

		// Duplicate charge: a different gateway transaction for the same, already paid order. The buyer
		// really paid twice. Until the correctness core records it as funds held, it is rejected with 99
		// so VNPay keeps retrying, and nothing is written.
		assertIpnReply(signedCallback(secondTxnRef, AMOUNT_VND * 100, "00", "00"), "99");
		assertThat(attemptStatus(secondTxnRef)).isEqualTo("PENDING");
		assertThat(attemptStatus(firstTxnRef)).isEqualTo("SUCCEEDED");
		assertThat(orderStatus(orderId)).isEqualTo("PAID");
	}

	@Test
	void failedPaymentIsRecordedAndOrderCanBePaidAgain() {
		UUID orderId = createOrder();
		String txnRef = startPayment(orderId);

		assertIpnReply(signedCallback(txnRef, AMOUNT_VND * 100, "24", "02"), "00");
		assertThat(attemptStatus(txnRef)).isEqualTo("FAILED");
		assertThat(orderStatus(orderId)).isEqualTo("AWAITING_PAYMENT");
		assertThat(mvc.post().uri("/api/orders/{id}/payments/vnpay", orderId).exchange())
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void invalidSignatureIsRejectedWithoutChange() {
		UUID orderId = createOrder();
		String txnRef = startPayment(orderId);
		Map<String, String> forged = signedCallback(txnRef, AMOUNT_VND * 100, "00", "00");
		forged.put("vnp_SecureHash", new VnpaySigner("attacker-guess").sign(forged));

		assertIpnReply(forged, "97");
		assertThat(attemptStatus(txnRef)).isEqualTo("PENDING");
		assertThat(orderStatus(orderId)).isEqualTo("AWAITING_PAYMENT");
	}

	@Test
	void unknownReferenceAndWrongAmountAreRejectedWithoutChange() {
		assertIpnReply(signedCallback("ffffffffffffffffffffffffffffffff", AMOUNT_VND * 100, "00", "00"), "01");

		UUID orderId = createOrder();
		String txnRef = startPayment(orderId);
		assertIpnReply(signedCallback(txnRef, (AMOUNT_VND - 1) * 100, "00", "00"), "04");
		assertThat(attemptStatus(txnRef)).isEqualTo("PENDING");
		assertThat(orderStatus(orderId)).isEqualTo("AWAITING_PAYMENT");
	}

	@Test
	void browserReturnReportsRecordedStateAndNeverChangesIt() {
		UUID orderId = createOrder();
		String txnRef = startPayment(orderId);

		MvcTestResult result = withParams(mvc.get().uri("/api/vnpay/return"),
				signedCallback(txnRef, AMOUNT_VND * 100, "00", "00")).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.gatewayReportsSuccess").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.attemptStatus").isEqualTo("PENDING");
		assertThat(attemptStatus(txnRef)).isEqualTo("PENDING");
		assertThat(orderStatus(orderId)).isEqualTo("AWAITING_PAYMENT");
	}

	@Test
	void paymentCannotStartForPaidOrExpiredOrder() {
		UUID paidOrder = createOrder();
		assertIpnReply(signedCallback(startPayment(paidOrder), AMOUNT_VND * 100, "00", "00"), "00");
		assertThat(mvc.post().uri("/api/orders/{id}/payments/vnpay", paidOrder).exchange())
			.hasStatus(HttpStatus.CONFLICT);

		UUID expiredOrder = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO orders (id, amount_vnd, description, created_at, expires_at)
				VALUES (:id, 150000, 'expired', now() - interval '1 hour', now() - interval '45 minutes')
				""").param("id", expiredOrder).update();
		assertThat(mvc.post().uri("/api/orders/{id}/payments/vnpay", expiredOrder).exchange())
			.hasStatus(HttpStatus.CONFLICT);
	}

	private UUID createOrder() {
		MvcTestResult created = mvc.post().uri("/api/orders")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amountVnd\": " + AMOUNT_VND + ", \"description\": \"Test order\"}")
			.exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(json(created, "$.id"));
	}

	private String startPayment(UUID orderId) {
		MvcTestResult started = mvc.post().uri("/api/orders/{id}/payments/vnpay", orderId).exchange();
		assertThat(started).hasStatus(HttpStatus.CREATED);
		return json(started, "$.txnRef");
	}

	/** The parameters VNPay sends to the IPN URL, signed with the configured (test) secret. */
	private Map<String, String> signedCallback(String txnRef, long scaledAmount, String responseCode,
			String transactionStatus) {
		Map<String, String> params = new HashMap<>();
		params.put("vnp_TmnCode", "TESTTMN1");
		params.put("vnp_TxnRef", txnRef);
		params.put("vnp_Amount", Long.toString(scaledAmount));
		params.put("vnp_ResponseCode", responseCode);
		params.put("vnp_TransactionStatus", transactionStatus);
		params.put("vnp_TransactionNo", "14512345");
		params.put("vnp_BankCode", "NCB");
		params.put("vnp_CardType", "ATM");
		params.put("vnp_OrderInfo", "Thanh toan don hang " + txnRef);
		params.put("vnp_PayDate", "20260929003105");
		params.put("vnp_SecureHash", signer.sign(params));
		return params;
	}

	private void assertIpnReply(Map<String, String> callback, String expectedRspCode) {
		MvcTestResult result = withParams(mvc.get().uri("/api/vnpay/ipn"), callback).exchange();
		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.RspCode").isEqualTo(expectedRspCode);
	}

	private static MockMvcRequestBuilder withParams(MockMvcRequestBuilder request, Map<String, String> params) {
		params.forEach(request::param);
		return request;
	}

	private String attemptStatus(String txnRef) {
		return jdbc.sql("SELECT status FROM payment_attempts WHERE txn_ref = :ref").param("ref", txnRef)
			.query(String.class).single();
	}

	private String attemptUpdatedAt(String txnRef) {
		return jdbc.sql("SELECT updated_at::text FROM payment_attempts WHERE txn_ref = :ref").param("ref", txnRef)
			.query(String.class).single();
	}

	private String orderStatus(UUID orderId) {
		return jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", orderId)
			.query(String.class).single();
	}

	private static String json(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), path);
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
