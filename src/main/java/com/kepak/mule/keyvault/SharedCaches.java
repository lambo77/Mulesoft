package com.kepak.mule.keyvault;

import com.kepak.mule.keyvault.vault.SecretCache;
import com.kepak.mule.keyvault.vault.SecretSource;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * One {@link SecretCache} per vault identity, shared by the placeholder provider and the
 * {@code azure-kv:get-secret} operation. The Mule runtime builds the provider and the operation's config
 * as separate objects, so sharing has to happen here.
 *
 * <p>The key holds vault URL, client ID, tenant ID and TTL. It never holds the client secret.
 */
final class SharedCaches {

  private static final ConcurrentHashMap<String, SecretCache> CACHES = new ConcurrentHashMap<>();

  private SharedCaches() {
  }

  static SecretCache forConfig(AzureKeyVaultConfiguration config,
                               Function<AzureKeyVaultConfiguration, SecretSource> sourceFactory) {
    String key = config.getVaultUrl() + "|" + config.getClientId() + "|" + config.getTenantId() + "|"
        + config.getTtlMinutes();
    return CACHES.computeIfAbsent(key,
        k -> new SecretCache(sourceFactory.apply(config), Duration.ofMinutes(config.getTtlMinutes())));
  }

  /** Test support: forget all cached identities. */
  static void clear() {
    CACHES.clear();
  }
}
