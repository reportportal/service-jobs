package com.epam.reportportal.elastic.dao;

public interface LogMessageRepositoryCustom<T> {
    <S extends T> S save(S logMessage);
}
