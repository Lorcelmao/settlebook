package dev.settlebook.gateway.vnpay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class VnpayConfiguration {

	@Bean
	VnpaySigner vnpaySigner(VnpayProperties properties) {
		return new VnpaySigner(properties.hashSecret());
	}

}
