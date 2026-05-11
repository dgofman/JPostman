package io.jpostman;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Loads a Postman Collection v2.1 JSON export and provides helpers to inspect
 * folders, requests, and an optionally loaded environment.
 */
public class Collection {

	private static final Logger log = LoggerFactory.getLogger(Collection.class);

	private final JsonObject root;
	private String name;
	private final List<Folder> folders = new ArrayList<>();
	private final List<Request> rootRequests = new ArrayList<>();
	private Environment environment;

	private Collection(JsonObject root) {
		this.root = root;
	}

	// -------------------------------------------------------------------------
	// Loading
	// -------------------------------------------------------------------------

	/**
	 * Load a collection from a file path. Opens and closes the file internally.
	 *
	 * @param filePath absolute or relative path to the *.postman_collection.json
	 *                 file
	 */
	public static Collection load(String filePath) throws IOException {
		return load(new FileInputStream(filePath));
	}

	/**
	 * Load a collection from an already-open {@link Reader}. The caller retains
	 * ownership and is responsible for closing the reader.
	 *
	 * @param reader an open reader positioned at the start of the JSON
	 */
	public static Collection load(InputStream is) throws IOException {
		try (Reader reader = new InputStreamReader(is)) {
			return load(JsonParser.parseReader(reader).getAsJsonObject());
		}
	}
	
	public static Collection load(JsonObject root) throws IOException {
		Collection col = new Collection(root);
		if (root.has("info")) {
			JsonObject info = root.getAsJsonObject("info");
			col.name = info.has("name") ? info.get("name").getAsString() : "Unnamed Collection";
		}

		if (root.has("item")) {
			parseItems(root.getAsJsonArray("item"), col, null);
		}
		return col;
	}

	/**
	 * Load and attach a Postman environment file to this collection.
	 *
	 * @param filePath path to the *.postman_environment.json file
	 */
	public void loadEnvironment(String filePath) throws IOException {
		this.environment = Environment.load(filePath);
	}

	/**
	 * Load and attach a Postman environment file to this collection.
	 *
	 * @param reader an open reader positioned at the start of the JSON
	 */
	public void loadEnvironment(InputStream is) throws IOException {
		this.environment = Environment.load(is);
	}

	// -------------------------------------------------------------------------
	// Parsing helpers
	// -------------------------------------------------------------------------

	private static void parseItems(JsonArray items, Collection col, Folder parentFolder) {
		for (JsonElement element : items) {
			JsonObject item = element.getAsJsonObject();
			String itemName = item.has("name") ? item.get("name").getAsString() : "Unnamed";

			if (item.has("item")) {
				// Item array present → this is a folder (may be nested)
				Folder folder = new Folder(itemName);
				col.folders.add(folder);
				parseItems(item.getAsJsonArray("item"), col, folder);
			} else if (item.has("request")) {
				// Leaf item → this is a request
				JsonObject reqObj = item.getAsJsonObject("request");
				String folder = parentFolder != null ? parentFolder.getName() : "(root)";
				Request request = Request.from(itemName, folder, reqObj);

				if (parentFolder != null) {
					parentFolder.addRequest(request);
				} else {
					col.rootRequests.add(request);
				}
			}
		}
	}

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	public JsonObject getRoot() {
		return root;
	}

	public String getName() {
		return name;
	}

	public List<Folder> getFolders() {
		return folders;
	}

	public Folder getFolder(String folderName) {
		for (Folder folder : folders) {
			if (folder.getName().equalsIgnoreCase(folderName)) {
				return folder;
			}
		}
		return null;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public List<Request> getRequests() {
		return rootRequests;
	}

	public Request getRequest(String requestName) {
		for (Request request : rootRequests) {
			if (request.getName().equalsIgnoreCase(requestName)) {
				return request;
			}
		}
		return null;
	}

	// -------------------------------------------------------------------------
	// Print functions
	// -------------------------------------------------------------------------

	/** Prints every folder and its request count. */
	public void print() {
		log.info(String.format("=== Collection: %s (%d request%s) ===", name, rootRequests.size(), rootRequests.size() == 1 ? "" : "s"));
		if (folders.isEmpty()) {
			log.trace("  (no folders)");
		} else {
			log.trace(String.format("  (%d folder%s)", folders.size(), folders.size() == 1 ? "" : "s"));
			log.trace(this.toString());
		}
	}

	@Override
	public String toString() {
		return folders.stream().map(e -> String.format("  %s\n", e.getName())).collect(Collectors.joining());

	}
}
