package com.nocturne.launcher;

import java.io.IOException;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Ely.by account authentication (Yggdrasil-compatible) plus offline profiles. */
public final class ElybyAuth {
	private ElybyAuth() {
	}

	public static class Session {
		public String name = "";
		public String uuid = "";
		public String accessToken = "";
		public String clientToken = "";
	}

	public static Session login(String username, String password) throws IOException {
		String clientToken = UUID.randomUUID().toString().replace("-", "");
		JsonObject agent = new JsonObject();
		agent.addProperty("name", "Minecraft");
		agent.addProperty("version", 1);
		JsonObject body = new JsonObject();
		body.addProperty("username", username);
		body.addProperty("password", password);
		body.addProperty("clientToken", clientToken);
		body.addProperty("requestUser", false);
		body.add("agent", agent);
		String json = DownloadUtil.postJson("https://authserver.ely.by/authenticate", body.toString());
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		if (!obj.has("accessToken")) {
			throw new IOException("ely.by login failed: " + json);
		}
		JsonObject profile = obj.getAsJsonObject("selectedProfile");
		Session session = new Session();
		session.name = profile.get("name").getAsString();
		session.uuid = profile.get("id").getAsString();
		session.accessToken = obj.get("accessToken").getAsString();
		session.clientToken = clientToken;
		return session;
	}

	/** Offline profile: local play only, cannot join online-mode servers. */
	public static Session offline(String name) {
		String clean = name == null ? "" : name.trim();
		if (clean.isEmpty() || clean.length() > 16) {
			throw new IllegalArgumentException("Name must be 1-16 characters.");
		}
		Session session = new Session();
		session.name = clean;
		session.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + clean).getBytes(java.nio.charset.StandardCharsets.UTF_8))
			.toString().replace("-", "");
		session.accessToken = "0";
		session.clientToken = "0";
		return session;
	}
}
