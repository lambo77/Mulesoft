package com.kepak.mule.keyvault;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure validation rules for the Key Vault configuration and secret names. No Azure or Mule
 * runtime dependencies, so these rules are unit-testable on their own.
 */
public final class ConfigValidation {

  static final int MIN_TTL_MINUTES = 1;
  static final int MAX_SECRET_NAME_LENGTH = 127;

  private static final Pattern SECRET_NAME = Pattern.compile("^[A-Za-z0-9-]{1," + MAX_SECRET_NAME_LENGTH + "}$");
  private static final Pattern VAULT_HOST = Pattern.compile("^[a-z0-9-]+\\.vault\\.azure\\.net$");

  private ConfigValidation() {
  }

  /**
   * @throws ConfigValidationException naming the first invalid parameter. Never includes a secret value.
   */
  public static void validate(AzureKeyVaultConfiguration config) {
    validateVaultUrl(config.getVaultUrl());
    requireNonBlank("clientId", config.getClientId());
    requireNonBlank("clientSecret", config.getClientSecret());
    requireNonBlank("tenantId", config.getTenantId());
    validateSecretName("probeSecretName", config.getProbeSecretName());
    validateTtl(config.getTtlMinutes());
  }

  static void validateVaultUrl(String vaultUrl) {
    requireNonBlank("vaultUrl", vaultUrl);
    URI uri;
    try {
      uri = URI.create(vaultUrl.trim());
    } catch (IllegalArgumentException e) {
      throw new ConfigValidationException("vaultUrl is not a valid URI");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new ConfigValidationException("vaultUrl must use https");
    }
    String host = uri.getHost();
    if (host == null || !VAULT_HOST.matcher(host.toLowerCase(Locale.ROOT)).matches()) {
      throw new ConfigValidationException("vaultUrl host must match <name>.vault.azure.net (sovereign clouds are not supported)");
    }
  }

  /** True when the value is a valid Key Vault secret name. */
  public static boolean isValidSecretName(String secretName) {
    return secretName != null && SECRET_NAME.matcher(secretName).matches();
  }

  /** Key Vault secret names: letters, digits and hyphens, 1 to 127 characters. */
  public static void validateSecretName(String parameter, String secretName) {
    requireNonBlank(parameter, secretName);
    if (!SECRET_NAME.matcher(secretName).matches()) {
      throw new ConfigValidationException(parameter + " must be 1 to 127 letters, digits or hyphens");
    }
  }

  static void validateTtl(int ttlMinutes) {
    if (ttlMinutes < MIN_TTL_MINUTES) {
      throw new ConfigValidationException("ttlMinutes must be at least " + MIN_TTL_MINUTES);
    }
  }

  private static void requireNonBlank(String parameter, String value) {
    if (value == null || value.isBlank()) {
      throw new ConfigValidationException(parameter + " is required");
    }
  }
}
