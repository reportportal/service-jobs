package com.epam.reportportal.model;

/**
 * Checked exception for cases when file is not found in blob storage.
 */
public class BlobNotFoundException extends Exception {

  private String fileName;

  public BlobNotFoundException() {
    super();
  }

  public BlobNotFoundException(String message) {
    super(message);
  }

  public BlobNotFoundException(String fileName, Throwable cause) {
    super(cause);
    this.fileName = fileName;
  }

  public BlobNotFoundException(Throwable cause) {
    super(cause);
  }

  protected BlobNotFoundException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public String getFileName() {
    return fileName;
  }
}
