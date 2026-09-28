package dev.settlebook.order;

import java.time.Instant;
import java.util.UUID;

/** A merchant order. {@code amountVnd} is whole Vietnamese dong. */
public record Order(
		UUID id,
		long amountVnd,
		String description,
		OrderStatus status,
		Instant createdAt,
		Instant expiresAt) {
}
