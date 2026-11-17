package com.nocturne.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Small HTTP + file download helper shared by the launcher services. */
public final class DownloadUtil {
	public static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private DownloadUtil() {
	}

	public static String getJson(String url) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("Accept", "application/json")
				.header("User-Agent", "Nocturne-Launcher/1.0")
				.GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException("GET " + url + " -> HTTP " + response.statusCode());
			}
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}

	public static String postJson(String url, String body) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("User-Agent", "Nocturne-Launcher/1.0")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException("POST " + url + " -> HTTP " + response.statusCode() + ": " + response.body());
			}
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}

	public static String postForm(String url, String body) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(60))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("User-Agent", "Nocturne-Launcher/1.0")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException("POST " + url + " -> HTTP " + response.statusCode() + ": " + response.body());
			}
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}

	/** Downloads a URL to a file, skipping when the file already exists with the expected size. */
	public static void download(String url, Path target, long expectedSize) throws IOException {
		if (Files.isRegularFile(target) && expectedSize > 0) {
			try {
				if (Files.size(target) == expectedSize) {
					return;
				}
			} catch (IOException ignored) {
			}
		}
		Files.createDirectories(target.getParent());
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofMinutes(10))
				.header("User-Agent", "Nocturne-Launcher/1.0")
				.GET().build();
			HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException("GET " + url + " -> HTTP " + response.statusCode());
			}
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
				byte[] buffer = new byte[65536];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			}
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}
}
