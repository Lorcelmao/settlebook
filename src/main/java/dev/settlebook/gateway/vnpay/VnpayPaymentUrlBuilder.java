package dev.settlebook.gateway.vnpay;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

import dev.settlebook.payment.PaymentAttempt;

/** Builds the signed VNPay payment-page URL the buyer is redirected to. */
@Component
class VnpayPaymentUrlBuilder {

	/** {@code vnp_Amount} is Numeric[1,12] after scaling by 100, so the largest payable amount is this. */
	static final long MAX_AMOUNT_VND = 9_999_999_999L;

	private final VnpayProperties properties;
	private final VnpaySigner signer;

	VnpayPaymentUrlBuilder(VnpayProperties properties, VnpaySigner signer) {
		this.properties = properties;
		this.signer = signer;
	}

	/**
	 * @param now creation time sent as {@code vnp_CreateDate}
	 * @param expiresAt end of the order's payment window, sent as {@code vnp_ExpireDate} so VNPay refuses
	 *        payment after the merchant would
	 * @param buyerIp the buyer's IP address ({@code vnp_IpAddr} is required)
	 */
	String build(PaymentAttempt attempt, Instant now, Instant expiresAt, String buyerIp) {
		if (attempt.amountVnd() > MAX_AMOUNT_VND) {
			throw amountTooLarge(attempt.amountVnd());
		}
		Map<String, String> params = new HashMap<>();
		params.put("vnp_Version", "2.1.0");
		params.put("vnp_Command", "pay");
		params.put("vnp_TmnCode", properties.tmnCode());
		params.put("vnp_Amount", Long.toString(attempt.amountVnd() * 100));
		params.put("vnp_CurrCode", "VND");
		params.put("vnp_TxnRef", attempt.txnRef());
		// VNPay requires Vietnamese without diacritics and no special characters, so the buyer-facing
		// description is never sent; a fixed ASCII text plus our reference is.
		params.put("vnp_OrderInfo", "Thanh toan don hang " + attempt.txnRef());
		params.put("vnp_OrderType", "other");
		params.put("vnp_Locale", "vn");
		params.put("vnp_ReturnUrl", properties.returnUrl());
		params.put("vnp_IpAddr", buyerIp);
		params.put("vnp_CreateDate", VnpayTime.format(now));
		params.put("vnp_ExpireDate", VnpayTime.format(expiresAt));

		String query = VnpaySigner.canonicalQuery(params);
		return properties.payUrl() + "?" + query + "&" + VnpaySigner.SECURE_HASH + "=" + signer.sign(params);
	}

	private static ErrorResponseException amountTooLarge(long amountVnd) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				"Amount " + amountVnd + " VND exceeds the VNPay maximum of " + MAX_AMOUNT_VND + " VND");
		problem.setTitle("Amount not supported by gateway");
		return new ErrorResponseException(HttpStatus.UNPROCESSABLE_CONTENT, problem, null);
	}

}
