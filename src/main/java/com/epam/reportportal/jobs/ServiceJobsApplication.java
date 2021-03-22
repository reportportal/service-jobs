package com.epam.reportportal.jobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.epam.reportportal" })
public class ServiceJobsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceJobsApplication.class, args);
	}

}
