# Azure Key Vault Configuration Property Provider (Mule 4)

## WP5b status (azure-kv:get-secret operation)

Written:
- `AzureKeyVaultOperations.getSecret(@Config AzureKeyVaultConfiguration, secretName)`. The Mule SDK exposes it as `azure-kv:get-secret`. Validates the config and the secret name, then reads through the shared cache. Rotated values are picked up within `ttlMinutes` (D1).
- `SharedCaches`: one `SecretCache` per vault identity (vault URL, client ID, tenant ID, TTL). Never keyed on the secret. The provider and the operation both use it, so the two paths share one cache and one client. This was needed because the runtime builds the provider's config and the operation's config as separate objects.
- `AzureKeyVaultExtension` now declares `@Operations(AzureKeyVaultOperations.class)`.

Verified:
- 6 operation tests pass, including a test that the placeholder provider and the operation share one cache (no second vault call).
- All six unit test classes pass: 49 tests in total.
- `javac --release 17` compiles everything against the 4.9 classpath.

Open:
- **Named error type (D2).** The plan asks for a named Mule error type on runtime failures. The SDK's `@Throws` and `ErrorTypeProvider` exist in `mule-extensions-api` 1.9.0, but the `ErrorTypeDefinition` annotation is not in that jar. Until that is found, operation failures surface as the `VaultException` hierarchy. Not yet a named Mule error type.
- **Sensitive output.** No annotation for marking operation output as sensitive was found in the 1.9.0 jar. The output is not logged by our code, but flow logging is not blocked by an annotation yet.
- **Runtime behaviour.** The operation's `@Config` binding and the shared cache are unverified until WP7 runs on Mule 4.9.
- Maven build still blocked in the annotation processor (see WP2).

## WP5 status (SPI factory and placeholder provider)

Written:
- `AzureKeyVaultPropertiesProviderFactory`: implements `ConfigurationPropertiesProviderFactory`. `getSupportedComponentIdentifier()` returns namespace `azure-kv`, name `config`. `createProvider(ComponentAst, UnaryOperator<String>, ResourceProvider)` builds the provider.
- `AzureKeyVaultPropertiesProvider`: reads each parameter through the resolver, validates the config (WP2), builds `Retrying(AzureSecretSource)` in a `SecretCache`, and reads the probe secret once (D3). `provide(key)` returns empty for keys that are not valid secret names, and otherwise reads through the cache. `getDescription()` names the vault and not the secret.
- Services file now lists the factory class.

Verified:
- `javac --release 17` compiles the factory and provider against the 4.9 `ConfigurationPropertiesProviderFactory`, `ConfigurationPropertiesProvider`, `ConfigurationProperty` and `ComponentIdentifier` signatures.
- 43 unit tests pass across five classes (validation, provider/factory, retry, status mapping, cache).

Open gates (must be confirmed in WP7, not assumed):
- **Routing.** The design assumes the runtime only asks this provider for `azure-kv::` placeholders. If it asks every provider for every key, then a valid-looking name that belongs to another provider raises `SecretNotFoundException` and breaks start-up. Confirm before use.
- **Key format.** The design assumes `provide()` receives the name without the `azure-kv::` prefix. Unverified.
- Not run against a live vault yet.

Not done in WP5: the operation (WP5b).

## WP4 status (TTL cache)

Written: `vault/SecretCache`. Thread-safe `ConcurrentHashMap` of value and fetch time, TTL from `ttlMinutes`. Shared by the placeholder and operation paths (WP5/WP5b).
- Fresh value (age under one TTL): served with no vault call. This is how a fresh value survives an outage.
- Expired or missing: fetched through the source. On failure the failure surfaces, and an expired value is never served (D2).
- Failures are not cached.
- Concurrent misses for one name share one fetch (per-key lock, double-checked).

Verified: 9 unit tests pass, including single-flight with 20 concurrent threads (one vault call) and the D2 outage cases. Run with the reflective runner.

Not done in WP4: `Retry-After` honouring and cache eviction of unused names. Neither is required by the plan.

## WP3 status (vault access layer)

Written (`com.kepak.mule.keyvault.vault`):
- `SecretSource`: interface, `String getSecretValue(String)`. Keeps Azure types out of the unit tests.
- `AzureSecretSource`: builds a `SecretClient` with `ClientSecretCredential` from the config. Maps SDK failures to the exception hierarchy. `mapStatus` maps HTTP status: 401/403 auth, 404 not found, 408/429/5xx unavailable (retryable), other 4xx plain `VaultException`.
- `RetryingSecretSource`: retries only `VaultUnavailableException`. Exponential backoff from 500 ms, 3 retries (4 attempts), injectable `Sleeper`.
- Exceptions: `VaultException` (base), `SecretNotFoundException`, `VaultAuthenticationException`, `VaultUnavailableException`. Messages name the secret, never its value.

Verified:
- 22 unit tests pass (10 validation, 7 retry, 5 status mapping), run through the reflective runner.
- `AzureSecretSource` compiles against `azure-security-keyvault-secrets` 4.11.1, `azure-identity` 1.18.4 and `azure-core`.

Not done in WP3:
- Not run against a live Key Vault. That needs the dev vault and an App Registration (setup guide Section 2).
- `Retry-After` headers are not honoured. Backoff is fixed.
- The start-up probe read is still to be wired, in the provider factory (WP5).

## WP2 status (extension, config, validation)

Written:
- `AzureKeyVaultExtension`: `@Extension(name = "Azure Key Vault Properties Provider")` with `@Configurations`.
- `AzureKeyVaultConfiguration`: `@Configuration(name = "config")`, parameters `vaultUrl`, `clientId`, `clientSecret` (`@Password`), `tenantId`, `probeSecretName`, `ttlMinutes` (default 60). `toString` omits the secret.
- `ConfigValidation`: pure rules. HTTPS `*.vault.azure.net` host, required fields, secret names 1 to 127 letters/digits/hyphens, TTL minimum 1. Error messages name the parameter, never the value.
- `ConfigValidationTest`: 10 cases.

Verified:
- `ConfigValidationTest` passes 10/10 when run through a reflective runner (see below).
- Main sources compile with `javac --release 17` against the resolved 4.9 classpath.

Not done in WP2:
- The start-up probe read (`probeSecretName`). It needs the vault client from WP3, so it is wired in WP3/WP5. The SDK has no `@Initialise` hook in 1.9.0, so validation runs from the provider factory at config creation.

Blocked: `mvn test` / `mvn install` fail in the Mule annotation processor (`maven-processor-plugin`, `process-sources` phase). It reports `NoClassDefFoundError: org/mule/runtime/module/extension/api/loader/java/DefaultJavaExtensionModelLoader`, although that class is in `mule-module-extensions-support` 4.9.0 on the processor classpath. The parent also brings `mule-module-extensions-support` 3.7.0 (`org.mule.modules`). That version clash is the likely cause. Not yet resolved.

Test run without Maven (substitute for surefire, since no JUnit launcher is cached):
    javac --release 17 -proc:none -d <out> -cp "<classpath>" <sources>
    java -cp "<classpath>" RunTests com.kepak.mule.keyvault.ConfigValidationTest


Status: WP1 (skeleton) in progress. Build is BLOCKED in this environment, see below.
Plan: `plan-FINAL.md`. Guide: `setup-and-implementation-guide.md`.

## WP1 status

Gate passed: SPI contract verified against the Mule 4.9 jars (see below). Build resolves all dependencies.
Stops at the extension-descriptor step because no `@Extension` Java class exists yet. That is WP2 work.

Files:
- `pom.xml`: `mule-modules-parent` 1.1.3 (parent pins 4.1.1, overridden to 4.9.0), `mule-extension` packaging, `release 17`, `mule-extensions-api` 1.9.0, `mule-properties-api` 1.5.0 (both provided), azure-sdk-bom 1.3.8, azure-identity, azure-security-keyvault-secrets. Plugin repositories declared over https:// (the parent uses http://, which Maven blocks).
- `src/main/resources/META-INF/mule-artifact.json`: minimal descriptor. Fields still UNVERIFIED.
- `src/main/resources/META-INF/services/org.mule.runtime.properties.api.ConfigurationPropertiesProviderFactory`: empty, to be filled in WP5.

Verified against the 4.9 jars (`mule-properties-api` 1.5.0, `mule-module-properties-config` 4.9.0):
- SPI interface: `org.mule.runtime.properties.api.ConfigurationPropertiesProviderFactory`
  - `ComponentIdentifier getSupportedComponentIdentifier()`
  - `ConfigurationPropertiesProvider createProvider(ComponentAst, UnaryOperator<String>, ResourceProvider)`
- Provider interface: `org.mule.runtime.properties.api.ConfigurationPropertiesProvider`
  - `Optional<? extends ConfigurationProperty> provide(String key)`
  - `String getDescription()`
- Services file: `META-INF/services/org.mule.runtime.properties.api.ConfigurationPropertiesProviderFactory`
- Annotations in `mule-extensions-api` 1.9.0: `org.mule.runtime.extension.api.annotation.Extension`, `...annotation.param.Parameter`, `...annotation.param.display.Password`

Corrections to plan v4 / FINAL:
- The SPI package and services file name in the plan (`org.mule.runtime.config.api.dsl.model.properties`) are wrong for 4.9. The correct name is above.
- The provider method is `provide(String)`, not `getConfigurationProperty(String)`.
- The extension API for 4.9 is `mule-extensions-api` 1.9.0, not the 1.1.5 the parent pins.

Still open for WP1/WP2: `mule-artifact.json` fields, and the `<azure-kv:config>` XML shape (not yet checked).

## Build

    mvn clean install

Requires JDK 17 and access to `repository.mulesoft.org` (see above).
