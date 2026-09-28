package dev.settlebook.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.settlebook.TestcontainersConfiguration;

/** Order API against a real PostgreSQL container, including the schema's own constraints. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderApiIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void createdOrderCanBeFetched() {
		MvcTestResult created = mvc.post().uri("/api/orders")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"amountVnd": 150000, "description": "  Ao thun size M  "}
					""")
			.exchange();

		assertThat(created).hasStatus(HttpStatus.CREATED);
		String id = created.getResponse().getHeader("Location").replace("/api/orders/", "");

		assertThat(created).bodyJson().extractingPath("$.id").isEqualTo(id);
		assertThat(created).bodyJson().extractingPath("$.status").isEqualTo("AWAITING_PAYMENT");
		assertThat(created).bodyJson().extractingPath("$.description").isEqualTo("Ao thun size M");

		MvcTestResult fetched = mvc.get().uri("/api/orders/{id}", id).exchange();
		assertThat(fetched).hasStatusOk();
		assertThat(fetched).bodyJson().extractingPath("$.amountVnd").isEqualTo(150000);
		assertThat(jsonString(fetched, "$.createdAt")).isEqualTo(jsonString(created, "$.createdAt"));

		Instant createdAt = Instant.parse(jsonString(fetched, "$.createdAt"));
		Instant expiresAt = Instant.parse(jsonString(fetched, "$.expiresAt"));
		assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void invalidRequestIsRejectedAsProblemDetail() {
		MvcTestResult result = mvc.post().uri("/api/orders")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"amountVnd": 0, "description": " "}
					""")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void missingAmountIsRejected() {
		MvcTestResult result = mvc.post().uri("/api/orders")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"description": "no amount"}
					""")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void unknownOrderIsNotFoundProblemDetail() {
		MvcTestResult result = mvc.get().uri("/api/orders/{id}", UUID.randomUUID()).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Order not found");
	}

	@Test
	void databaseRejectsNonPositiveAmountEvenWithoutApiValidation() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO orders (amount_vnd, description, created_at, expires_at)
				VALUES (0, 'bypass', now(), now() + interval '15 minutes')
				""").update())
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("orders_amount_positive");
	}

	private static String jsonString(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), path);
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
