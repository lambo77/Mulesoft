package com.kepak.mule.keyvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kepak.mule.keyvault.vault.RetryingSecretSource;
import com.kepak.mule.keyvault.vault.SecretNotFoundException;
import com.kepak.mule.keyvault.vault.SecretSource;
import com.kepak.mule.keyvault.vault.VaultAuthenticationException;
import com.kepak.mule.keyvault.vault.VaultLog;
import com.kepak.mule.keyvault.vault.VaultUnavailableException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * WP6 guardrails: no secret value reaches a log line, an exception message, or a config string on any
 * error path, and the operation's success path logs nothing that carries the value.
 */
class SecretGuardrailTest {

  private static final String VAULT = "https://kv-mule-dev.vault.azure.net/";
  private static final String VALUE = "PLAIN-VALUE-7f3a";
  private static final String CLIENT = "CLIENT-SECRET-9c2b";

  private final List<String> logged = new CopyOnWriteArrayList<>();

  @BeforeEach
  void captureLogs() {
    SharedCaches.clear();
    logged.clear();
    VaultLog.setSink((level, message) -> logged.add(level + " " + message));
  }

  private static AzureKeyVaultConfiguration config(String clientId) {
    return new AzureKeyVaultConfiguration(VAULT, clientId, CLIENT, "tenant", "probe-secret", 60);
  }

  private static void assertNoSecretLeak(List<String> lines) {
    for (String line : lines) {
      assertFalse(line.contains(VALUE), "secret value logged: " + line);
      assertFalse(line.contains(CLIENT), "client secret logged: " + line);
    }
  }

  private static void assertNoSecretLeak(Throwable t) {
    assertFalse(String.valueOf(t.getMessage()).contains(VALUE), "value in exception message");
    assertFalse(String.valueOf(t.getMessage()).contains(CLIENT), "client secret in exception message");
  }

  @Test
  void operationSuccessReturnsValueAndLogsNothingCarryingIt() {
    SecretSource vault = name -> VALUE;
    String result = AzureKeyVaultOperations.getSecret(config("g-1"), "good-secret", c -> vault);

    assertEquals(VALUE, result);
    assertNoSecretLeak(logged);
  }

  @Test
  void operationFailureLogsSecretNameAndReasonButNotValue() {
    SecretSource vault = name -> {
      throw new SecretNotFoundException(name);
    };

    SecretNotFoundException e = assertThrows(SecretNotFoundException.class,
        () -> AzureKeyVaultOperations.getSecret(config("g-2"), "no-such-secret", c -> vault));

    assertNoSecretLeak(e);
    assertTrue(logged.stream().anyMatch(l -> l.startsWith("ERROR") && l.contains("no-such-secret")));
    assertNoSecretLeak(logged);
  }

  @Test
  void authFailureDoesNotExposeClientSecretEvenIfTheCauseDoes() {
    // The SDK cause may echo sensitive text. Our exception messages and log lines must not.
    SecretSource vault = name -> {
      throw new VaultAuthenticationException(name,
          new RuntimeException("AADSTS7000215: invalid client secret " + CLIENT));
    };

    VaultAuthenticationException e = assertThrows(VaultAuthenticationException.class,
        () -> AzureKeyVaultOperations.getSecret(config("g-3"), "good-secret", c -> vault));

    assertNoSecretLeak(e);
    assertNoSecretLeak(logged);
  }

  @Test
  void retryWarningsNameTheSecretNotItsValue() {
    AtomicInteger calls = new AtomicInteger();
    SecretSource vault = name -> {
      if (calls.incrementAndGet() == 1) {
        throw new VaultUnavailableException(name);
      }
      return VALUE;
    };

    String result = AzureKeyVaultOperations.getSecret(config("g-4"), "good-secret",
        c -> new RetryingSecretSource(vault, 3, 1, ms -> { }));

    assertEquals(VALUE, result);
    assertTrue(logged.stream().anyMatch(l -> l.startsWith("WARN") && l.contains("good-secret")));
    assertNoSecretLeak(logged);
  }

  @Test
  void startUpFailureLogsAnErrorWithoutTheValue() {
    SecretSource vault = name -> {
      throw new SecretNotFoundException(name);
    };
    Map<String, String> p = new HashMap<>();
    p.put("vaultUrl", VAULT);
    p.put("clientId", "g-5");
    p.put("clientSecret", CLIENT);
    p.put("tenantId", "tenant");
    p.put("probeSecretName", "missing-probe");
    p.put("ttlMinutes", "60");

    SecretNotFoundException e = assertThrows(SecretNotFoundException.class,
        () -> AzureKeyVaultPropertiesProvider.create(p::get, c -> vault));

    assertNoSecretLeak(e);
    assertTrue(logged.stream().anyMatch(l -> l.startsWith("ERROR") && l.contains("Start-up failed")));
    assertNoSecretLeak(logged);
  }

  @Test
  void startUpSuccessLogsTheVaultUrlOnly() {
    SecretSource vault = name -> VALUE;
    Map<String, String> p = new HashMap<>();
    p.put("vaultUrl", VAULT);
    p.put("clientId", "g-6");
    p.put("clientSecret", CLIENT);
    p.put("tenantId", "tenant");
    p.put("probeSecretName", "probe-secret");
    p.put("ttlMinutes", "60");

    AzureKeyVaultPropertiesProvider.create(p::get, c -> vault);

    assertTrue(logged.stream().anyMatch(l -> l.contains(VAULT)));
    assertNoSecretLeak(logged);
  }

  @Test
  void placeholderLookupNeverLogsTheValue() {
    SecretSource vault = name -> VALUE;
    Map<String, String> p = new HashMap<>();
    p.put("vaultUrl", VAULT);
    p.put("clientId", "g-7");
    p.put("clientSecret", CLIENT);
    p.put("tenantId", "tenant");
    p.put("probeSecretName", "probe-secret");
    p.put("ttlMinutes", "60");
    var provider = AzureKeyVaultPropertiesProvider.create(p::get, c -> vault);

    assertEquals(VALUE, provider.provide("good-secret").orElseThrow().getValue());
    assertNoSecretLeak(logged);
  }

  @Test
  void configToStringNeverContainsTheClientSecret() {
    assertFalse(config("g-8").toString().contains(CLIENT));
  }
}
