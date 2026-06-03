package io.github.jpostman.vault;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.response.LogicalResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Service for reading and writing Vault key/value secrets with an authenticated Vault client.
 *
 * <p>This class intentionally stays separate from authentication. {@link VaultClientFactory}
 * creates the authenticated client; this service uses that client to access secrets.</p>
 */
public class VaultSecretService {

    private final Vault vault;

    /**
     * Creates a secret service using an authenticated Vault client.
     *
     * @param vault authenticated Vault client
     * @throws NullPointerException if {@code vault} is {@code null}
     */
    public VaultSecretService(Vault vault) {
        this.vault = Objects.requireNonNull(vault, "vault");
    }

    /**
     * Reads a Vault secret path and returns its key/value data.
     *
     * <p>For the JOpenLibs Vault driver used here, the configured KV engine version is handled by
     * the driver. For a KV v2 mount named {@code secret}, callers can use a logical path such as
     * {@code secret/dev/myapp}.</p>
     *
     * @param path Vault logical secret path
     * @return secret data as string key/value pairs
     * @throws Exception if Vault returns an error or the path cannot be read
     */
    public Map<String, String> read(String path) throws Exception {
        LogicalResponse response = vault.logical().read(path);
        return response.getData();
    }

    /**
     * Reads a secret from a KV v2 mount using separate mount and secret path values.
     *
     * <p>Example: {@code readKv2("secret", "dev/myapp")} reads {@code secret/dev/myapp}.</p>
     *
     * @param mount KV mount name, for example {@code secret}
     * @param secretPath secret path under the mount, for example {@code dev/myapp}
     * @return secret data as string key/value pairs
     * @throws Exception if Vault returns an error or the path cannot be read
     */
    public Map<String, String> readKv2(String mount, String secretPath) throws Exception {
        return read(join(mount, secretPath));
    }

    /**
     * Reads one required value from a Vault secret.
     *
     * @param path Vault logical secret path
     * @param key key to read from the secret data
     * @return value for the requested key
     * @throws Exception if Vault returns an error or the path cannot be read
     * @throws IllegalArgumentException if the requested key is missing from the secret data
     */
    public String readRequiredValue(String path, String key) throws Exception {
        Map<String, String> data = read(path);
        String value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Vault secret key not found. path=" + path + ", key=" + key);
        }
        return value;
    }

    /**
     * Writes key/value pairs to a Vault logical path.
     *
     * <p>This is mainly useful for local tests and setup utilities. Production application code
     * usually reads secrets rather than writing them.</p>
     *
     * @param path Vault logical path to write
     * @param values key/value pairs to write
     * @throws Exception if Vault returns an error or the path cannot be written
     */
    public void write(String path, Map<String, String> values) throws Exception {
        Map<String, Object> objectValues = new HashMap<>();
        objectValues.putAll(values);
        vault.logical().write(path, objectValues);
    }

    /**
     * Joins two Vault path fragments using exactly one slash.
     *
     * @param left first path fragment
     * @param right second path fragment
     * @return joined path
     */
    private static String join(String left, String right) {
        String a = trimSlashes(left);
        String b = trimSlashes(right);
        return a + "/" + b;
    }

    /**
     * Removes leading and trailing slashes from a path fragment.
     *
     * @param value path fragment to normalize
     * @return normalized path fragment, or an empty string when {@code value} is {@code null}
     */
    private static String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
