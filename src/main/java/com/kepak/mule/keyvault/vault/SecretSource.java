package com.kepak.mule.keyvault.vault;

/** Reads one secret value by name. The seam that keeps Azure SDK types out of the unit tests. */
public interface SecretSource {

  /**
   * @return the secret value
   * @throws VaultException on any failure; see the subclasses for retry behaviour
   */
  String getSecretValue(String secretName);
}
