package com.nocturne.launcher;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Resolves Fabric loader + intermediary versions from meta.fabricmc.net. */
public final class FabricApi {
	private FabricApi() {
	}

	public static class Resolved {
		public String loaderVersion = "";
		public String intermediaryVersion = "";
	}

	public static Resolved resolve(String mcVersion, String preferredLoader) throws IOException {
		String json = DownloadUtil.getJson("https://meta.fabricmc.net/v2/versions/loader/" + mcVersion);
		JsonArray array = JsonParser.parseString(json).getAsJsonArray();
		if (array.size() == 0) {
			throw new IOException("No Fabric loader for Minecraft " + mcVersion);
		}
		Resolved resolved = new Resolved();
		JsonObject first = array.get(0).getAsJsonObject();
		String latestStable = null;
		for (int i = 0; i < array.size(); i++) {
			JsonObject row = array.get(i).getAsJsonObject();
			JsonObject loader = row.getAsJsonObject("loader");
			boolean stable = !loader.has("stable") || loader.get("stable").getAsBoolean();
			if (stable) {
				latestStable = loader.get("version").getAsString();
				if (resolved.intermediaryVersion.isEmpty()) {
					resolved.intermediaryVersion = row.getAsJsonObject("intermediary").get("version").getAsString();
				}
				break;
			}
		}
		if (latestStable == null) {
			latestStable = first.getAsJsonObject("loader").get("version").getAsString();
			resolved.intermediaryVersion = first.getAsJsonObject("intermediary").get("version").getAsString();
		}
		resolved.loaderVersion = (preferredLoader == null || preferredLoader.trim().isEmpty()) ? latestStable : preferredLoader.trim();
		return resolved;
	}

	public static String loaderJarUrl(String loaderVersion) {
		return "https://maven.fabricmc.net/net/fabricmc/fabric-loader/" + loaderVersion
			+ "/fabric-loader-" + loaderVersion + ".jar";
	}

	public static String intermediaryJarUrl(String intermediaryVersion) {
		return "https://maven.fabricmc.net/net/fabricmc/intermediary/" + intermediaryVersion
			+ "/intermediary-" + intermediaryVersion + ".jar";
	}

	public static String fabricApiJarUrl(String fabricApiVersion) {
		return "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/" + fabricApiVersion
			+ "/fabric-api-" + fabricApiVersion + ".jar";
	}
}
