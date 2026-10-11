package com.kepak.mule.keyvault.vault;

/**
 * Retries only {@link VaultUnavailableException}, with exponential backoff capped by a retry count.
 * Auth, not-found and other failures are surfaced immediately.
 */
public class RetryingSecretSource implements SecretSource {

  /** Sleeps between attempts. Injected so tests run without waiting. */
  public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  public static final int DEFAULT_MAX_RETRIES = 3;
  public static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 500L;

  private final SecretSource delegate;
  private final int maxRetries;
  private final long initialBackoffMillis;
  private final Sleeper sleeper;

  public RetryingSecretSource(SecretSource delegate) {
    this(delegate, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_BACKOFF_MILLIS, Thread::sleep);
  }

  public RetryingSecretSource(SecretSource delegate, int maxRetries, long initialBackoffMillis, Sleeper sleeper) {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must not be negative");
    }
    this.delegate = delegate;
    this.maxRetries = maxRetries;
    this.initialBackoffMillis = initialBackoffMillis;
    this.sleeper = sleeper;
  }

  @Override
  public String getSecretValue(String secretName) {
    long backoff = initialBackoffMillis;
    for (int retry = 0; ; retry++) {
      try {
        return delegate.getSecretValue(secretName);
      } catch (VaultUnavailableException e) {
        if (retry >= maxRetries) {
          throw e;
        }
        pause(backoff, secretName, e);
        backoff *= 2;
      }
    }
  }

  private void pause(long millis, String secretName, VaultUnavailableException cause) {
    try {
      sleeper.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultUnavailableException(secretName, cause);
    }
  }
}
