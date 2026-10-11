package com.kepak.mule.keyvault.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AzureSecretSourceMappingTest {

  private static final String SECRET = "my-mulesoft-api-secret";

  @Test
  void mapsAuthStatusesToAuthenticationFailure() {
    assertInstanceOf(VaultAuthenticationException.class, AzureSecretSource.mapStatus(401, SECRET, null));
    assertInstanceOf(VaultAuthenticationException.class, AzureSecretSource.mapStatus(403, SECRET, null));
  }

  @Test
  void mapsNotFoundToSecretNotFound() {
    assertInstanceOf(SecretNotFoundException.class, AzureSecretSource.mapStatus(404, SECRET, null));
  }

  @Test
  void mapsThrottlingAndServerErrorsToRetryableUnavailable() {
    assertInstanceOf(VaultUnavailableException.class, AzureSecretSource.mapStatus(429, SECRET, null));
    assertInstanceOf(VaultUnavailableException.class, AzureSecretSource.mapStatus(408, SECRET, null));
    assertInstanceOf(VaultUnavailableException.class, AzureSecretSource.mapStatus(500, SECRET, null));
    assertInstanceOf(VaultUnavailableException.class, AzureSecretSource.mapStatus(503, SECRET, null));
  }

  @Test
  void otherStatusesAreNotRetryable() {
    VaultException e = AzureSecretSource.mapStatus(400, SECRET, null);
    assertFalse(e instanceof VaultUnavailableException);
    assertEquals(VaultException.class, e.getClass());
    assertTrue(e.getMessage().contains("400"));
  }

  @Test
  void mappedMessagesNameTheSecret() {
    assertTrue(AzureSecretSource.mapStatus(404, SECRET, null).getMessage().contains(SECRET));
    assertTrue(AzureSecretSource.mapStatus(429, SECRET, null).getMessage().contains(SECRET));
  }
}
