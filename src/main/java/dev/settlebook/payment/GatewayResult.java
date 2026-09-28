package dev.settlebook.payment;

import java.time.Instant;

/**
 * A payment result reported by a gateway, already authenticated and translated out of the gateway's
 * wire format. Nothing in the payment module depends on a specific gateway's parameter names.
 *
 * @param amountVnd whole VND as reported by the gateway (any gateway scaling already removed)
 * @param successful whether the gateway reports that the buyer was charged
 * @param responseCode the gateway's own result code, kept for audit and display
 * @param transactionNo the gateway's transaction id; null if the gateway did not assign one
 */
public record GatewayResult(
		Gateway gateway,
		String txnRef,
		long amountVnd,
		boolean successful,
		String responseCode,
		String transactionNo,
		String bankCode,
		Instant paidAt) {
}
