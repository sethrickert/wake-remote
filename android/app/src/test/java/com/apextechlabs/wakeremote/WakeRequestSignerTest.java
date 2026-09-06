package com.apextechlabs.wakeremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import javax.crypto.spec.SecretKeySpec;

public final class WakeRequestSignerTest {
    @Test
    public void requestMatchesServerContract() throws Exception {
        byte[] key = WakeKeyStore.decodeKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        byte[] nonce = new byte[] {0x00,0x11,0x22,0x33,0x44,0x55,0x66,0x77,(byte)0x88,(byte)0x99,(byte)0xaa,(byte)0xbb,(byte)0xcc,(byte)0xdd,(byte)0xee,(byte)0xff};
        WakeRequestSigner.SignedRequest request = WakeRequestSigner.create(
            new SecretKeySpec(key, "HmacSHA256"), "main-pc", 1_757_030_400L, nonce
        );

        assertEquals("{\"target\":\"main-pc\"}", new String(request.body, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("1757030400", request.timestamp);
        assertEquals("00112233445566778899aabbccddeeff", request.nonce);
        assertTrue(request.canonical.endsWith("2bb5e54274cbaba5aa0ae69ed8c4037278376287fa363b096fbc4662774bcb34"));
        assertEquals(5, request.canonical.split("\\n", -1).length);
        assertFalse(request.canonical.endsWith("\n"));
        assertEquals("e40a07dab22ebff23eeaa158aa9f861918ba70007c7b85ba6acf5aac48823a38", request.signature);
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
