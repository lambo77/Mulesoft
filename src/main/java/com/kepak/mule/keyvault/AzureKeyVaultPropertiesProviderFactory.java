package com.kepak.mule.keyvault;

import com.kepak.mule.keyvault.vault.AzureSecretSource;
import com.kepak.mule.keyvault.vault.RetryingSecretSource;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.properties.api.ConfigurationPropertiesProvider;
import org.mule.runtime.properties.api.ConfigurationPropertiesProviderFactory;
import org.mule.runtime.properties.api.ResourceProvider;

import java.util.function.UnaryOperator;

/**
 * Registered with the Mule runtime through the SPI services file. Matches the {@code azure-kv:config}
 * element and creates the placeholder provider for {@code ${azure-kv::<secret>}}.
 */
public class AzureKeyVaultPropertiesProviderFactory implements ConfigurationPropertiesProviderFactory {

  static final String NAMESPACE = "azure-kv";
  static final String CONFIG_NAME = "config";

  @Override
  public ComponentIdentifier getSupportedComponentIdentifier() {
    return ComponentIdentifier.builder().namespace(NAMESPACE).name(CONFIG_NAME).build();
  }

  @Override
  public ConfigurationPropertiesProvider createProvider(ComponentAst componentModel,
                                                        UnaryOperator<String> parameterResolver,
                                                        ResourceProvider resourceProvider) {
    return AzureKeyVaultPropertiesProvider.create(parameterResolver,
        config -> new RetryingSecretSource(new AzureSecretSource(config)));
  }
}
