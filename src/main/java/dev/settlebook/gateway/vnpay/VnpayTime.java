package dev.settlebook.gateway.vnpay;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** VNPay timestamps: {@code yyyyMMddHHmmss} in GMT+7 (Asia/Ho_Chi_Minh, which has no daylight saving). */
final class VnpayTime {

	static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private VnpayTime() {
	}

	static String format(Instant instant) {
		return FORMAT.format(instant.atZone(ZONE));
	}

	static Instant parse(String value) {
		return LocalDateTime.parse(value, FORMAT).atZone(ZONE).toInstant();
	}

}
