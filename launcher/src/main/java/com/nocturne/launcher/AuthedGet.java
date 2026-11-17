package com.nocturne.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Extra DownloadUtil entry point for authenticated GET requests. */
final class AuthedGet {
	private AuthedGet() {
	}

	static String get(String url, String bearer) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + bearer)
				.header("User-Agent", "Nocturne-Launcher/1.0")
				.GET().build();
			HttpResponse<String> response = DownloadUtil.HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException("GET " + url + " -> HTTP " + response.statusCode() + ": " + response.body());
			}
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}
}
