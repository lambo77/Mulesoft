package com.kepak.mule.keyvault;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigValidationTest {

  private static final String SECRET = "super-secret-value-do-not-log";
  private static final String VAULT = "https://kv-mule-dev.vault.azure.net/";

  private static AzureKeyVaultConfiguration valid() {
    return new AzureKeyVaultConfiguration(VAULT, "client-id", SECRET, "tenant-id", "mule-kv-probe", 60);
  }

  @Test
  void acceptsValidConfiguration() {
    assertDoesNotThrow(() -> ConfigValidation.validate(valid()));
  }

  @Test
  void acceptsVaultUrlWithoutTrailingSlash() {
    AzureKeyVaultConfiguration c = new AzureKeyVaultConfiguration("https://kv-mule-dev.vault.azure.net", "a", SECRET, "t", "p", 1);
    assertDoesNotThrow(() -> ConfigValidation.validate(c));
  }

  @Test
  void rejectsNonHttpsVaultUrl() {
    AzureKeyVaultConfiguration c = new AzureKeyVaultConfiguration("http://kv-mule-dev.vault.azure.net/", "a", SECRET, "t", "p", 1);
    ConfigValidationException e = assertThrows(ConfigValidationException.class, () -> ConfigValidation.validate(c));
    assertTrue(e.getMessage().contains("https"));
  }

  @Test
  void rejectsVaultHostOutsidePublicCloudPattern() {
    AzureKeyVaultConfiguration c = new AzureKeyVaultConfiguration("https://example.com/", "a", SECRET, "t", "p", 1);
    assertThrows(ConfigValidationException.class, () -> ConfigValidation.validate(c));
  }

  @Test
  void rejectsBlankRequiredParametersAndNamesThem() {
    AzureKeyVaultConfiguration c = new AzureKeyVaultConfiguration(VAULT, " ", SECRET, "t", "p", 1);
    ConfigValidationException e = assertThrows(ConfigValidationException.class, () -> ConfigValidation.validate(c));
    assertTrue(e.getMessage().contains("clientId"));
  }

  @Test
  void errorMessagesNeverContainTheClientSecret() {
    AzureKeyVaultConfiguration c = new AzureKeyVaultConfiguration("https://example.com/", "a", SECRET, "t", "p", 1);
    ConfigValidationException e = assertThrows(ConfigValidationException.class, () -> ConfigValidation.validate(c));
    assertFalse(e.getMessage().contains(SECRET));
  }

  @Test
  void configToStringNeverContainsTheClientSecret() {
    assertFalse(valid().toString().contains(SECRET));
  }

  @Test
  void rejectsDottedSecretNames() {
    AzureKeyVaultConfiguration c = new AzureKeyVaultConfiguration(VAULT, "a", SECRET, "t", "azure.client.id", 1);
    assertThrows(ConfigValidationException.class, () -> ConfigValidation.validate(c));
  }

  @Test
  void secretNameLengthBoundaries() {
    assertDoesNotThrow(() -> ConfigValidation.validateSecretName("name", "a".repeat(127)));
    assertThrows(ConfigValidationException.class, () -> ConfigValidation.validateSecretName("name", "a".repeat(128)));
  }

  @Test
  void rejectsTtlBelowMinimum() {
    AzureKeyVaultConfiguration c = new AzureKeyVaultConfiguration(VAULT, "a", SECRET, "t", "p", 0);
    ConfigValidationException e = assertThrows(ConfigValidationException.class, () -> ConfigValidation.validate(c));
    assertTrue(e.getMessage().contains("ttlMinutes"));
  }
}
