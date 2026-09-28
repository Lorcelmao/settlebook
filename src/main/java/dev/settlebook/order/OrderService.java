package dev.settlebook.order;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

	private final OrderRepository orders;
	private final OrderProperties properties;
	private final Clock clock;

	OrderService(OrderRepository orders, OrderProperties properties, Clock clock) {
		this.orders = orders;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public Order create(long amountVnd, String description) {
		// PostgreSQL stores microseconds; truncating keeps the returned object equal to what is persisted.
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		Order order = new Order(UUID.randomUUID(), amountVnd, description.strip(), OrderStatus.AWAITING_PAYMENT,
				now, now.plus(properties.paymentWindow()));
		return orders.insert(order);
	}

	@Transactional(readOnly = true)
	public Order get(UUID id) {
		return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
	}

	/** Joins the caller's transaction; returns false if the order was no longer awaiting payment. */
	@Transactional
	public boolean markPaidIfAwaitingPayment(UUID id) {
		return orders.markPaidIfAwaitingPayment(id);
	}

}
