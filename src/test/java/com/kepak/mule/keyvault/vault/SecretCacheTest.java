package com.kepak.mule.keyvault.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SecretCacheTest {

  private static final String SECRET = "d365-client-secret";
  private static final Duration TTL = Duration.ofMinutes(60);
  private static final long TTL_MILLIS = TTL.toMillis();

  /** Source whose result and availability can be changed between calls. */
  static class ControllableSource implements SecretSource {
    final AtomicInteger calls = new AtomicInteger();
    volatile String value = "v1";
    volatile RuntimeException failure;
    long delayMillis;

    @Override
    public String getSecretValue(String secretName) {
      calls.incrementAndGet();
      if (delayMillis > 0) {
        try {
          Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      if (failure != null) {
        throw failure;
      }
      return value;
    }
  }

  private static SecretCache cache(SecretSource source, AtomicLong clock) {
    return new SecretCache(source, TTL, clock::get);
  }

  @Test
  void servesFreshValueWithoutCallingVault() {
    ControllableSource source = new ControllableSource();
    AtomicLong clock = new AtomicLong(0);
    SecretCache cache = cache(source, clock);

    assertEquals("v1", cache.get(SECRET));
    clock.set(TTL_MILLIS - 1);
    assertEquals("v1", cache.get(SECRET));

    assertEquals(1, source.calls.get());
  }

  @Test
  void refetchesOnceTtlHasExpired() {
    ControllableSource source = new ControllableSource();
    AtomicLong clock = new AtomicLong(0);
    SecretCache cache = cache(source, clock);

    cache.get(SECRET);
    source.value = "v2"; // secret rotated in Key Vault
    clock.set(TTL_MILLIS);

    assertEquals("v2", cache.get(SECRET));
    assertEquals(2, source.calls.get());
  }

  @Test
  void outageWithinTtlIsServedFromCache() {
    ControllableSource source = new ControllableSource();
    AtomicLong clock = new AtomicLong(0);
    SecretCache cache = cache(source, clock);

    cache.get(SECRET);
    source.failure = new VaultUnavailableException(SECRET);
    clock.set(TTL_MILLIS / 2);

    assertEquals("v1", cache.get(SECRET));
  }

  @Test
  void outageAfterTtlFailsRatherThanServingExpiredValue() {
    ControllableSource source = new ControllableSource();
    AtomicLong clock = new AtomicLong(0);
    SecretCache cache = cache(source, clock);

    cache.get(SECRET);
    source.failure = new VaultUnavailableException(SECRET);
    clock.set(TTL_MILLIS + 1);

    assertThrows(VaultUnavailableException.class, () -> cache.get(SECRET));
  }

  @Test
  void neverCachedSecretFailsImmediatelyDuringOutage() {
    ControllableSource source = new ControllableSource();
    source.failure = new VaultUnavailableException(SECRET);
    SecretCache cache = cache(source, new AtomicLong(0));

    assertThrows(VaultUnavailableException.class, () -> cache.get(SECRET));
  }

  @Test
  void failuresAreNotCached() {
    ControllableSource source = new ControllableSource();
    source.failure = new VaultUnavailableException(SECRET);
    SecretCache cache = cache(source, new AtomicLong(0));

    assertThrows(VaultUnavailableException.class, () -> cache.get(SECRET));
    source.failure = null;

    assertEquals("v1", cache.get(SECRET));
    assertEquals(2, source.calls.get());
  }

  @Test
  void authAndNotFoundFailuresSurfaceFromCache() {
    ControllableSource source = new ControllableSource();
    source.failure = new SecretNotFoundException(SECRET);
    SecretCache cache = cache(source, new AtomicLong(0));

    assertThrows(SecretNotFoundException.class, () -> cache.get(SECRET));
  }

  @Test
  void concurrentMissesShareOneFetch() throws InterruptedException {
    ControllableSource source = new ControllableSource();
    source.delayMillis = 100;
    SecretCache cache = cache(source, new AtomicLong(0));

    int threads = 20;
    CountDownLatch start = new CountDownLatch(1);
    List<String> results = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      Thread t = new Thread(() -> {
        try {
          start.await();
          String v = cache.get(SECRET);
          synchronized (results) {
            results.add(v);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      workers.add(t);
      t.start();
    }
    start.countDown();
    for (Thread t : workers) {
      t.join();
    }

    assertEquals(1, source.calls.get());
    assertEquals(threads, results.size());
    assertTrue(results.stream().allMatch("v1"::equals));
  }

  @Test
  void rejectsNonPositiveTtl() {
    assertThrows(IllegalArgumentException.class, () -> new SecretCache(new ControllableSource(), Duration.ZERO));
  }
}
