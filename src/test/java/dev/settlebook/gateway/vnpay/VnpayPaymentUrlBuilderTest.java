package dev.settlebook.gateway.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.ErrorResponseException;

import dev.settlebook.payment.Gateway;
import dev.settlebook.payment.PaymentAttempt;
import dev.settlebook.payment.PaymentAttemptStatus;

class VnpayPaymentUrlBuilderTest {

	private final VnpayProperties properties = new VnpayProperties("TESTTMN1", "test-secret",
			"https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "http://localhost:8080/api/vnpay/return");

	private final VnpaySigner signer = new VnpaySigner(properties.hashSecret());

	private final VnpayPaymentUrlBuilder builder = new VnpayPaymentUrlBuilder(properties, signer);

	@Test
	void buildsSignedUrlWithScaledAmountAndGmtPlus7Dates() {
		// 17:30 UTC is 00:30 the next day in Vietnam: the date must roll over.
		Instant now = Instant.parse("2026-09-28T17:30:00Z");
		String url = builder.build(attempt(150_000), now, now.plusSeconds(900), "203.0.113.7");

		assertThat(url).startsWith(properties.payUrl() + "?");
		Map<String, String> params = queryParams(url);
		assertThat(params)
			.containsEntry("vnp_Amount", "15000000")
			.containsEntry("vnp_CreateDate", "20260929003000")
			.containsEntry("vnp_ExpireDate", "20260929004500")
			.containsEntry("vnp_TmnCode", "TESTTMN1")
			.containsEntry("vnp_IpAddr", "203.0.113.7")
			.containsEntry("vnp_CurrCode", "VND")
			.containsEntry("vnp_Version", "2.1.0");
		assertThat(params.get("vnp_OrderInfo")).matches("[A-Za-z0-9 ]+");
		assertThat(signer.isValid(params)).isTrue();
	}

	@Test
	void rejectsAmountBeyondVnpayFieldLimit() {
		assertThatThrownBy(() -> builder.build(attempt(VnpayPaymentUrlBuilder.MAX_AMOUNT_VND + 1), Instant.now(),
				Instant.now().plusSeconds(900), "127.0.0.1"))
			.isInstanceOf(ErrorResponseException.class);
	}

	private static PaymentAttempt attempt(long amountVnd) {
		UUID id = UUID.randomUUID();
		return new PaymentAttempt(id, UUID.randomUUID(), Gateway.VNPAY, id.toString().replace("-", ""), amountVnd,
				PaymentAttemptStatus.PENDING, Instant.now());
	}

	private static Map<String, String> queryParams(String url) {
		Map<String, String> params = new HashMap<>();
		for (String pair : URI.create(url).getRawQuery().split("&")) {
			String[] kv = pair.split("=", 2);
			params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
		}
		return params;
	}

}
