package com.kepak.mule.keyvault.vault;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Thread-safe TTL cache in front of a {@link SecretSource}. Shared by the placeholder and operation paths.
 *
 * <ul>
 *   <li>A value fetched less than one TTL ago is served without contacting Key Vault. During an outage this
 *       is what keeps a fresh value available.</li>
 *   <li>Once a value has expired it is refetched. If the refetch fails, the failure is surfaced. An expired
 *       value is never served (decision D2).</li>
 *   <li>A secret never fetched successfully fails immediately on error.</li>
 *   <li>Failures are not cached. The next call tries the vault again.</li>
 *   <li>Concurrent misses for the same name share one fetch (single-flight).</li>
 * </ul>
 */
public class SecretCache {

  private record Entry(String value, long fetchedAtMillis) {
  }

  private final SecretSource source;
  private final long ttlMillis;
  private final LongSupplier clockMillis;
  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

  public SecretCache(SecretSource source, Duration ttl) {
    this(source, ttl, System::currentTimeMillis);
  }

  SecretCache(SecretSource source, Duration ttl, LongSupplier clockMillis) {
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    this.source = source;
    this.ttlMillis = ttl.toMillis();
    this.clockMillis = clockMillis;
  }

  public String get(String secretName) {
    Entry cached = entries.get(secretName);
    if (cached != null && isFresh(cached)) {
      return cached.value();
    }
    Object lock = locks.computeIfAbsent(secretName, k -> new Object());
    synchronized (lock) {
      // Another thread may have refreshed the entry while this one waited.
      cached = entries.get(secretName);
      if (cached != null && isFresh(cached)) {
        return cached.value();
      }
      // A failure here propagates and nothing is cached.
      String value = source.getSecretValue(secretName);
      entries.put(secretName, new Entry(value, clockMillis.getAsLong()));
      return value;
    }
  }

  private boolean isFresh(Entry entry) {
    return clockMillis.getAsLong() - entry.fetchedAtMillis() < ttlMillis;
  }
}
