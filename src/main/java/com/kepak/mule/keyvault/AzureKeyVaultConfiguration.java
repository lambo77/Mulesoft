package com.kepak.mule.keyvault;

import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Password;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Connection settings for one Azure Key Vault. Values are validated by {@link ConfigValidation}
 * when the configuration is created. Secret values are never included in error messages.
 */
@Configuration(name = "config")
public class AzureKeyVaultConfiguration {

  @Parameter
  @DisplayName("Vault URL")
  @Summary("Key Vault base URL, for example https://kv-example.vault.azure.net/")
  private String vaultUrl;

  @Parameter
  @DisplayName("Client ID")
  @Summary("Application (client) ID of the Azure App Registration")
  private String clientId;

  @Parameter
  @Password
  @DisplayName("Client secret")
  @Summary("Client secret of the Azure App Registration. Store in CloudHub secure properties.")
  private String clientSecret;

  @Parameter
  @DisplayName("Tenant ID")
  @Summary("Microsoft Entra tenant (directory) ID")
  private String tenantId;

  @Parameter
  @DisplayName("Probe secret name")
  @Summary("Name of a secret read once at configuration creation to validate credentials")
  private String probeSecretName;

  @Parameter
  @Optional(defaultValue = "60")
  @DisplayName("TTL (minutes)")
  @Summary("How long a fetched secret is cached before it is read again. Minimum 1.")
  @Example("60")
  private int ttlMinutes;

  /** Used by the Mule runtime; parameters are injected by reflection. */
  public AzureKeyVaultConfiguration() {
  }

  AzureKeyVaultConfiguration(String vaultUrl, String clientId, String clientSecret, String tenantId,
      String probeSecretName, int ttlMinutes) {
    this.vaultUrl = vaultUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tenantId = tenantId;
    this.probeSecretName = probeSecretName;
    this.ttlMinutes = ttlMinutes;
  }

  public String getVaultUrl() {
    return vaultUrl;
  }

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getProbeSecretName() {
    return probeSecretName;
  }

  public int getTtlMinutes() {
    return ttlMinutes;
  }

  @Override
  public String toString() {
    // Deliberately omits clientSecret.
    return "AzureKeyVaultConfiguration{vaultUrl=" + vaultUrl + ", clientId=" + clientId
        + ", tenantId=" + tenantId + ", probeSecretName=" + probeSecretName + ", ttlMinutes=" + ttlMinutes + "}";
  }
}
