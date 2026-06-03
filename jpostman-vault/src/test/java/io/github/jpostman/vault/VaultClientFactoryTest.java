package io.github.jpostman.vault;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.response.LookupResponse;

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class VaultClientFactoryTest {

    private final VaultClientFactory vaultClientFactory = new VaultClientFactory();

    private Properties vaultProperties;

    @BeforeClass
    public void loadProperties() throws Exception {
        vaultProperties = loadVaultLocalProperties();

        // Make properties available to VaultSettings.required(...)
        // This is useful if VaultSettings reads System properties or environment variables.
        copyToSystemPropertiesIfMissing(vaultProperties);
    }

    private static Properties loadVaultLocalProperties() throws Exception {
        Properties properties = new Properties();

        try (InputStream input = VaultClientFactoryTest.class
                .getClassLoader()
                .getResourceAsStream("vault-local.properties")) {

            if (input == null) {
                throw new SkipException("Skipping: src/test/resources/vault-local.properties not found");
            }

            properties.load(input);
        }

        return properties;
    }

    private static void copyToSystemPropertiesIfMissing(Properties properties) {
        for (String name : properties.stringPropertyNames()) {
            String currentValue = System.getProperty(name);

            if (currentValue == null || currentValue.isBlank()) {
                System.setProperty(name, properties.getProperty(name));
            }
        }
    }

    @Test
    public void shouldLoginWithConfiguredAuthMethod() throws Exception {
        String vaultAddress = requiredProperty("VAULT_ADDR");
        String authMethod = requiredProperty("VAULT_AUTH_METHOD");
        String authPath = optionalProperty("VAULT_AUTH_PATH", authMethod);

        VaultSettings settings = new VaultSettings(
                vaultAddress,
                null,
                authMethod,
                authPath
        );

        Vault vault = vaultClientFactory.createVaultWithAuth(settings);

        assertValidVaultLogin(vault);
    }

    @Test
    public void shouldLoginWithToken() throws Exception {
        requirePropertyOrSkip("VAULT_TOKEN");

        VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "token",
                null
        );

        Vault vault = vaultClientFactory.createVaultWithAuth(settings);

        assertValidVaultLogin(vault);
    }


    @Test
    public void shouldLoginWithCustomVaultAuthenticator() throws Exception {
        requirePropertyOrSkip("VAULT_TOKEN");

        VaultAuthenticator customAuthenticator = settings -> new DefaultVaultAuthenticator()
                .authenticate(settings);

        VaultClientFactory customFactory = new VaultClientFactory(customAuthenticator);

        VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "token",
                null
        );

        Vault vault = customFactory.createVaultWithAuth(settings);

        assertValidVaultLogin(vault);
    }

    @Test
    public void shouldLoginWithUserPass() throws Exception {
        requirePropertyOrSkip("VAULT_USERNAME");
        requirePropertyOrSkip("VAULT_PASSWORD");

        VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "userpass",
                optionalProperty("VAULT_AUTH_PATH", "userpass")
        );

        Vault vault = vaultClientFactory.createVaultWithAuth(settings);

        assertValidVaultLogin(vault);
    }

    @Test
    public void shouldLoginWithAppRole() throws Exception {
        requirePropertyOrSkip("VAULT_ROLE_ID");
        requirePropertyOrSkip("VAULT_SECRET_ID");

        VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "approle",
                optionalProperty("VAULT_APPROLE_AUTH_PATH", "approle")
        );

        Vault vault = vaultClientFactory.createVaultWithAuth(settings);

        assertValidVaultLogin(vault);
    }

    @Test
    public void shouldLoginWithJwt() throws Exception {
        requirePropertyOrSkip("VAULT_JWT_ROLE");
        requirePropertyOrSkip("VAULT_JWT");

        VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "jwt",
                optionalProperty("VAULT_JWT_AUTH_PATH", "jwt")
        );

        Vault vault = vaultClientFactory.createVaultWithAuth(settings);

        assertValidVaultLogin(vault);
    }
    
    @Test
    public void shouldLoginWithGithub() throws Exception {
        requirePropertyOrSkip("VAULT_GITHUB_TOKEN");

        VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "github",
                optionalProperty("VAULT_GITHUB_AUTH_PATH", "github")
        );

        Vault vault = vaultClientFactory.createVaultWithAuth(settings);

        assertValidVaultLogin(vault);
    }
    
    @Test
    public void shouldLoginWithLdap() throws Exception {
        requirePropertyOrSkip("VAULT_LDAP_USERNAME");
        requirePropertyOrSkip("VAULT_LDAP_PASSWORD");

        VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "ldap",
                optionalProperty("VAULT_LDAP_AUTH_PATH", "ldap")
        );

        Vault vault = vaultClientFactory.createVaultWithAuth(settings);

        assertValidVaultLogin(vault);
    }

    @Test
    public void shouldRejectUnsupportedAuthMethod() {
        VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "invalid-auth",
                null
        );

        try {
            vaultClientFactory.createVaultWithAuth(settings);
            fail("Expected IllegalArgumentException for unsupported auth method.");
        } catch (IllegalArgumentException e) {
            assertTrue(
                    e.getMessage().contains("Unsupported auth method"),
                    "Error message should mention unsupported auth method."
            );
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass().getName(), e);
        }
    }
    
    @Test
	public void canAuthenticateAndReadKv2Secret() throws Exception {
		String mount = optionalProperty("VAULT_KV_MOUNT", "secret");
		String path = optionalProperty("VAULT_SECRET_PATH", "dev/myapp");
		
		VaultSettings settings = new VaultSettings(
                optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200"),
                null,
                "token",
                null
        );

		Vault vault = vaultClientFactory.createVaultWithAuth(settings);

		VaultSecretService service = new VaultSecretService(vault);
		Map<String, String> data = service.readKv2(mount, path);

		assertNotNull(data, "Vault secret data should not be null");
		assertEquals(data.get("username"), "testuser", "Unexpected username from Vault secret");
		assertEquals(data.get("password"), "testpass", "Unexpected password from Vault secret");
	}

    private void assertValidVaultLogin(Vault vault) throws Exception {
        assertNotNull(vault, "Vault client should not be null.");

        LookupResponse response = vault.auth().lookupSelf();

        assertNotNull(response, "lookupSelf response should not be null.");
        assertNotNull(response.getPolicies(), "lookupSelf policies should not be null.");
    }

    private String requiredProperty(String name) {
        String value = get(name, null);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + name);
        }

        return value;
    }

    private void requirePropertyOrSkip(String name) {
        String value = get(name, null);

        if (value == null || value.isBlank()) {
            throw new SkipException("Skipping test because " + name + " is not set.");
        }
    }

    private String optionalProperty(String name, String defaultValue) {
        return get(name, defaultValue);
    }

    private String get(String name, String defaultValue) {
        String value = System.getProperty(name);

        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }

        if ((value == null || value.isBlank()) && vaultProperties != null) {
            value = vaultProperties.getProperty(name);
        }

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}