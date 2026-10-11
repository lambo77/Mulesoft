package com.kepak.mule.keyvault.vault;

/**
 * Base failure for reading a secret. Messages name the vault secret, never its value.
 * Only {@link VaultUnavailableException} is retried.
 */
public class VaultException extends RuntimeException {

  public VaultException(String message) {
    super(message);
  }

  public VaultException(String message, Throwable cause) {
    super(message, cause);
  }
}
