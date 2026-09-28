package dev.settlebook.gateway.vnpay;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * VNPay merchant connection settings. Values come from environment variables (or a local, git-ignored
 * {@code .env}); the application refuses to start without them rather than failing on the first payment.
 *
 * @param tmnCode terminal id issued by VNPay ({@code vnp_TmnCode})
 * @param hashSecret key for the HMAC-SHA512 checksum ({@code vnp_HashSecret}); never logged
 * @param payUrl VNPay payment page the buyer is redirected to
 * @param returnUrl where VNPay sends the buyer's browser after payment
 */
@Validated
@ConfigurationProperties("settlebook.vnpay")
public record VnpayProperties(
		@NotBlank String tmnCode,
		@NotBlank String hashSecret,
		@NotBlank String payUrl,
		@NotBlank String returnUrl) {

	@Override
	public String toString() {
		return "VnpayProperties[tmnCode=" + tmnCode + ", hashSecret=<redacted>, payUrl=" + payUrl
				+ ", returnUrl=" + returnUrl + "]";
	}

}
