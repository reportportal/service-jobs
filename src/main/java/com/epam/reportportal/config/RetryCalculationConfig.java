package com.epam.reportportal.config;

import com.epam.reportportal.calculation.RetryProcessing;
import com.epam.reportportal.calculation.statistic.RetryCalculation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;

//@Configuration
// commented template config for mechanism for possible retry processing
public class RetryCalculationConfig {

//    @Bean
//    public RetryProcessing getRetryProcessing(@Value("${rp.variable.calculation.retry.batchSize}") int batchSize,
//        @Value("${rp.variable.calculation.retry.batchTimeout}") int timeout, JdbcTemplate jdbcTemplate) {
//        return new RetryProcessing(batchSize, timeout, new DefaultManagedTaskScheduler(), new RetryCalculation(jdbcTemplate));
//    }
}
