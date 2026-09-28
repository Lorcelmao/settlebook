package dev.settlebook.order;

/** Lifecycle of a merchant order. Mirrors the {@code orders_status_known} check constraint. */
public enum OrderStatus {
	AWAITING_PAYMENT,
	PAID,
	EXPIRED,
	CANCELLED
}
