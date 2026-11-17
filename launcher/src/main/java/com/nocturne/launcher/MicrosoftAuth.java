package com.nocturne.launcher;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Legitimate Microsoft authentication for Minecraft: device-code flow
 * (the user approves the login in their own browser, the launcher never
 * sees their password), then Xbox Live -&gt; XSTS -&gt; Minecraft chain.
 * Offline accounts are for offline/local play only and cannot join
 * online-mode servers; nothing here bypasses server authentication.
 */
public final class MicrosoftAuth {
	private MicrosoftAuth() {
	}

	public static class DeviceCode {
		public String deviceCode = "";
		public String userCode = "";
		public String verificationUri = "";
		public int interval = 5;
	}

	public static class Session {
		public String name = "";
		public String uuid = "";
		public String accessToken = "";
		public String refreshToken = "";
	}

	public static DeviceCode startDeviceFlow(String clientId) throws IOException {
		String body = "client_id=" + enc(clientId)
			+ "&scope=" + enc("XboxLive.signin offline_access");
		String json = DownloadUtil.postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", body);
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		DeviceCode code = new DeviceCode();
		code.deviceCode = str(obj, "device_code");
		code.userCode = str(obj, "user_code");
		code.verificationUri = str(obj, "verification_uri");
		try {
			code.interval = obj.get("interval").getAsInt();
		} catch (Exception ignored) {
			code.interval = 5;
		}
		if (code.deviceCode.isEmpty()) {
			throw new IOException("device flow failed: " + json);
		}
		return code;
	}

	/** Polls until the user approves (or timeoutMs elapses). Returns null on timeout. */
	public static Session pollForApproval(String clientId, DeviceCode code, long timeoutMs) throws IOException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(Math.max(2000, code.interval * 1000L));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
			String body = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
				+ "&client_id=" + enc(clientId)
				+ "&device_code=" + enc(code.deviceCode);
			String json;
			try {
				json = DownloadUtil.postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", body);
			} catch (IOException e) {
				String message = e.getMessage();
				if (message != null && message.contains("authorization_pending")) {
					continue;
				}
				if (message != null && (message.contains("slow_down") || message.contains("authorization_declined")
					|| message.contains("expired_token") || message.contains("bad_verification_code"))) {
					throw e;
				}
				continue;
			}
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			if (!obj.has("access_token")) {
				continue;
			}
			String msToken = obj.get("access_token").getAsString();
			String refresh = obj.has("refresh_token") ? obj.get("refresh_token").getAsString() : "";
			return xboxChain(msToken, refresh);
		}
		return null;
	}

	public static Session refresh(String clientId, String refreshToken) throws IOException {
		String body = "grant_type=" + enc("refresh_token")
			+ "&client_id=" + enc(clientId)
			+ "&refresh_token=" + enc(refreshToken);
		String json = DownloadUtil.postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", body);
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		if (!obj.has("access_token")) {
			throw new IOException("refresh failed: " + json);
		}
		String msToken = obj.get("access_token").getAsString();
		String refresh = obj.has("refresh_token") ? obj.get("refresh_token").getAsString() : refreshToken;
		return xboxChain(msToken, refresh);
	}

	private static Session xboxChain(String msToken, String refreshToken) throws IOException {
		JsonObject xblProps = new JsonObject();
		xblProps.addProperty("AuthMethod", "RPS");
		xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
		xblProps.addProperty("RpsTicket", "d=" + msToken);
		JsonObject xblBody = new JsonObject();
		xblBody.add("Properties", xblProps);
		xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
		xblBody.addProperty("TokenType", "JWT");
		String xblJson = DownloadUtil.postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody.toString());
		String xblToken = JsonParser.parseString(xblJson).getAsJsonObject().get("Token").getAsString();

		JsonObject xstsProps = new JsonObject();
		xstsProps.addProperty("SandboxId", "RETAIL");
		com.google.gson.JsonArray userTokens = new com.google.gson.JsonArray();
		userTokens.add(xblToken);
		xstsProps.add("UserTokens", userTokens);
		JsonObject xstsBody = new JsonObject();
		xstsBody.add("Properties", xstsProps);
		xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		xstsBody.addProperty("TokenType", "JWT");
		String xstsJson;
		try {
			xstsJson = DownloadUtil.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody.toString());
		} catch (IOException e) {
			throw new IOException("Xbox authorization failed (no Xbox profile on this account?): " + e.getMessage());
		}
		JsonObject xsts = JsonParser.parseString(xstsJson).getAsJsonObject();
		String xstsToken = xsts.get("Token").getAsString();
		String uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0)
			.getAsJsonObject().get("uhs").getAsString();

		JsonObject mcBody = new JsonObject();
		mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
		String mcJson = DownloadUtil.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody.toString());
		String mcToken = JsonParser.parseString(mcJson).getAsJsonObject().get("access_token").getAsString();

		String profileJson = AuthedGet.get("https://api.minecraftservices.com/minecraft/profile", mcToken);
		JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
		Session session = new Session();
		session.name = profile.get("name").getAsString();
		session.uuid = profile.get("id").getAsString();
		session.accessToken = mcToken;
		session.refreshToken = refreshToken;
		return session;
	}

	private static String str(JsonObject obj, String key) {
		try {
			return obj.has(key) ? obj.get(key).getAsString() : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}
}
