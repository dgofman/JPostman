package io.github.jpostman.vault;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.response.LogicalResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Service for reading and writing Vault key/value secrets with an authenticated
 * Vault client.
 *
 * <p>
 * This class intentionally stays separate from authentication.
 * {@link VaultClientFactory} creates the authenticated client; this service
 * uses that client to access secrets.
 * </p>
 */
public class VaultSecretService {

	private final Vault vault;

	/**
	 * Creates a secret service using an authenticated Vault client.
	 *
	 * @param vault authenticated Vault client
	 */
	public VaultSecretService(Vault vault) {
		this.vault = Objects.requireNonNull(vault, "vault");
	}

	/**
	 * Reads a Vault logical path.
	 *
	 * @param path Vault logical path, for example "secret/dev/myapp"
	 * @return secret data
	 * @throws Exception if the path cannot be read
	 */
	public Map<String, String> read(String path) throws Exception {
		LogicalResponse response = vault.logical().read(path);
		return response.getData();
	}

	/**
	 * Reads a secret from a KV mount.
	 *
	 * @param mount      KV mount name, for example "secret"
	 * @param secretPath secret path under the mount, for example "dev/myapp"
	 * @return secret data
	 * @throws Exception if the path cannot be read
	 */
	public Map<String, String> readKv2(String mount, String secretPath) throws Exception {
		return read(join(mount, secretPath));
	}

	/**
	 * Reads one required value from a Vault secret.
	 *
	 * @param path Vault logical path
	 * @param key  key to read
	 * @return value for the requested key
	 * @throws Exception if the path cannot be read
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
	 * @param path   Vault logical path, for example "secret/dev/myapp"
	 * @param values key/value pairs to write
	 * @throws Exception if the path cannot be written
	 */
	public void write(String path, Map<String, String> values) throws Exception {
		Map<String, Object> objectValues = new HashMap<>();
		objectValues.putAll(values);

		vault.logical().write(path, objectValues);
	}

	/**
	 * Writes a secret to a KV mount.
	 *
	 * @param mount      KV mount name, for example "secret"
	 * @param secretPath secret path under the mount, for example "dev/myapp"
	 * @param values     key/value pairs to write
	 * @throws Exception if the path cannot be written
	 */
	public void writeKv2(String mount, String secretPath, Map<String, String> values) throws Exception {
		write(join(mount, secretPath), values);
	}

	/**
	 * Deletes a Vault logical path.
	 *
	 * @param path Vault logical path, for example "secret/dev/myapp"
	 * @throws Exception if the path cannot be deleted
	 */
	public void delete(String path) throws Exception {
		vault.logical().delete(path);
	}

	/**
	 * Deletes a secret from a KV mount.
	 *
	 * @param mount      KV mount name, for example "secret"
	 * @param secretPath secret path under the mount, for example "dev/myapp"
	 * @throws Exception if the path cannot be deleted
	 */
	public void deleteKv2(String mount, String secretPath) throws Exception {
		delete(join(mount, secretPath));
	}

	/**
	 * Deletes one key from a KV secret.
	 *
	 * If the key is the last value, the whole secret path is deleted.
	 *
	 * @param mount      KV mount name, for example "secret"
	 * @param secretPath secret path under the mount, for example "dev/myapp"
	 * @param key        key to remove
	 * @throws Exception if the secret cannot be read, written, or deleted
	 */
	public void deleteKv2Key(String mount, String secretPath, String key) throws Exception {
		Map<String, String> data = readKv2(mount, secretPath);
		if (data == null || !data.containsKey(key)) {
			return;
		}
		data.remove(key);
		writeKv2(mount, secretPath, data);
	}

	/**
	 * Reads a Vault secret that contains a shell script value and extracts exported
	 * variables.
	 *
	 * Example script value: export KEY1=VALUE1 export KEY2=VALUE2
	 *
	 * @param mount      KV mount name, for example "secret"
	 * @param secretPath secret path under the mount, for example "dev/myapp"
	 * @return exported shell variables as key/value pairs
	 * @throws Exception if the secret cannot be read
	 */
	public Map<String, String> readShellValues(String mount, String secretPath) throws Exception {
		Map<String, String> data = readKv2(mount, secretPath);
		if (data == null || data.isEmpty()) {
			return new HashMap<>();
		}
		String script = data.get("script");
		if (script == null || script.trim().isEmpty()) {
			return new HashMap<>();
		}
		return parseShellExports(script);
	}

	/**
	 * Parses simple shell export lines.
	 */
	private static Map<String, String> parseShellExports(String script) {
		Map<String, String> values = new HashMap<>();
		String[] lines = script.split("\\R");
		for (String line : lines) {
			String trimmedLine = line.trim();
			if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
				continue;
			}
			if (!trimmedLine.startsWith("export ")) {
				continue;
			}
			String assignment = trimmedLine.substring("export ".length()).trim();
			int equalsIndex = assignment.indexOf('=');
			if (equalsIndex <= 0) {
				continue;
			}
			String key = assignment.substring(0, equalsIndex).trim();
			String value = assignment.substring(equalsIndex + 1).trim();
			values.put(key, value);
		}
		return values;
	}

	/**
	 * Joins two Vault path fragments using one slash.
	 */
	private static String join(String left, String right) {
		String a = trimSlashes(left);
		String b = trimSlashes(right);
		return a + "/" + b;
	}

	/**
	 * Removes leading and trailing slashes.
	 */
	private static String trimSlashes(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("^/+", "").replaceAll("/+$", "");
	}
}
