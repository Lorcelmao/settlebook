package dev.settlebook.gateway.vnpay;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.settlebook.order.Order;
import dev.settlebook.order.OrderService;
import dev.settlebook.payment.ApplyOutcome;
import dev.settlebook.payment.ChargeOnUnpayableOrderException;
import dev.settlebook.payment.Gateway;
import dev.settlebook.payment.GatewayResult;
import dev.settlebook.payment.PaymentAttempt;
import dev.settlebook.payment.PaymentService;

@RestController
class VnpayController {

	private static final Logger log = LoggerFactory.getLogger(VnpayController.class);

	private final PaymentService payments;
	private final OrderService orders;
	private final VnpaySigner signer;
	private final VnpayPaymentUrlBuilder urlBuilder;
	private final Clock clock;

	VnpayController(PaymentService payments, OrderService orders, VnpaySigner signer,
			VnpayPaymentUrlBuilder urlBuilder, Clock clock) {
		this.payments = payments;
		this.orders = orders;
		this.signer = signer;
		this.urlBuilder = urlBuilder;
		this.clock = clock;
	}

	/** Starts a VNPay attempt and returns the URL the buyer's browser should be sent to. */
	@PostMapping("/api/orders/{orderId}/payments/vnpay")
	ResponseEntity<StartedPayment> start(@PathVariable UUID orderId, HttpServletRequest request) {
		PaymentAttempt attempt = payments.startAttempt(orderId, Gateway.VNPAY);
		Order order = orders.get(orderId);
		String paymentUrl = urlBuilder.build(attempt, clock.instant(), order.expiresAt(), request.getRemoteAddr());
		return ResponseEntity.created(URI.create("/api/orders/" + orderId))
			.body(new StartedPayment(attempt.id(), attempt.txnRef(), attempt.amountVnd(), paymentUrl));
	}

	/**
	 * IPN: VNPay's server-to-server callback, the only request that changes payment state. The reply code
	 * decides whether VNPay retries: 00 and 02 stop retries; 01, 04, 97, 99 and timeouts trigger them.
	 * The reply is sent only after the database transaction has committed.
	 */
	@GetMapping("/api/vnpay/ipn")
	Map<String, String> ipn(@RequestParam Map<String, String> params) {
		String txnRef = params.get("vnp_TxnRef");
		if (!signer.isValid(params)) {
			log.warn("VNPay IPN rejected: invalid checksum (txnRef={})", txnRef);
			return reply("97", "Invalid signature");
		}
		try {
			ApplyOutcome outcome = payments.applyGatewayResult(VnpayCallbacks.toResult(params));
			log.info("VNPay IPN {}: {}", txnRef, outcome);
			return switch (outcome) {
				case APPLIED -> reply("00", "Confirm Success");
				// A duplicate callback (the same event delivered again) lands here and changes nothing.
				case ALREADY_APPLIED -> reply("02", "Order already confirmed");
				case ATTEMPT_NOT_FOUND -> reply("01", "Order not found");
				case AMOUNT_MISMATCH -> reply("04", "Invalid amount");
			};
		}
		catch (ChargeOnUnpayableOrderException ex) {
			// A distinct second charge or a late payment, not a replay: keep VNPay retrying rather than
			// acknowledging money that has not been recorded.
			log.error("VNPay IPN {} not applied: {}", txnRef, ex.getMessage());
			return reply("99", "Unknown error");
		}
		catch (RuntimeException ex) {
			log.error("VNPay IPN {} failed; VNPay will retry", txnRef, ex);
			return reply("99", "Unknown error");
		}
	}

	/**
	 * Where VNPay sends the buyer's browser after payment. Display only: it never changes state, because
	 * the browser can be closed, replayed or forged. It reports what the IPN has recorded so far.
	 */
	@GetMapping("/api/vnpay/return")
	PaymentReturn returnPage(@RequestParam Map<String, String> params) {
		if (!signer.isValid(params)) {
			throw badRequest("Invalid VNPay checksum");
		}
		GatewayResult reported;
		try {
			reported = VnpayCallbacks.toResult(params);
		}
		catch (IllegalArgumentException ex) {
			throw badRequest(ex.getMessage());
		}
		PaymentAttempt attempt = payments.findAttempt(Gateway.VNPAY, reported.txnRef())
			.orElseThrow(() -> badRequest("Unknown payment reference " + reported.txnRef()));
		Order order = orders.get(attempt.orderId());
		return new PaymentReturn(attempt.txnRef(), order.id(), reported.successful(), reported.responseCode(),
				attempt.status().name(), order.status().name());
	}

	private static Map<String, String> reply(String code, String message) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("RspCode", code);
		body.put("Message", message);
		return body;
	}

	private static ErrorResponseException badRequest(String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("Invalid payment return");
		return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
	}

	record StartedPayment(UUID attemptId, String txnRef, long amountVnd, String paymentUrl) {
	}

	/**
	 * @param gatewayReportsSuccess what the (signed) browser redirect claims; informational only
	 * @param attemptStatus what Settlebook has actually recorded from the IPN; PENDING means "processing"
	 */
	record PaymentReturn(String txnRef, UUID orderId, boolean gatewayReportsSuccess, String gatewayResponseCode,
			String attemptStatus, String orderStatus) {
	}

}
