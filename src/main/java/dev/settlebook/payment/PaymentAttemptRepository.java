package dev.settlebook.payment;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PaymentAttemptRepository {

	private static final RowMapper<PaymentAttempt> ATTEMPT_ROW = (rs, rowNum) -> new PaymentAttempt(
			rs.getObject("id", UUID.class),
			rs.getObject("order_id", UUID.class),
			Gateway.valueOf(rs.getString("gateway")),
			rs.getString("txn_ref"),
			rs.getLong("amount_vnd"),
			PaymentAttemptStatus.valueOf(rs.getString("status")),
			rs.getTimestamp("created_at").toInstant());

	private final JdbcClient jdbc;

	PaymentAttemptRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	PaymentAttempt insert(PaymentAttempt attempt) {
		jdbc.sql("""
				INSERT INTO payment_attempts (id, order_id, gateway, txn_ref, amount_vnd, status, created_at, updated_at)
				VALUES (:id, :orderId, :gateway, :txnRef, :amountVnd, :status, :createdAt, :createdAt)
				""")
			.param("id", attempt.id())
			.param("orderId", attempt.orderId())
			.param("gateway", attempt.gateway().name())
			.param("txnRef", attempt.txnRef())
			.param("amountVnd", attempt.amountVnd())
			.param("status", attempt.status().name())
			.param("createdAt", Timestamp.from(attempt.createdAt()))
			.update();
		return attempt;
	}

	Optional<PaymentAttempt> findByGatewayAndTxnRef(Gateway gateway, String txnRef) {
		return jdbc.sql("SELECT * FROM payment_attempts WHERE gateway = :gateway AND txn_ref = :txnRef")
			.param("gateway", gateway.name())
			.param("txnRef", txnRef)
			.query(ATTEMPT_ROW)
			.optional();
	}

	/**
	 * Records a gateway result only if the attempt is still PENDING, as one atomic statement. Returns
	 * false if another result was already recorded, so a result can never be applied twice.
	 */
	boolean completeIfPending(UUID attemptId, PaymentAttemptStatus finalStatus, GatewayResult result, Instant now) {
		return jdbc.sql("""
				UPDATE payment_attempts
				   SET status = :status,
				       gateway_transaction_no = :transactionNo,
				       gateway_response_code = :responseCode,
				       gateway_bank_code = :bankCode,
				       gateway_paid_at = :paidAt,
				       updated_at = :now
				 WHERE id = :id AND status = 'PENDING'
				""")
			.param("status", finalStatus.name())
			.param("transactionNo", result.transactionNo())
			.param("responseCode", result.responseCode())
			.param("bankCode", result.bankCode())
			.param("paidAt", result.paidAt() == null ? null : Timestamp.from(result.paidAt()))
			.param("now", Timestamp.from(now))
			.param("id", attemptId)
			.update() == 1;
	}

}
