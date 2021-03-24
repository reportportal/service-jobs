package com.epam.reportportal.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class BaseJob {
    protected JdbcTemplate jdbcTemplate;
    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    public BaseJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    protected void logStart() {
        LOGGER.info("Job {} has been started.", this.getClass().getSimpleName());
    }

    protected void logFinish(Object result) {
        LOGGER.info("Job {} has been finished. Result {}", this.getClass().getSimpleName(), result);
    }

    protected void logFinish() {
        logFinish(null);
    }
}
