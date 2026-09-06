package com.apextechlabs.wakeremote;

import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;

import java.security.KeyStore;
import java.util.Locale;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

final class WakeKeyStore {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "apex_wake_hmac_v1";

    boolean hasKey() throws Exception {
        return open().containsAlias(ALIAS);
    }

    SecretKey getKey() throws Exception {
        java.security.Key key = open().getKey(ALIAS, null);
        if (!(key instanceof SecretKey)) throw new IllegalStateException("Secure wake key is unavailable");
        return (SecretKey) key;
    }

    void importHexKey(String input) throws Exception {
        byte[] raw = decodeKey(input);
        try {
            SecretKey key = new SecretKeySpec(raw, "HmacSHA256");
            KeyProtection protection = new KeyProtection.Builder(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
            ).setDigests(KeyProperties.DIGEST_SHA256).build();
            open().setEntry(ALIAS, new KeyStore.SecretKeyEntry(key), protection);
        } finally {
            java.util.Arrays.fill(raw, (byte) 0);
        }
    }

    void deleteKey() throws Exception {
        KeyStore store = open();
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
    }

    static byte[] decodeKey(String input) {
        String value = input == null ? "" : input.trim().toLowerCase(Locale.US);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Enter the 64-character hexadecimal key from the server setup.");
        }
        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private KeyStore open() throws Exception {
        KeyStore store = KeyStore.getInstance(STORE);
        store.load(null);
        return store;
    }
}
