package dev.settlebook.gateway.vnpay;

import java.time.format.DateTimeParseException;
import java.util.Map;

import dev.settlebook.payment.Gateway;
import dev.settlebook.payment.GatewayResult;

/** Translates a VNPay callback's query parameters (IPN or browser return) into a {@link GatewayResult}. */
final class VnpayCallbacks {

	private VnpayCallbacks() {
	}

	/**
	 * Call only after the checksum has been verified.
	 *
	 * @throws IllegalArgumentException if a required field is missing or malformed
	 */
	static GatewayResult toResult(Map<String, String> params) {
		String txnRef = required(params, "vnp_TxnRef");
		long scaledAmount = parseLong(required(params, "vnp_Amount"), "vnp_Amount");
		if (scaledAmount % 100 != 0) {
			throw new IllegalArgumentException("vnp_Amount is not a whole number of VND: " + scaledAmount);
		}
		String responseCode = required(params, "vnp_ResponseCode");
		String transactionStatus = required(params, "vnp_TransactionStatus");
		// VNPay's rule: charged only when both the response code and the transaction status are 00.
		boolean successful = "00".equals(responseCode) && "00".equals(transactionStatus);
		String payDate = params.get("vnp_PayDate");
		return new GatewayResult(
				Gateway.VNPAY,
				txnRef,
				scaledAmount / 100,
				successful,
				responseCode,
				emptyToNull(params.get("vnp_TransactionNo")),
				emptyToNull(params.get("vnp_BankCode")),
				payDate == null || payDate.isEmpty() ? null : parsePayDate(payDate));
	}

	private static String required(Map<String, String> params, String name) {
		String value = params.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing " + name);
		}
		return value;
	}

	private static long parseLong(String value, String name) {
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException(name + " is not a number: " + value, ex);
		}
	}

	private static java.time.Instant parsePayDate(String value) {
		try {
			return VnpayTime.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw new IllegalArgumentException("vnp_PayDate is malformed: " + value, ex);
		}
	}

	private static String emptyToNull(String value) {
		return value == null || value.isEmpty() ? null : value;
	}

}
