package com.kepak.mule.keyvault.vault;

/** Bad credentials, or the identity lacks read access to the secret. Not retried. */
public class VaultAuthenticationException extends VaultException {

  public VaultAuthenticationException(String secretName, Throwable cause) {
    super("Key Vault rejected the credentials reading secret '" + secretName
        + "'. Check clientId, clientSecret, tenantId and the Key Vault role assignment.", cause);
  }

  public VaultAuthenticationException(String secretName) {
    this(secretName, null);
  }
}
