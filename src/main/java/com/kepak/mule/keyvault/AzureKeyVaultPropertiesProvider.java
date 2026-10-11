package com.kepak.mule.keyvault;

import com.kepak.mule.keyvault.vault.SecretCache;
import com.kepak.mule.keyvault.vault.SecretSource;
import com.kepak.mule.keyvault.vault.VaultException;
import com.kepak.mule.keyvault.vault.VaultLog;
import org.mule.runtime.properties.api.ConfigurationProperty;
import org.mule.runtime.properties.api.ConfigurationPropertiesProvider;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Resolves {@code ${azure-kv::<secret>}} placeholders from one Key Vault, through the shared TTL cache.
 *
 * <p>Creation reads the connection parameters, validates them, and reads the probe secret once
 * (decision D3). Any failure fails start-up (decision Q2).
 */
public final class AzureKeyVaultPropertiesProvider implements ConfigurationPropertiesProvider {

  static final int DEFAULT_TTL_MINUTES = 60;

  private final SecretCache cache;
  private final String vaultUrl;

  private AzureKeyVaultPropertiesProvider(SecretCache cache, String vaultUrl) {
    this.cache = cache;
    this.vaultUrl = vaultUrl;
  }

  /**
   * @param parameterResolver resolves each config parameter by name (values may contain resolved placeholders)
   * @param sourceFactory     builds the secret source for a validated configuration
   */
  static AzureKeyVaultPropertiesProvider create(UnaryOperator<String> parameterResolver,
                                                Function<AzureKeyVaultConfiguration, SecretSource> sourceFactory) {
    AzureKeyVaultConfiguration config = new AzureKeyVaultConfiguration(
        parameterResolver.apply("vaultUrl"),
        parameterResolver.apply("clientId"),
        parameterResolver.apply("clientSecret"),
        parameterResolver.apply("tenantId"),
        parameterResolver.apply("probeSecretName"),
        parseTtl(parameterResolver.apply("ttlMinutes")));
    ConfigValidation.validate(config);

    SecretCache cache = SharedCaches.forConfig(config, sourceFactory);
    // Probe read: validates credentials and the probe secret. A failure fails start-up.
    try {
      cache.get(config.getProbeSecretName());
    } catch (VaultException e) {
      VaultLog.error("Start-up failed: " + e.getMessage());
      throw e;
    }
    VaultLog.info("Key Vault probe read succeeded for " + config.getVaultUrl());
    return new AzureKeyVaultPropertiesProvider(cache, config.getVaultUrl());
  }

  /**
   * Keys that are not valid Key Vault secret names (for example dotted keys) are not secrets this provider
   * can hold, so the provider returns empty and the runtime can resolve them elsewhere. Valid names are read
   * from the vault. A missing or unreadable secret throws (Q2). See the WP7 gate on namespace routing.
   */
  @Override
  public Optional<ConfigurationProperty> provide(String key) {
    if (!ConfigValidation.isValidSecretName(key)) {
      return Optional.empty();
    }
    String value = cache.get(key);
    return Optional.of(new AzureKeyVaultProperty(key, value, vaultUrl));
  }

  @Override
  public String getDescription() {
    return "Azure Key Vault " + vaultUrl;
  }

  static int parseTtl(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_TTL_MINUTES;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new ConfigValidationException("ttlMinutes must be a whole number of minutes");
    }
  }

  private static final class AzureKeyVaultProperty implements ConfigurationProperty {

    private final String key;
    private final String value;
    private final String source;

    AzureKeyVaultProperty(String key, String value, String source) {
      this.key = key;
      this.value = value;
      this.source = source;
    }

    @Override
    public Object getSource() {
      return source;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public String getKey() {
      return key;
    }
  }
}
