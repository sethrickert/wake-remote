package com.apextechlabs.wakeremote;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

final class WakeRequestSigner {
    static final String METHOD = "POST";
    static final String API_PREFIX = "/api";
    /** Signed into the HMAC canonical string. Must stay identical to the server and every other client. */
    static final String PATH = API_PREFIX + "/v1/wake";
    static final String ENROLL_PATH = API_PREFIX + "/v1/enroll";
    private static final SecureRandom RANDOM = new SecureRandom();

    static SignedRequest create(SecretKey key, String target, long timestampSeconds) throws Exception {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        return create(key, target, timestampSeconds, nonce);
    }

    static SignedRequest create(SecretKey key, String target, long timestampSeconds, byte[] nonce) throws Exception {
        if (nonce == null || nonce.length < 16) throw new IllegalArgumentException("Nonce must be at least 16 bytes");
        byte[] body = ("{\"target\":\"" + target + "\"}").getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(timestampSeconds);
        String nonceHex = hex(nonce);
        String bodyHash = hex(MessageDigest.getInstance("SHA-256").digest(body));
        String canonical = METHOD + "\n" + PATH + "\n" + timestamp + "\n" + nonceHex + "\n" + bodyHash;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        String signature = hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        return new SignedRequest(timestamp, nonceHex, signature, canonical, body);
    }

    static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = alphabet[value >>> 4];
            result[i * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
    }

    static final class SignedRequest {
        final String timestamp;
        final String nonce;
        final String signature;
        final String canonical;
        final byte[] body;

        SignedRequest(String timestamp, String nonce, String signature, String canonical, byte[] body) {
            this.timestamp = timestamp;
            this.nonce = nonce;
            this.signature = signature;
            this.canonical = canonical;
            this.body = body;
        }
    }
}
