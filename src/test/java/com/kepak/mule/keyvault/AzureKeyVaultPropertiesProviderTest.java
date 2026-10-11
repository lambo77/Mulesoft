package com.kepak.mule.keyvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kepak.mule.keyvault.vault.SecretNotFoundException;
import com.kepak.mule.keyvault.vault.SecretSource;
import com.kepak.mule.keyvault.vault.VaultException;
import com.kepak.mule.keyvault.vault.VaultUnavailableException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mule.runtime.properties.api.ConfigurationProperty;

class AzureKeyVaultPropertiesProviderTest {

  private static final String VAULT = "https://kv-mule-dev.vault.azure.net/";
  private static final String PROBE = "mule-kv-probe";

  /** Fake vault: returns values from a map, counts calls, and can be told to fail. */
  static class FakeVault implements SecretSource {
    final Map<String, String> secrets = new HashMap<>();
    final AtomicInteger calls = new AtomicInteger();
    RuntimeException failure;

    FakeVault() {
      secrets.put(PROBE, "probe-ok");
    }

    @Override
    public String getSecretValue(String secretName) {
      calls.incrementAndGet();
      if (failure != null) {
        throw failure;
      }
      String value = secrets.get(secretName);
      if (value == null) {
        throw new SecretNotFoundException(secretName);
      }
      return value;
    }
  }

  private static Map<String, String> params() {
    Map<String, String> p = new HashMap<>();
    p.put("vaultUrl", VAULT);
    p.put("clientId", "client-id");
    p.put("clientSecret", "super-secret-value-do-not-log");
    p.put("tenantId", "tenant-id");
    p.put("probeSecretName", PROBE);
    p.put("ttlMinutes", "60");
    return p;
  }

  @org.junit.jupiter.api.BeforeEach
  void resetSharedCaches() {
    SharedCaches.clear();
  }

  private static AzureKeyVaultPropertiesProvider create(Map<String, String> p, FakeVault vault) {
    return AzureKeyVaultPropertiesProvider.create(p::get, config -> vault);
  }

  @Test
  void createReadsTheProbeSecretOnce() {
    FakeVault vault = new FakeVault();
    create(params(), vault);
    assertEquals(1, vault.calls.get());
  }

  @Test
  void createFailsWhenProbeSecretIsMissing() {
    FakeVault vault = new FakeVault();
    Map<String, String> p = params();
    p.put("probeSecretName", "no-such-secret");
    assertThrows(SecretNotFoundException.class, () -> create(p, vault));
  }

  @Test
  void createFailsWhenVaultIsUnreachable() {
    FakeVault vault = new FakeVault();
    vault.failure = new VaultUnavailableException(PROBE);
    assertThrows(VaultUnavailableException.class, () -> create(params(), vault));
  }

  @Test
  void createFailsOnInvalidConfigurationBeforeTouchingTheVault() {
    FakeVault vault = new FakeVault();
    Map<String, String> p = params();
    p.put("vaultUrl", "http://insecure.example.com/");
    assertThrows(ConfigValidationException.class, () -> create(p, vault));
    assertEquals(0, vault.calls.get());
  }

  @Test
  void defaultsTtlWhenNotSet() {
    assertEquals(60, AzureKeyVaultPropertiesProvider.parseTtl(null));
    assertEquals(60, AzureKeyVaultPropertiesProvider.parseTtl("  "));
  }

  @Test
  void rejectsNonNumericTtl() {
    assertThrows(ConfigValidationException.class, () -> AzureKeyVaultPropertiesProvider.parseTtl("sixty"));
  }

  @Test
  void resolvesSecretForAValidKey() {
    FakeVault vault = new FakeVault();
    vault.secrets.put("my-mulesoft-api-secret", "api-value");
    AzureKeyVaultPropertiesProvider provider = create(params(), vault);

    Optional<? extends ConfigurationProperty> result = provider.provide("my-mulesoft-api-secret");

    assertTrue(result.isPresent());
    assertEquals("api-value", result.get().getValue());
    assertEquals("my-mulesoft-api-secret", result.get().getKey());
  }

  @Test
  void repeatedLookupsAreServedFromTheCache() {
    FakeVault vault = new FakeVault();
    vault.secrets.put("my-mulesoft-api-secret", "api-value");
    AzureKeyVaultPropertiesProvider provider = create(params(), vault);
    int afterCreate = vault.calls.get();

    provider.provide("my-mulesoft-api-secret");
    provider.provide("my-mulesoft-api-secret");

    assertEquals(afterCreate + 1, vault.calls.get());
  }

  @Test
  void keysThatAreNotValidSecretNamesReturnEmptyWithoutAVaultCall() {
    FakeVault vault = new FakeVault();
    AzureKeyVaultPropertiesProvider provider = create(params(), vault);
    int afterCreate = vault.calls.get();

    assertFalse(provider.provide("db.host").isPresent());
    assertFalse(provider.provide("").isPresent());
    assertEquals(afterCreate, vault.calls.get());
  }

  @Test
  void missingValidSecretFailsResolution() {
    FakeVault vault = new FakeVault();
    AzureKeyVaultPropertiesProvider provider = create(params(), vault);
    assertThrows(VaultException.class, () -> provider.provide("does-not-exist"));
  }

  @Test
  void descriptionNamesTheVaultButNotTheSecret() {
    String description = create(params(), new FakeVault()).getDescription();
    assertTrue(description.contains(VAULT));
    assertFalse(description.contains("super-secret-value-do-not-log"));
  }

  @Test
  void factoryMatchesTheAzureKvConfigElement() {
    var id = new AzureKeyVaultPropertiesProviderFactory().getSupportedComponentIdentifier();
    assertEquals("azure-kv", id.getNamespace());
    assertEquals("config", id.getName());
  }
}
