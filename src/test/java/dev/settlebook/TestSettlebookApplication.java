package dev.settlebook;

import org.springframework.boot.SpringApplication;

public class TestSettlebookApplication {

	public static void main(String[] args) {
		SpringApplication.from(SettlebookApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
