package dev.settlebook.payment;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.settlebook.order.Order;
import dev.settlebook.order.OrderService;
import dev.settlebook.order.OrderStatus;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final PaymentAttemptRepository attempts;
	private final OrderService orders;
	private final Clock clock;

	PaymentService(PaymentAttemptRepository attempts, OrderService orders, Clock clock) {
		this.attempts = attempts;
		this.orders = orders;
		this.clock = clock;
	}

	/** Creates a new attempt for an order that is awaiting payment and not yet past its payment window. */
	@Transactional
	public PaymentAttempt startAttempt(UUID orderId, Gateway gateway) {
		Order order = orders.get(orderId);
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		if (order.status() != OrderStatus.AWAITING_PAYMENT) {
			throw new OrderNotPayableException(orderId, "status is " + order.status());
		}
		if (!now.isBefore(order.expiresAt())) {
			throw new OrderNotPayableException(orderId, "payment window ended at " + order.expiresAt());
		}
		UUID attemptId = UUID.randomUUID();
		// Globally unique, alphanumeric and 32 characters: satisfies every gateway's reference format.
		String txnRef = attemptId.toString().replace("-", "");
		return attempts.insert(new PaymentAttempt(attemptId, orderId, gateway, txnRef, order.amountVnd(),
				PaymentAttemptStatus.PENDING, now));
	}

	@Transactional(readOnly = true)
	public Optional<PaymentAttempt> findAttempt(Gateway gateway, String txnRef) {
		return attempts.findByGatewayAndTxnRef(gateway, txnRef);
	}

	/**
	 * Applies an authenticated gateway result. The attempt update and the order update commit together or
	 * not at all; the caller replies to the gateway only after this method returns.
	 *
	 * @throws ChargeOnUnpayableOrderException for a successful charge on an order that is no longer
	 *         awaiting payment (a duplicate charge or a late payment); the transaction rolls back
	 */
	@Transactional
	public ApplyOutcome applyGatewayResult(GatewayResult result) {
		Optional<PaymentAttempt> found = attempts.findByGatewayAndTxnRef(result.gateway(), result.txnRef());
		if (found.isEmpty()) {
			return ApplyOutcome.ATTEMPT_NOT_FOUND;
		}
		PaymentAttempt attempt = found.get();
		if (result.amountVnd() != attempt.amountVnd()) {
			log.warn("Amount mismatch for {} {}: gateway reported {}, attempt is {}", result.gateway(),
					result.txnRef(), result.amountVnd(), attempt.amountVnd());
			return ApplyOutcome.AMOUNT_MISMATCH;
		}

		PaymentAttemptStatus finalStatus = result.successful() ? PaymentAttemptStatus.SUCCEEDED
				: PaymentAttemptStatus.FAILED;
		if (!attempts.completeIfPending(attempt.id(), finalStatus, result, clock.instant())) {
			return ApplyOutcome.ALREADY_APPLIED;
		}
		if (result.successful() && !orders.markPaidIfAwaitingPayment(attempt.orderId())) {
			throw new ChargeOnUnpayableOrderException(attempt.orderId(), result.txnRef());
		}
		return ApplyOutcome.APPLIED;
	}

}
