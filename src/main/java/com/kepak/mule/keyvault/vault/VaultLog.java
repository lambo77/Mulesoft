package com.kepak.mule.keyvault.vault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Logging for the plugin. Every message is written through here so tests can capture it.
 *
 * <p>Callers must pass secret names and vault URLs only. Never a secret value, a client secret, or an
 * object whose {@code toString} could include one.
 */
public final class VaultLog {

  public enum Level { INFO, WARN, ERROR }

  private static final Logger LOGGER = LoggerFactory.getLogger("com.kepak.mule.keyvault");

  private static volatile BiConsumer<Level, String> sink = VaultLog::writeToSlf4j;

  private VaultLog() {
  }

  public static void info(String message) {
    sink.accept(Level.INFO, message);
  }

  public static void warn(String message) {
    sink.accept(Level.WARN, message);
  }

  public static void error(String message) {
    sink.accept(Level.ERROR, message);
  }

  /** Test support: replace the sink. Pass null to restore the default. */
  public static void setSink(BiConsumer<Level, String> replacement) {
    sink = replacement == null ? VaultLog::writeToSlf4j : replacement;
  }

  private static void writeToSlf4j(Level level, String message) {
    switch (level) {
      case INFO:
        LOGGER.info(message);
        break;
      case WARN:
        LOGGER.warn(message);
        break;
      default:
        LOGGER.error(message);
    }
  }
}
