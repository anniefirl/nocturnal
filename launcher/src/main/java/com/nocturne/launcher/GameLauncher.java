package com.nocturne.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Resolves a Minecraft version, downloads everything it needs, adds Fabric
 * and the Nocturne mod, then starts the game. All downloads are cached, so
 * repeat launches are fast and fully offline-capable once complete.
 */
public final class GameLauncher {
	public interface Log {
		void line(String text);
	}

	private GameLauncher() {
	}

	public static Process launch(ProfileStore.Data settings, ProfileStore.Account account, Log log) throws Exception {
		if (account == null) {
			throw new IOException("No account selected.");
		}
		Path gameDir = Paths.get(settings.gameDir);
		Files.createDirectories(gameDir);
		Path libsDir = gameDir.resolve("libraries");
		Path versionsDir = gameDir.resolve("versions");
		Path assetsDir = gameDir.resolve("assets");
		Path nativesDir = gameDir.resolve("natives").resolve(settings.mcVersion);
		Path modsDir = gameDir.resolve("mods");
		Files.createDirectories(libsDir);
		Files.createDirectories(versionsDir);
		Files.createDirectories(assetsDir);
		Files.createDirectories(nativesDir);
		Files.createDirectories(modsDir);

		// 1. Version details.
		log.line("Resolving Minecraft " + settings.mcVersion + "...");
		String versionUrl = null;
		for (MojangApi.Entry entry : MojangApi.releases()) {
			if (entry.id.equals(settings.mcVersion)) {
				versionUrl = entry.url;
				break;
			}
		}
		if (versionUrl == null) {
			throw new IOException("Minecraft version " + settings.mcVersion + " not found in the version manifest.");
		}
		JsonObject version = MojangApi.versionDetails(versionUrl);
		JsonObject downloads = version.getAsJsonObject("downloads");

		// 2. Client jar.
		JsonObject clientDl = downloads.getAsJsonObject("client");
		Path clientJar = versionsDir.resolve(settings.mcVersion + ".jar");
		log.line("Client jar...");
		DownloadUtil.download(clientDl.get("url").getAsString(), clientJar, sizeOf(clientDl));

		// 3. Libraries (+ Windows natives).
		log.line("Libraries...");
		List<Path> classpath = new ArrayList<>();
		classpath.add(clientJar);
		JsonArray libraries = version.getAsJsonArray("libraries");
		for (int i = 0; i < libraries.size(); i++) {
			JsonObject lib = libraries.get(i).getAsJsonObject();
			if (!rulesAllow(lib)) {
				continue;
			}
			JsonObject libDownloads = lib.getAsJsonObject("downloads");
			if (libDownloads.has("artifact")) {
				JsonObject artifact = libDownloads.getAsJsonObject("artifact");
				Path target = libsDir.resolve(artifact.get("path").getAsString());
				DownloadUtil.download(artifact.get("url").getAsString(), target, sizeOf(artifact));
				classpath.add(target);
			}
			if (libDownloads.has("classifiers")) {
				JsonObject classifiers = libDownloads.getAsJsonObject("classifiers");
				String key = pickNativesKey(classifiers);
				if (key != null) {
					JsonObject nativeArtifact = classifiers.getAsJsonObject(key);
					Path target = libsDir.resolve(nativeArtifact.get("path").getAsString());
					DownloadUtil.download(nativeArtifact.get("url").getAsString(), target, sizeOf(nativeArtifact));
					extractNatives(target, nativesDir);
				}
			}
		}

		// 4. Assets.
		JsonObject assetIndex = version.getAsJsonObject("assetIndex");
		String assetId = assetIndex.get("id").getAsString();
		Path indexFile = assetsDir.resolve("indexes").resolve(assetId + ".json");
		log.line("Assets (" + assetId + ")...");
		DownloadUtil.download(assetIndex.get("url").getAsString(), indexFile, sizeOf(assetIndex));
		JsonObject index = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject();
		JsonObject objects = index.getAsJsonObject("objects");
		int assetCount = 0;
		for (String name : objects.keySet()) {
			String hash = objects.getAsJsonObject(name).get("hash").getAsString();
			Path target = assetsDir.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
			if (!Files.isRegularFile(target)) {
				DownloadUtil.download("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash,
					target, objects.getAsJsonObject(name).has("size") ? objects.getAsJsonObject(name).get("size").getAsLong() : 0);
				assetCount++;
				if (assetCount % 200 == 0) {
					log.line("  " + assetCount + " asset files...");
				}
			}
		}

		// 5. Logging config.
		Path loggingFile = null;
		try {
			JsonObject logging = version.getAsJsonObject("logging").getAsJsonObject("client")
				.getAsJsonObject("file");
			loggingFile = gameDir.resolve("logging").resolve("client.xml");
			DownloadUtil.download(logging.get("url").getAsString(), loggingFile, sizeOf(logging));
		} catch (Exception ignored) {
		}

		// 6. Fabric loader + intermediary.
		log.line("Fabric loader...");
		FabricApi.Resolved fabric = FabricApi.resolve(settings.mcVersion, settings.loaderVersion);
		if (settings.loaderVersion == null || settings.loaderVersion.trim().isEmpty()) {
			settings.loaderVersion = fabric.loaderVersion;
			ProfileStore.save(settings);
		}
		Path loaderJar = libsDir.resolve("fabric-loader-" + fabric.loaderVersion + ".jar");
		Path intermediaryJar = libsDir.resolve("intermediary-" + fabric.intermediaryVersion + ".jar");
		DownloadUtil.download(FabricApi.loaderJarUrl(fabric.loaderVersion), loaderJar, 0);
		DownloadUtil.download(FabricApi.intermediaryJarUrl(fabric.intermediaryVersion), intermediaryJar, 0);
		classpath.add(loaderJar);
		classpath.add(intermediaryJar);

		// 7. Fabric API into mods (so the client works out of the box).
		log.line("Fabric API...");
		String fabricApiVersion = resolveFabricApiVersion(settings.mcVersion);
		Path fabricApiJar = modsDir.resolve("fabric-api-" + fabricApiVersion + ".jar");
		if (!Files.isRegularFile(fabricApiJar)) {
			DownloadUtil.download(FabricApi.fabricApiJarUrl(fabricApiVersion), fabricApiJar, 0);
		}

		// 8. Nocturne mod jar (from the latest GitHub release, or a manual copy).
		Path nocturneJar = modsDir.resolve("nocturne.jar");
		if (!Files.isRegularFile(nocturneJar)) {
			String latest = latestReleaseModUrl();
			if (latest != null) {
				log.line("Downloading Nocturne mod...");
				DownloadUtil.download(latest, nocturneJar, 0);
			} else {
				log.line("WARNING: no Nocturne mod jar found - copy it to " + modsDir + " as nocturne.jar");
			}
		}

		// 9. Auth refresh for Microsoft accounts.
		if ("microsoft".equals(account.type) && account.refreshToken != null && !account.refreshToken.isEmpty()) {
			try {
				log.line("Refreshing Microsoft session...");
				MicrosoftAuth.Session session = MicrosoftAuth.refresh(settings.azureClientId, account.refreshToken);
				account.name = session.name;
				account.uuid = session.uuid;
				account.accessToken = session.accessToken;
				account.refreshToken = session.refreshToken;
			} catch (Exception e) {
				log.line("Session refresh failed (" + e.getMessage() + "), trying stored token.");
			}
		}

		// 10. Build the java command.
		String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin"
			+ java.io.File.separator + "java.exe";
		if (!Files.isRegularFile(Paths.get(javaBin))) {
			javaBin = System.getProperty("java.home") + java.io.File.separator + "bin"
				+ java.io.File.separator + "java";
		}
		List<String> command = new ArrayList<>();
		command.add(javaBin);
		command.add("-Xmx" + Math.max(1024, settings.ramMb) + "M");
		command.add("-Xms512M");
		command.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
		if (loggingFile != null && Files.isRegularFile(loggingFile)) {
			command.add("-Dlog4j.configurationFile=" + loggingFile.toAbsolutePath());
		}
		// Ely.by needs its auth library on the classpath via a javaagent.
		if ("elyby".equals(account.type)) {
			Path injector = gameDir.resolve("injector").resolve("authlib-injector.jar");
			if (!Files.isRegularFile(injector)) {
				String injectorUrl = latestInjectorUrl();
				if (injectorUrl != null) {
					log.line("Downloading authlib-injector for Ely.by...");
					DownloadUtil.download(injectorUrl, injector, 0);
				}
			}
			if (Files.isRegularFile(injector)) {
				command.add("-javaagent:" + injector.toAbsolutePath() + "=authserver.ely.by");
			} else {
				log.line("WARNING: authlib-injector missing, Ely.by online servers may reject the session.");
			}
		}
		StringBuilder cp = new StringBuilder();
		for (Path entry : classpath) {
			if (cp.length() > 0) {
				cp.append(java.io.File.pathSeparator);
			}
			cp.append(entry.toAbsolutePath());
		}
		command.add("-cp");
		command.add(cp.toString());
		command.add("net.fabricmc.loader.impl.launch.knot.KnotClient");
		command.add("--username");
		command.add(account.name);
		command.add("--version");
		command.add(settings.mcVersion);
		command.add("--gameDir");
		command.add(gameDir.toAbsolutePath().toString());
		command.add("--assetsDir");
		command.add(assetsDir.toAbsolutePath().toString());
		command.add("--assetIndex");
		command.add(assetId);
		command.add("--uuid");
		command.add(account.uuid == null ? "0" : account.uuid.replace("-", ""));
		command.add("--accessToken");
		command.add(account.accessToken == null ? "0" : account.accessToken);
		command.add("--userType");
		command.add("microsoft".equals(account.type) ? "msa" : ("elyby".equals(account.type) ? "mojang" : "legacy"));
		command.add("--versionType");
		command.add("release");

		log.line("Starting Minecraft...");
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(gameDir.toFile());
		Process process = builder.start();
		drainAsync(process.getInputStream(), log, "");
		drainAsync(process.getErrorStream(), log, "");
		return process;
	}

	private static void drainAsync(InputStream in, Log log, String prefix) {
		Thread thread = new Thread(() -> {
			try {
				byte[] buffer = new byte[8192];
				StringBuilder pending = new StringBuilder();
				int read;
				while ((read = in.read(buffer)) != -1) {
					pending.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
					int idx;
					while ((idx = pending.indexOf("\n")) != -1) {
						log.line(prefix + pending.substring(0, idx).trim());
						pending.delete(0, idx + 1);
					}
				}
				if (pending.length() > 0) {
					log.line(prefix + pending.toString().trim());
				}
			} catch (Exception ignored) {
			}
		}, "nocturne-game-log");
		thread.setDaemon(true);
		thread.start();
	}

	private static long sizeOf(JsonObject artifact) {
		try {
			return artifact.has("size") ? artifact.get("size").getAsLong() : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	private static boolean rulesAllow(JsonObject lib) {
		try {
			if (!lib.has("rules")) {
				return true;
			}
			boolean allowed = false;
			boolean hasRules = false;
			for (JsonElement element : lib.getAsJsonArray("rules")) {
				hasRules = true;
				JsonObject rule = element.getAsJsonObject();
				boolean matches = true;
				if (rule.has("os")) {
					JsonObject os = rule.getAsJsonObject("os");
					matches = true;
					if (os.has("name")) {
						matches = "windows".equals(os.get("name").getAsString());
					}
					if (matches && os.has("arch")) {
						String arch = System.getProperty("os.arch", "");
						matches = os.get("arch").getAsString().equals(arch)
							|| ("x86_64".equals(os.get("arch").getAsString()) && arch.contains("64"));
					}
				}
				if (matches) {
					allowed = "allow".equals(rule.get("action").getAsString());
				}
			}
			return hasRules ? allowed : true;
		} catch (Exception e) {
			return true;
		}
	}

	private static String pickNativesKey(JsonObject classifiers) {
		String fallback = null;
		for (String key : classifiers.keySet()) {
			if (key.contains("natives-windows-x86_64")) {
				return key;
			}
			if (key.contains("natives-windows") && fallback == null) {
				fallback = key;
			}
		}
		return fallback;
	}

	private static void extractNatives(Path jar, Path nativesDir) throws IOException {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (entry.isDirectory() || name.startsWith("META-INF")) {
					continue;
				}
				Path target = nativesDir.resolve(name.substring(name.lastIndexOf('/') + 1));
				if (Files.isRegularFile(target)) {
					continue;
				}
				try (InputStream in = zip.getInputStream(entry);
					OutputStream out = Files.newOutputStream(target)) {
					byte[] buffer = new byte[32768];
					int read;
					while ((read = in.read(buffer)) != -1) {
						out.write(buffer, 0, read);
					}
				}
			}
		}
	}

	private static String resolveFabricApiVersion(String mcVersion) {
		try {
			String encoded = java.net.URLEncoder.encode("[\"" + mcVersion + "\"]", java.nio.charset.StandardCharsets.UTF_8);
			String json = DownloadUtil.getJson(
				"https://api.modrinth.com/v2/project/fabric-api/version?game_versions=" + encoded + "&loaders=[\"fabric\"]&limit=1");
			JsonArray array = JsonParser.parseString(json).getAsJsonArray();
			if (array.size() > 0) {
				return array.get(0).getAsJsonObject().get("version_number").getAsString();
			}
		} catch (Exception ignored) {
		}
		if ("26.3".equals(mcVersion)) {
			return "0.165.2+26.3";
		}
		throw new RuntimeException("Could not resolve a Fabric API version for Minecraft " + mcVersion);
	}

	private static String latestReleaseModUrl() {
		try {
			String json = DownloadUtil.getJson("https://api.github.com/repos/anniefirl/nocturnal/releases/latest");
			JsonObject release = JsonParser.parseString(json).getAsJsonObject();
			JsonArray assets = release.getAsJsonArray("assets");
			for (int i = 0; i < assets.size(); i++) {
				JsonObject asset = assets.get(i).getAsJsonObject();
				String name = asset.get("name").getAsString();
				if (name.endsWith(".jar") && !name.contains("sources") && !name.contains("launcher")) {
					return asset.get("browser_download_url").getAsString();
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static String latestInjectorUrl() {
		try {
			String json = DownloadUtil.getJson("https://api.github.com/repos/yushijinhun/authlib-injector/releases/latest");
			JsonObject release = JsonParser.parseString(json).getAsJsonObject();
			String tag = release.get("tag_name").getAsString();
			return "https://github.com/yushijinhun/authlib-injector/releases/download/" + tag
				+ "/authlib-injector-" + tag.replace("v", "") + ".jar";
		} catch (Exception ignored) {
		}
		return null;
	}
}
