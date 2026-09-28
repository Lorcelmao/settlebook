package dev.settlebook.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered as 409: a payment cannot be started for an order that is not awaiting payment or has expired. */
public class OrderNotPayableException extends ErrorResponseException {

	public OrderNotPayableException(UUID orderId, String reason) {
		super(HttpStatus.CONFLICT, problem(orderId, reason), null);
	}

	private static ProblemDetail problem(UUID orderId, String reason) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Order " + orderId + " cannot be paid: " + reason);
		problem.setTitle("Order not payable");
		return problem;
	}

}
