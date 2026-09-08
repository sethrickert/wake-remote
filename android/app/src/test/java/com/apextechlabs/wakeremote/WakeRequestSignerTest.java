package com.apextechlabs.wakeremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.spec.SecretKeySpec;

public final class WakeRequestSignerTest {
    /**
     * Reads the authoritative fixture rather than transcribing it. Transcribed copies
     * silently drift from shared/test-vectors.json, which is exactly what this test exists
     * to prevent.
     */
    private static String vector(String field) throws Exception {
        File file = new File("../../shared/test-vectors.json").getCanonicalFile();
        for (File dir = new File(".").getCanonicalFile(); !file.exists() && dir != null; dir = dir.getParentFile()) {
            file = new File(dir, "shared/test-vectors.json");
        }
        assertNotNull("shared/test-vectors.json not found", file);
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!matcher.find()) throw new IllegalStateException("missing fixture field: " + field);
        return matcher.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    @Test
    public void requestMatchesServerContract() throws Exception {
        byte[] key = WakeKeyStore.decodeKey(vector("key_hex"));
        String nonceHex = vector("nonce");
        byte[] nonce = new byte[nonceHex.length() / 2];
        for (int i = 0; i < nonce.length; i++) {
            nonce[i] = (byte) Integer.parseInt(nonceHex.substring(i * 2, i * 2 + 2), 16);
        }

        WakeRequestSigner.SignedRequest request = WakeRequestSigner.create(
            new SecretKeySpec(key, "HmacSHA256"), vector("target"), 1_757_030_400L, nonce
        );

        assertEquals(vector("body"), new String(request.body, StandardCharsets.UTF_8));
        assertEquals("1757030400", request.timestamp);
        assertEquals(nonceHex, request.nonce);
        // Asserts the whole canonical string, so the signed path itself is covered.
        assertEquals(vector("canonical"), request.canonical);
        assertEquals(5, request.canonical.split("\\n", -1).length);
        assertFalse(request.canonical.endsWith("\n"));
        assertEquals(vector("signature"), request.signature);
    }

    @Test
    public void signedPathIsTheApiPath() throws Exception {
        assertEquals(vector("path"), WakeRequestSigner.PATH);
        assertEquals("/api/v1/wake", WakeRequestSigner.PATH);
    }

    @Test
    public void responseMappingIsFriendlyAndRateLimitIsBounded() {
        assertEquals(WakeApiClient.Result.Kind.SUCCESS, WakeApiClient.forHttpStatus(204, null).kind);
        assertEquals(WakeApiClient.Result.Kind.ERROR, WakeApiClient.forHttpStatus(401, null).kind);
        WakeApiClient.Result limited = WakeApiClient.forHttpStatus(429, "9999");
        assertEquals(WakeApiClient.Result.Kind.RATE_LIMITED, limited.kind);
        assertEquals(300, limited.retryAfterSeconds);
        assertTrue(WakeApiClient.forHttpStatus(503, null).message.contains("WireGuard"));
    }
}
