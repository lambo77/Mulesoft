package com.kepak.mule.keyvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kepak.mule.keyvault.vault.SecretNotFoundException;
import com.kepak.mule.keyvault.vault.SecretSource;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AzureKeyVaultOperationsTest {

  private static final String VAULT = "https://kv-mule-dev.vault.azure.net/";

  private final AtomicInteger calls = new AtomicInteger();

  @BeforeEach
  void reset() {
    SharedCaches.clear();
    calls.set(0);
  }

  private SecretSource vault() {
    return secretName -> {
      calls.incrementAndGet();
      if ("rotating-secret".equals(secretName)) {
        return "v" + calls.get();
      }
      throw new SecretNotFoundException(secretName);
    };
  }

  private static AzureKeyVaultConfiguration config(String clientId) {
    return new AzureKeyVaultConfiguration(VAULT, clientId, "super-secret-value-do-not-log", "tenant", "probe", 60);
  }

  @Test
  void returnsSecretValue() {
    String value = AzureKeyVaultOperations.getSecret(config("ops-1"), "rotating-secret", c -> vault());
    assertEquals("v1", value);
  }

  @Test
  void repeatedCallsAreServedFromTheCache() {
    AzureKeyVaultConfiguration c = config("ops-2");
    AzureKeyVaultOperations.getSecret(c, "rotating-secret", x -> vault());
    AzureKeyVaultOperations.getSecret(c, "rotating-secret", x -> vault());
    assertEquals(1, calls.get());
  }

  @Test
  void operationAndPlaceholderProviderShareOneCache() {
    AzureKeyVaultConfiguration c = config("ops-3");
    AzureKeyVaultPropertiesProvider.create(
        name -> switch (name) {
          case "vaultUrl" -> VAULT;
          case "clientId" -> "ops-3";
          case "clientSecret" -> "super-secret-value-do-not-log";
          case "tenantId" -> "tenant";
          case "probeSecretName" -> "rotating-secret";
          case "ttlMinutes" -> "60";
          default -> null;
        },
        x -> vault());
    int afterProbe = calls.get();

    AzureKeyVaultOperations.getSecret(c, "rotating-secret", x -> vault());

    assertEquals(afterProbe, calls.get());
  }

  @Test
  void rejectsDottedSecretNameWithoutAVaultCall() {
    assertThrows(ConfigValidationException.class,
        () -> AzureKeyVaultOperations.getSecret(config("ops-4"), "azure.client.id", x -> vault()));
    assertEquals(0, calls.get());
  }

  @Test
  void rejectsInvalidConfigurationWithoutAVaultCall() {
    AzureKeyVaultConfiguration bad = new AzureKeyVaultConfiguration("http://insecure/", "c", "s", "t", "p", 60);
    assertThrows(ConfigValidationException.class,
        () -> AzureKeyVaultOperations.getSecret(bad, "rotating-secret", x -> vault()));
    assertEquals(0, calls.get());
  }

  @Test
  void missingSecretSurfaces() {
    assertThrows(SecretNotFoundException.class,
        () -> AzureKeyVaultOperations.getSecret(config("ops-5"), "no-such-secret", x -> vault()));
  }
}
