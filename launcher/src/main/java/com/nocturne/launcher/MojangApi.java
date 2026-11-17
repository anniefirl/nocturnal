package com.nocturne.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Reads Mojang's version manifest and per-version details. */
public final class MojangApi {
	public static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

	private MojangApi() {
	}

	public static class Entry {
		public String id = "";
		public String type = "";
		public String url = "";
	}

	public static List<Entry> releases() throws IOException {
		List<Entry> out = new ArrayList<>();
		JsonObject manifest = JsonParser.parseString(DownloadUtil.getJson(MANIFEST)).getAsJsonObject();
		JsonArray versions = manifest.getAsJsonArray("versions");
		for (int i = 0; i < versions.size(); i++) {
			JsonObject version = versions.get(i).getAsJsonObject();
			String type = version.has("type") ? version.get("type").getAsString() : "";
			if (!"release".equals(type)) {
				continue;
			}
			Entry entry = new Entry();
			entry.id = version.get("id").getAsString();
			entry.type = type;
			entry.url = version.get("url").getAsString();
			out.add(entry);
		}
		return out;
	}

	public static JsonObject versionDetails(String url) throws IOException {
		return JsonParser.parseString(DownloadUtil.getJson(url)).getAsJsonObject();
	}
}
