package com.epam.reportportal.storage;

/**
 * Storage service interface
 */
public interface DataStorageService {
    void delete(String filePath) throws Exception;
}