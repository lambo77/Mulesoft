package com.kepak.mule.keyvault.vault;

/** Throttling (HTTP 429), server errors, timeouts or network failures. Retried, then surfaced. */
public class VaultUnavailableException extends VaultException {

  public VaultUnavailableException(String secretName, Throwable cause) {
    super("Key Vault is unavailable or throttling while reading secret '" + secretName + "'", cause);
  }

  public VaultUnavailableException(String secretName) {
    this(secretName, null);
  }
}
