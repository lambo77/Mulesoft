package com.kepak.mule.keyvault;

import com.kepak.mule.keyvault.vault.AzureSecretSource;
import com.kepak.mule.keyvault.vault.RetryingSecretSource;
import com.kepak.mule.keyvault.vault.SecretSource;
import com.kepak.mule.keyvault.vault.VaultException;
import com.kepak.mule.keyvault.vault.VaultLog;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.function.Function;

/**
 * The {@code azure-kv:get-secret} operation. The only path that picks up a rotated secret without a restart:
 * it reads through the TTL cache at call time (decision D1).
 */
public class AzureKeyVaultOperations {

  @DisplayName("Get secret")
  @Summary("Reads a Key Vault secret through the TTL cache. Rotated values are picked up within ttlMinutes.")
  public String getSecret(@Config AzureKeyVaultConfiguration config,
                          @DisplayName("Secret name") @Summary("Key Vault secret name: letters, digits and hyphens") String secretName) {
    return getSecret(config, secretName, c -> new RetryingSecretSource(new AzureSecretSource(c)));
  }

  /** Testable core. The public operation supplies the Azure-backed source. */
  static String getSecret(AzureKeyVaultConfiguration config, String secretName,
                          Function<AzureKeyVaultConfiguration, SecretSource> sourceFactory) {
    ConfigValidation.validate(config);
    ConfigValidation.validateSecretName("secretName", secretName);
    try {
      return SharedCaches.forConfig(config, sourceFactory).get(secretName);
    } catch (VaultException e) {
      // The returned value is never logged, on success or failure.
      VaultLog.error("azure-kv:get-secret failed for secret '" + secretName + "': " + e.getMessage());
      throw e;
    }
  }
}
