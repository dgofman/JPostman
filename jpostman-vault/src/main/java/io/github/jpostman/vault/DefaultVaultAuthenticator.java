package io.github.jpostman.vault;

import java.io.File;

import io.github.jopenlibs.vault.SslConfig;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.response.AuthResponse;

/**
 * Default {@link VaultAuthenticator} implementation used by
 * {@link VaultClientFactory}.
 *
 * <p>
 * This authenticator supports token, userpass, AppRole, LDAP, GitHub, and JWT.
 * Each auth method is implemented in a protected method so users can extend
 * this class and override only the behavior they need.
 * </p>
 */
public class DefaultVaultAuthenticator implements VaultAuthenticator {

	/**
	 * Authenticates with Vault using the configured auth method.
	 *
	 * @param settings Vault connection and authentication settings
	 * @return authenticated Vault client
	 * @throws Exception if authentication fails or configuration is invalid
	 */
	@Override
	public Vault authenticate(VaultSettings settings) throws Exception {
		String method = settings.authMethod();
		String mount = settings.authPath();

		if (method == null || method.isBlank()) {
			throw new IllegalArgumentException("Vault auth method cannot be null or blank.");
		}

		if (mount == null || mount.isBlank()) {
			mount = defaultMountFor(method);
		}

		VaultConfig config = createBaseConfig(settings).build();
		Vault vault = Vault.create(config);

		switch (method.toLowerCase()) {
		case "token":
			return authenticateWithToken(settings);
		case "userpass":
			return authenticateWithUserPass(settings, vault, mount);
		case "approle":
			return authenticateWithAppRole(settings, vault, mount);
		case "ldap":
			return authenticateWithLdap(settings, vault, mount);
		case "github":
			return authenticateWithGithub(settings, vault, mount);
		case "jwt":
			return authenticateWithJwt(settings, vault, mount);
		default:
			return authenticateUnsupported(settings, method, mount);
		}
	}

	/**
	 * Authenticates using an existing Vault token.
	 *
	 * <p>
	 * Override this method to customize token-based authentication.
	 * </p>
	 *
	 * @param settings Vault settings
	 * @return authenticated Vault client
	 * @throws Exception if the token is missing or the client cannot be created
	 */
	protected Vault authenticateWithToken(VaultSettings settings) throws Exception {
		return createVaultWithToken(settings, settings.required("VAULT_TOKEN"));
	}

	/**
	 * Authenticates using Userpass auth.
	 *
	 * <p>
	 * Override this method to customize username/password auth behavior.
	 * </p>
	 *
	 * @param settings Vault settings
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithUserPass(VaultSettings settings, Vault vault, String mount) throws Exception {
		AuthResponse response = vault.auth().loginByUserPass(settings.required("VAULT_USERNAME"),
				settings.required("VAULT_PASSWORD"), mount);
		return createVaultWithToken(settings, response.getAuthClientToken());
	}

	/**
	 * Authenticates using AppRole auth.
	 *
	 * <p>
	 * Override this method to customize AppRole login behavior.
	 * </p>
	 *
	 * @param settings Vault settings
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithAppRole(VaultSettings settings, Vault vault, String mount) throws Exception {
		AuthResponse response = vault.auth().loginByAppRole(mount, settings.required("VAULT_ROLE_ID"),
				settings.required("VAULT_SECRET_ID"));
		return createVaultWithToken(settings, response.getAuthClientToken());
	}

	/**
	 * Authenticates using LDAP auth.
	 *
	 * <p>
	 * Override this method to customize LDAP login behavior.
	 * </p>
	 *
	 * @param settings Vault settings
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithLdap(VaultSettings settings, Vault vault, String mount) throws Exception {
		AuthResponse response = vault.auth().loginByLDAP(settings.required("VAULT_LDAP_USERNAME"),
				settings.required("VAULT_LDAP_PASSWORD"), mount);
		return createVaultWithToken(settings, response.getAuthClientToken());
	}

	/**
	 * Authenticates using GitHub auth.
	 *
	 * <p>
	 * Override this method to customize GitHub login behavior.
	 * </p>
	 *
	 * @param settings Vault settings
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithGithub(VaultSettings settings, Vault vault, String mount) throws Exception {
		AuthResponse response = vault.auth().loginByGithub(settings.required("VAULT_GITHUB_TOKEN"), mount);
		return createVaultWithToken(settings, response.getAuthClientToken());
	}

	/**
	 * Authenticates using JWT auth.
	 *
	 * <p>
	 * Override this method to customize JWT login behavior.
	 * </p>
	 *
	 * @param settings Vault settings
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithJwt(VaultSettings settings, Vault vault, String mount) throws Exception {
		AuthResponse response = vault.auth().loginByJwt(mount, settings.required("VAULT_JWT_ROLE"),
				settings.required("VAULT_JWT"));
		return createVaultWithToken(settings, response.getAuthClientToken());
	}

	/**
	 * Handles unsupported authentication methods.
	 *
	 * <p>
	 * Override this method if you want to support custom auth method names without
	 * replacing the full authenticator.
	 * </p>
	 *
	 * @param settings Vault settings
	 * @param method   unsupported method name
	 * @param mount    auth mount path
	 * @return authenticated Vault client
	 * @throws Exception always by default
	 */
	protected Vault authenticateUnsupported(VaultSettings settings, String method, String mount) throws Exception {
		throw new IllegalArgumentException("Unsupported auth method: " + method);
	}

	/**
	 * Creates a Vault client configured with a token.
	 *
	 * <p>
	 * This method is protected so subclasses can reuse it after custom login logic.
	 * </p>
	 *
	 * @param settings Vault connection settings
	 * @param token    Vault client token
	 * @return authenticated Vault client
	 * @throws Exception if token is blank or client cannot be created
	 */
	protected Vault createVaultWithToken(VaultSettings settings, String token) throws Exception {
		if (token == null || token.isBlank()) {
			throw new IllegalStateException("Vault login did not return a client token.");
		}
		VaultConfig config = createBaseConfig(settings).token(token).build();
		return Vault.create(config);
	}

	/**
	 * Creates the base Vault configuration.
	 *
	 * <p>
	 * This method is protected so subclasses can customize address, namespace, SSL,
	 * proxy, or other driver-level configuration.
	 * </p>
	 *
	 * @param settings Vault settings
	 * @return Vault configuration builder
	 * @throws Exception if SSL configuration cannot be created
	 */
	protected VaultConfig createBaseConfig(VaultSettings settings) throws Exception {
		VaultConfig config = new VaultConfig().address(settings.address()).sslConfig(sslConfig(settings));
		if (settings.namespace() != null && !settings.namespace().isBlank()) {
			config.nameSpace(settings.namespace());
		}
		return config;
	}

	/**
	 * Creates SSL configuration for the Vault driver.
	 *
	 * @param settings Vault settings
	 * @return SSL configuration
	 * @throws Exception if SSL configuration cannot be built
	 */
	protected SslConfig sslConfig(VaultSettings settings) throws Exception {
		boolean verify = Boolean.parseBoolean(settings.optional("VAULT_SSL_VERIFY", "false"));
		String pemFile = settings.optional("VAULT_SSL_PEM_FILE", null);
		SslConfig sslConfig = new SslConfig().verify(verify);
		if (verify && pemFile != null && !pemFile.isBlank()) {
			sslConfig.pemFile(new File(pemFile));
		}
		return sslConfig.build();
	}

	/**
	 * Returns the default auth mount path for a known auth method.
	 *
	 * @param method authentication method name
	 * @return default mount path
	 */
	protected String defaultMountFor(String method) {
		switch (method.toLowerCase()) {
		case "userpass":
			return "userpass";
		case "approle":
			return "approle";
		case "ldap":
			return "ldap";
		case "github":
			return "github";
		case "jwt":
			return "jwt";
		case "token":
			return "token";
		default:
			return method;
		}
	}
}