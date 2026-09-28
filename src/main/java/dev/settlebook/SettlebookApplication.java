package dev.settlebook;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SettlebookApplication {

	public static void main(String[] args) {
		SpringApplication.run(SettlebookApplication.class, args);
	}

	/** Single time source so expiry and business-date logic can be tested with a fixed clock. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
