# Azure Key Vault Configuration Property Provider (Mule 4)

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
