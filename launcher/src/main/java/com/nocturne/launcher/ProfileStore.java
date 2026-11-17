package com.nocturne.launcher;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Launcher accounts + settings, stored as JSON in the user's home folder. */
public final class ProfileStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static class Account {
		public String type = "offline"; // microsoft | elyby | offline
		public String name = "";
		public String uuid = "";
		public String accessToken = "";
		public String refreshToken = "";
		public String clientToken = "";
	}

	public static class Data {
		public List<Account> accounts = new ArrayList<>();
		public String selectedAccount = "";
		public String mcVersion = "26.3";
		public String loaderVersion = "";
		public int ramMb = 4096;
		public String gameDir = "";
		public String azureClientId = "00000000402B5328";
	}

	private ProfileStore() {
	}

	public static Path dataDir() {
		String home = System.getProperty("user.home", ".");
		return Paths.get(home, ".nocturne-launcher");
	}

	public static Path dataFile() {
		return dataDir().resolve("launcher.json");
	}

	public static Data load() {
		Data data = new Data();
		try {
			Files.createDirectories(dataDir());
			if (Files.isRegularFile(dataFile())) {
				try (Reader reader = Files.newBufferedReader(dataFile(), StandardCharsets.UTF_8)) {
					Data loaded = GSON.fromJson(reader, Data.class);
					if (loaded != null) {
						return loaded;
					}
				}
			}
		} catch (Exception ignored) {
		}
		if (data.gameDir == null || data.gameDir.isEmpty()) {
			data.gameDir = dataDir().resolve("game").toString();
		}
		if (data.azureClientId == null || data.azureClientId.isEmpty()) {
			data.azureClientId = "00000000402B5328";
		}
		return data;
	}

	public static void save(Data data) {
		try {
			Files.createDirectories(dataDir());
			try (Writer writer = Files.newBufferedWriter(dataFile(), StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException ignored) {
		}
	}

	public static Account find(Data data, String name) {
		if (data == null || name == null) {
			return null;
		}
		for (Account account : data.accounts) {
			if (name.equals(account.name)) {
				return account;
			}
		}
		return null;
	}
}
