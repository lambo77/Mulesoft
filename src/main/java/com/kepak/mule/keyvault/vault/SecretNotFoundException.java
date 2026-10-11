package com.kepak.mule.keyvault.vault;

/** The secret does not exist, or the name is wrong. Not retried. */
public class SecretNotFoundException extends VaultException {

  public SecretNotFoundException(String secretName) {
    super("Key Vault secret '" + secretName + "' was not found");
  }
}
