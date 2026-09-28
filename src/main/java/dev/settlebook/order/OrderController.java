package dev.settlebook.order;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

	private final OrderService orderService;

	OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
		Order order = orderService.create(request.amountVnd(), request.description());
		return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(OrderResponse.from(order));
	}

	@GetMapping("/{id}")
	OrderResponse get(@PathVariable UUID id) {
		return OrderResponse.from(orderService.get(id));
	}

	/**
	 * @param amountVnd whole VND; a boxed type so a missing value is a validation error rather than 0
	 * @param description shown to the buyer; 255 characters matches the VNPay order-info limit
	 */
	record CreateOrderRequest(
			@NotNull @Positive Long amountVnd,
			@NotBlank @Size(max = 255) String description) {
	}

	record OrderResponse(
			UUID id,
			long amountVnd,
			String description,
			OrderStatus status,
			Instant createdAt,
			Instant expiresAt) {

		static OrderResponse from(Order order) {
			return new OrderResponse(order.id(), order.amountVnd(), order.description(), order.status(),
					order.createdAt(), order.expiresAt());
		}

	}

}
