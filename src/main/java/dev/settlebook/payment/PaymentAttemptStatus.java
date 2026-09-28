package dev.settlebook.payment;

/** Mirrors the {@code payment_attempts_status_known} check constraint. */
public enum PaymentAttemptStatus {
	PENDING,
	SUCCEEDED,
	FAILED
}
