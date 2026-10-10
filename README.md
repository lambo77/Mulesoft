# Azure Key Vault Configuration Property Provider (Mule 4)

Status: WP1 (skeleton) in progress. Build is BLOCKED in this environment, see below.
Plan: `plan-FINAL.md`. Guide: `setup-and-implementation-guide.md`.

## WP1 status

Done (files present):
- `pom.xml`: `mule-modules-parent` 1.1.3, `mule-extension` packaging, `release 17`, azure-sdk-bom 1.3.8, azure-identity, azure-security-keyvault-secrets.
- `src/main/resources/META-INF/mule-artifact.json`: minimal descriptor. Fields UNVERIFIED.
- `src/main/resources/META-INF/services/org.mule.runtime.config.api.dsl.model.properties.ConfigurationPropertiesProviderFactory`: empty placeholder. Name UNVERIFIED (plan 2.4).

Not done, blocked:
- `mvn validate` fails: `org.mule.runtime.plugins:mule-extensions-maven-plugin:1.1.6` and `mule-extension` packaging cannot be resolved. Both come from the MuleSoft repository (`repository.mulesoft.org`), which this environment's network policy denies (HTTP 403).
- `org.mule.runtime:mule-extensions-api:1.1.5` (the version the parent pins) is not on Maven Central (404).
- `org.mule.sdk:mule-sdk-api`, the coordinate in plan 2.4, is not on Maven Central either.

Findings to fix before WP2:
- `mule-modules-parent` 1.1.3 pins `mule.version` 4.1.1, not the 4.9 target. The POM overrides `mule.version` to 4.9.0, but the parent's SDK version must be confirmed against 4.9 from the MuleSoft repository.
- `azure-identity` latest on Central is a beta (1.19.0-beta.2). Pick a release version.
- The two-week minimum age rule for version pins is not yet checked.

## Build

    mvn clean install

Requires JDK 17 and access to `repository.mulesoft.org` (see above).
