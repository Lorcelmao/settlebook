package dev.settlebook.order;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL through {@link JdbcClient}: later phases rely on PostgreSQL features
 * (ON CONFLICT, FOR UPDATE, SKIP LOCKED) that should stay visible in the code.
 */
@Repository
class OrderRepository {

	private static final RowMapper<Order> ORDER_ROW = (rs, rowNum) -> new Order(
			rs.getObject("id", UUID.class),
			rs.getLong("amount_vnd"),
			rs.getString("description"),
			OrderStatus.valueOf(rs.getString("status")),
			rs.getTimestamp("created_at").toInstant(),
			rs.getTimestamp("expires_at").toInstant());

	private final JdbcClient jdbc;

	OrderRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Order insert(Order order) {
		jdbc.sql("""
				INSERT INTO orders (id, amount_vnd, description, status, created_at, expires_at)
				VALUES (:id, :amountVnd, :description, :status, :createdAt, :expiresAt)
				""")
			.param("id", order.id())
			.param("amountVnd", order.amountVnd())
			.param("description", order.description())
			.param("status", order.status().name())
			.param("createdAt", Timestamp.from(order.createdAt()))
			.param("expiresAt", Timestamp.from(order.expiresAt()))
			.update();
		return order;
	}

	Optional<Order> findById(UUID id) {
		return jdbc.sql("SELECT * FROM orders WHERE id = :id")
			.param("id", id)
			.query(ORDER_ROW)
			.optional();
	}

	/**
	 * Conditional update: only an order still awaiting payment becomes PAID, so the check and the write
	 * are one atomic statement. Returns false if the order was in any other state.
	 */
	boolean markPaidIfAwaitingPayment(UUID id) {
		return jdbc.sql("UPDATE orders SET status = 'PAID' WHERE id = :id AND status = 'AWAITING_PAYMENT'")
			.param("id", id)
			.update() == 1;
	}

}
