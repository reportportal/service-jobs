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

    public abstract void execute();
}
