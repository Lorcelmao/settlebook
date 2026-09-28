package dev.settlebook.order;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param paymentWindow how long a new order waits for payment before it can be expired */
@ConfigurationProperties("settlebook.order")
public record OrderProperties(Duration paymentWindow) {
}
