package com.kepak.mule.keyvault.vault;

import com.azure.core.exception.AzureException;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.kepak.mule.keyvault.AzureKeyVaultConfiguration;

/**
 * {@link SecretSource} backed by the Azure Key Vault secrets SDK, authenticated with a client secret.
 * Maps SDK failures onto the {@link VaultException} hierarchy.
 */
public class AzureSecretSource implements SecretSource {

  private final SecretClient client;

  public AzureSecretSource(AzureKeyVaultConfiguration config) {
    this.client = new SecretClientBuilder()
        .vaultUrl(config.getVaultUrl())
        .credential(new ClientSecretCredentialBuilder()
            .tenantId(config.getTenantId())
            .clientId(config.getClientId())
            .clientSecret(config.getClientSecret())
            .build())
        .buildClient();
  }

  @Override
  public String getSecretValue(String secretName) {
    try {
      return client.getSecret(secretName).getValue();
    } catch (ResourceNotFoundException e) {
      throw new SecretNotFoundException(secretName);
    } catch (ClientAuthenticationException e) {
      throw new VaultAuthenticationException(secretName, e);
    } catch (HttpResponseException e) {
      throw mapStatus(e.getResponse().getStatusCode(), secretName, e);
    } catch (AzureException e) {
      throw new VaultUnavailableException(secretName, e);
    }
  }

  /** Maps an HTTP status from Key Vault onto the exception hierarchy. Package-private for tests. */
  static VaultException mapStatus(int status, String secretName, Throwable cause) {
    switch (status) {
      case 401:
      case 403:
        return new VaultAuthenticationException(secretName, cause);
      case 404:
        return new SecretNotFoundException(secretName);
      case 408:
      case 429:
        return new VaultUnavailableException(secretName, cause);
      default:
        if (status >= 500) {
          return new VaultUnavailableException(secretName, cause);
        }
        return new VaultException("Key Vault returned HTTP " + status + " reading secret '" + secretName + "'", cause);
    }
  }
}
