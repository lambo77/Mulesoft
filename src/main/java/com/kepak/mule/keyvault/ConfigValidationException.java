package com.kepak.mule.keyvault;

/** Raised when the Key Vault configuration or a secret name is invalid. */
public class ConfigValidationException extends IllegalArgumentException {

  public ConfigValidationException(String message) {
    super(message);
  }
}
