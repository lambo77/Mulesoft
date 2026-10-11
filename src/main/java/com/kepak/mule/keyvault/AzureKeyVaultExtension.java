package com.kepak.mule.keyvault;

import org.mule.runtime.api.meta.Category;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Operations;

/**
 * Mule extension declaration for the Azure Key Vault configuration properties provider.
 * Configuration parameters live in {@link AzureKeyVaultConfiguration}.
 */
@Extension(name = "Azure Key Vault Properties Provider", vendor = "Kepak", category = Category.COMMUNITY)
@Configurations(AzureKeyVaultConfiguration.class)
@Operations(AzureKeyVaultOperations.class)
public class AzureKeyVaultExtension {
}
