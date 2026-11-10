package com.epam.reportportal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.opendal.Operator;
import org.junit.jupiter.api.Test;

class DataStoreConfigurationTest {

  private final DataStoreConfiguration configuration = new DataStoreConfiguration();

  @Test
  void azureBlobOperatorIsBuiltForAzblobService() {
    try (Operator operator = configuration.azureBlobOperator("account", "a2V5", "", "rp-container")) {
      assertEquals("azblob", operator.info.scheme);
    }
  }

  @Test
  void gcsOperatorIsBuiltForGcsService() {
    try (Operator operator = configuration.gcsOperator("rp-bucket", "", "")) {
      assertEquals("gcs", operator.info.scheme);
    }
  }
}
