package io.github.jpostman.vault;

import io.github.jopenlibs.vault.SslConfig;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.response.AuthResponse;

import java.io.File;

/**
 * Default {@link VaultAuthenticator} implementation used by {@link VaultClientFactory}.
 *
 * <p>This authenticator supports the local/test authentication methods used by JPostman Vault:
 * token, userpass, AppRole, LDAP, GitHub, and JWT. It also supports optional SSL configuration
 * through {@code VAULT_SSL_VERIFY} and {@code VAULT_SSL_PEM_FILE}.</p>
 */
public class DefaultVaultAuthenticator implements VaultAuthenticator {

    /**
     * Authenticates with Vault using the method configured in {@link VaultSettings#authMethod()}.
     *
     * <p>Required credential names depend on the selected auth method:</p>
     * <ul>
     *     <li>{@code token}: {@code VAULT_TOKEN}</li>
     *     <li>{@code userpass}: {@code VAULT_USERNAME}, {@code VAULT_PASSWORD}</li>
     *     <li>{@code approle}: {@code VAULT_ROLE_ID}, {@code VAULT_SECRET_ID}</li>
     *     <li>{@code ldap}: {@code VAULT_LDAP_USERNAME}, {@code VAULT_LDAP_PASSWORD}</li>
     *     <li>{@code github}: {@code VAULT_GITHUB_TOKEN}</li>
     *     <li>{@code jwt}: {@code VAULT_JWT_ROLE}, {@code VAULT_JWT}</li>
     * </ul>
     *
     * @param settings Vault connection and authentication settings
     * @return authenticated Vault client
     * @throws Exception if authentication fails, a required setting is missing, or the Vault client cannot be created
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

        if ("token".equalsIgnoreCase(method)) {
            return createVaultWithToken(settings, settings.required("VAULT_TOKEN"));
        }

        VaultConfig config = createBaseConfig(settings).build();
        Vault vault = Vault.create(config);

        AuthResponse response;

        switch (method.toLowerCase()) {
            case "userpass":
                response = vault.auth().loginByUserPass(
                        settings.required("VAULT_USERNAME"),
                        settings.required("VAULT_PASSWORD"),
                        mount
                );
                return createVaultWithToken(settings, response.getAuthClientToken());

            case "approle":
                response = vault.auth().loginByAppRole(
                        mount,
                        settings.required("VAULT_ROLE_ID"),
                        settings.required("VAULT_SECRET_ID")
                );
                return createVaultWithToken(settings, response.getAuthClientToken());

            case "ldap":
                response = vault.auth().loginByLDAP(
                        settings.required("VAULT_LDAP_USERNAME"),
                        settings.required("VAULT_LDAP_PASSWORD"),
                        mount
                );
                return createVaultWithToken(settings, response.getAuthClientToken());

            case "github":
                response = vault.auth().loginByGithub(
                        settings.required("VAULT_GITHUB_TOKEN"),
                        mount
                );
                return createVaultWithToken(settings, response.getAuthClientToken());

            case "jwt":
                response = vault.auth().loginByJwt(
                        mount,
                        settings.required("VAULT_JWT_ROLE"),
                        settings.required("VAULT_JWT")
                );
                return createVaultWithToken(settings, response.getAuthClientToken());

            default:
                throw new IllegalArgumentException("Unsupported auth method: " + method);
        }
    }

    /**
     * Creates a Vault client configured with an already obtained Vault token.
     *
     * @param settings Vault connection settings
     * @param token Vault client token returned by an auth method or supplied directly
     * @return authenticated Vault client
     * @throws Exception if the token is blank or the Vault client cannot be created
     */
    private Vault createVaultWithToken(VaultSettings settings, String token) throws Exception {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Vault login did not return a client token.");
        }

        VaultConfig config = createBaseConfig(settings)
                .token(token)
                .build();

        return Vault.create(config);
    }

    /**
     * Creates the base Vault configuration shared by unauthenticated and authenticated clients.
     *
     * @param settings Vault connection settings
     * @return base Vault configuration builder
     * @throws Exception if SSL configuration cannot be created
     */
    private VaultConfig createBaseConfig(VaultSettings settings) throws Exception {
        VaultConfig config = new VaultConfig()
                .address(settings.address())
                .sslConfig(sslConfig(settings));

        if (settings.namespace() != null && !settings.namespace().isBlank()) {
            config.nameSpace(settings.namespace());
        }

        return config;
    }

    /**
     * Creates SSL configuration for the Vault driver.
     *
     * <p>{@code VAULT_SSL_VERIFY} controls certificate verification and defaults to {@code false}
     * for local Docker testing. {@code VAULT_SSL_PEM_FILE} is used only when verification is enabled
     * and points to a PEM CA certificate file.</p>
     *
     * @param settings Vault settings used to resolve SSL properties
     * @return built SSL configuration
     * @throws Exception if the PEM file cannot be read or the SSL configuration cannot be built
     */
    private SslConfig sslConfig(VaultSettings settings) throws Exception {
        boolean verify = Boolean.parseBoolean(
                settings.optional("VAULT_SSL_VERIFY", "false")
        );

        String pemFile = settings.optional("VAULT_SSL_PEM_FILE", null);

        SslConfig sslConfig = new SslConfig()
                .verify(verify);

        if (verify && pemFile != null && !pemFile.isBlank()) {
            sslConfig.pemFile(new File(pemFile));
        }

        return sslConfig.build();
    }

    /**
     * Returns the default auth mount path for a known auth method.
     *
     * @param method authentication method name
     * @return default mount path for the method, or the original method value for unknown methods
     */
    private String defaultMountFor(String method) {
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
