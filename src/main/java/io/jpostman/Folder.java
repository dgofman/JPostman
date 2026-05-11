package io.jpostman;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Folder {

	private static final Logger log = LoggerFactory.getLogger(Folder.class);

	private final String name;
	private final List<Request> requests = new ArrayList<>();

	public Folder(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public List<Request> getRequests() {
		return requests;
	}

	public void addRequest(Request request) {
		requests.add(request);
	}

	public Request getRequest(String requestName) {
		for (Request request : requests) {
			if (request.getName().equalsIgnoreCase(requestName)) {
				return request;
			}
		}
		return null;
	}

	/**
	 * Prints all requests grouped by their parent folder. Requests at the
	 * collection root are listed under "(root)".
	 */
	public void print() {
		log.info(String.format("=== Folder: %s (%d request%s) ===", name, requests.size(), requests.size() == 1 ? "" : "s"));
		if (requests.isEmpty()) {
			log.trace("  (no requests)");
		} else {
			log.trace(this.toString());
		}
	}

	@Override
	public String toString() {
		return requests.stream().map(e -> String.format("    %s\n", e)).collect(Collectors.joining());
	}
}
