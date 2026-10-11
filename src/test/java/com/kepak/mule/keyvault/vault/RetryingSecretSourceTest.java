package com.kepak.mule.keyvault.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryingSecretSourceTest {

  /** Scripted source: each call pops the next outcome. A String is returned; a RuntimeException is thrown. */
  static class ScriptedSource implements SecretSource {
    final Deque<Object> outcomes = new ArrayDeque<>();
    int calls;

    @Override
    public String getSecretValue(String secretName) {
      calls++;
      Object next = outcomes.poll();
      if (next instanceof RuntimeException) {
        throw (RuntimeException) next;
      }
      return (String) next;
    }
  }

  static class RecordingSleeper implements RetryingSecretSource.Sleeper {
    final List<Long> sleeps = new ArrayList<>();

    @Override
    public void sleep(long millis) {
      sleeps.add(millis);
    }
  }

  private static final String SECRET = "d365-client-secret";

  @Test
  void returnsValueWithoutRetryWhenFirstCallSucceeds() {
    ScriptedSource source = new ScriptedSource();
    source.outcomes.add("value-1");
    RecordingSleeper sleeper = new RecordingSleeper();

    String value = new RetryingSecretSource(source, 3, 500, sleeper).getSecretValue(SECRET);

    assertEquals("value-1", value);
    assertEquals(1, source.calls);
    assertEquals(0, sleeper.sleeps.size());
  }

  @Test
  void retriesThrottlingWithDoublingBackoffThenSucceeds() {
    ScriptedSource source = new ScriptedSource();
    source.outcomes.add(new VaultUnavailableException(SECRET));
    source.outcomes.add(new VaultUnavailableException(SECRET));
    source.outcomes.add("value-2");
    RecordingSleeper sleeper = new RecordingSleeper();

    String value = new RetryingSecretSource(source, 3, 500, sleeper).getSecretValue(SECRET);

    assertEquals("value-2", value);
    assertEquals(3, source.calls);
    assertEquals(List.of(500L, 1000L), sleeper.sleeps);
  }

  @Test
  void givesUpAfterMaxRetriesAndSurfacesUnavailable() {
    ScriptedSource source = new ScriptedSource();
    for (int i = 0; i < 10; i++) {
      source.outcomes.add(new VaultUnavailableException(SECRET));
    }
    RecordingSleeper sleeper = new RecordingSleeper();

    assertThrows(VaultUnavailableException.class,
        () -> new RetryingSecretSource(source, 3, 500, sleeper).getSecretValue(SECRET));

    assertEquals(4, source.calls);
    assertEquals(3, sleeper.sleeps.size());
  }

  @Test
  void doesNotRetryNotFound() {
    ScriptedSource source = new ScriptedSource();
    source.outcomes.add(new SecretNotFoundException(SECRET));
    RecordingSleeper sleeper = new RecordingSleeper();

    assertThrows(SecretNotFoundException.class,
        () -> new RetryingSecretSource(source, 3, 500, sleeper).getSecretValue(SECRET));

    assertEquals(1, source.calls);
    assertEquals(0, sleeper.sleeps.size());
  }

  @Test
  void doesNotRetryAuthenticationFailure() {
    ScriptedSource source = new ScriptedSource();
    source.outcomes.add(new VaultAuthenticationException(SECRET));
    RecordingSleeper sleeper = new RecordingSleeper();

    assertThrows(VaultAuthenticationException.class,
        () -> new RetryingSecretSource(source, 3, 500, sleeper).getSecretValue(SECRET));

    assertEquals(1, source.calls);
  }

  @Test
  void zeroRetriesMeansOneAttempt() {
    ScriptedSource source = new ScriptedSource();
    source.outcomes.add(new VaultUnavailableException(SECRET));

    assertThrows(VaultUnavailableException.class,
        () -> new RetryingSecretSource(source, 0, 500, new RecordingSleeper()).getSecretValue(SECRET));

    assertEquals(1, source.calls);
  }

  @Test
  void errorMessagesNameTheSecretNotItsValue() {
    VaultException e = new SecretNotFoundException(SECRET);
    assertEquals(true, e.getMessage().contains(SECRET));
  }
}
