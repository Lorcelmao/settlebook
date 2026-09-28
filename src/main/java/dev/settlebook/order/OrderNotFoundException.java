package dev.settlebook.order;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered by Spring MVC as a 404 application/problem+json response. */
public class OrderNotFoundException extends ErrorResponseException {

	public OrderNotFoundException(UUID id) {
		super(HttpStatus.NOT_FOUND, problem(id), null);
	}

	private static ProblemDetail problem(UUID id) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Order " + id + " does not exist");
		problem.setTitle("Order not found");
		return problem;
	}

}
