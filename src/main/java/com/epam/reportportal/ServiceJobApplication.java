package com.epam.reportportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.epam.reportportal" })
public class ServiceJobApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceJobApplication.class, args);
	}

}
