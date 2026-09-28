package dev.settlebook.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * One redirect of a buyer to a gateway for an order.
 *
 * @param txnRef our reference sent to the gateway; the gateway echoes it back in callbacks
 */
public record PaymentAttempt(
		UUID id,
		UUID orderId,
		Gateway gateway,
		String txnRef,
		long amountVnd,
		PaymentAttemptStatus status,
		Instant createdAt) {
}
