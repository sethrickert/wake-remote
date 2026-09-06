package com.apextechlabs.wakeremote;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.crypto.SecretKey;
import javax.net.ssl.HttpsURLConnection;

final class WakeApiClient {
    Result send(SecretKey key, String serverUrl, String keyId, String target) {
        HttpURLConnection connection = null;
        try {
            WakeRequestSigner.SignedRequest signed = WakeRequestSigner.create(key, target, System.currentTimeMillis() / 1000L);
            connection = (HttpURLConnection) new URL(serverUrl + "/v1/wake").openConnection();
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(signed.body.length);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Key-Id", keyId);
            connection.setRequestProperty("X-Timestamp", signed.timestamp);
            connection.setRequestProperty("X-Nonce", signed.nonce);
            connection.setRequestProperty("X-Signature", signed.signature);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(signed.body);
            }
            return forHttpStatus(connection.getResponseCode(), connection.getHeaderField("Retry-After"));
        } catch (UnknownHostException exception) {
            return Result.network("No internet connection or the service address could not be found.");
        } catch (SocketTimeoutException exception) {
            return Result.network("The secure service did not respond in time. Try again shortly.");
        } catch (IOException exception) {
            return Result.network("The secure service could not be reached. Check your connection and try again.");
        } catch (Exception exception) {
            return Result.error("The secure request could not be signed on this device.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static Result forHttpStatus(int status, String retryAfter) {
        switch (status) {
            case HttpURLConnection.HTTP_NO_CONTENT:
                return Result.success();
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                return Result.error("Authentication failed. Re-enter the key and check that automatic date and time are enabled.");
            case HttpURLConnection.HTTP_NOT_FOUND:
                return Result.error("The wake target is not configured on the service.");
            case 429:
                return Result.rateLimited(parseRetryAfter(retryAfter));
            case HttpURLConnection.HTTP_UNAVAILABLE:
                return Result.error("The service could not reach your home network. Check the WireGuard tunnel.");
            default:
                return Result.error("The secure service returned an unexpected response (" + status + ").");
        }
    }

    private static int parseRetryAfter(String value) {
        if (value == null) return 60;
        try {
            int seconds = Integer.parseInt(value.trim());
            return Math.max(1, Math.min(seconds, 300));
        } catch (NumberFormatException ignored) {
            return 60;
        }
    }

    static final class Result {
        enum Kind { SUCCESS, ERROR, NETWORK, RATE_LIMITED }

        final Kind kind;
        final String message;
        final int retryAfterSeconds;

        private Result(Kind kind, String message, int retryAfterSeconds) {
            this.kind = kind;
            this.message = message;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        static Result success() {
            return new Result(Kind.SUCCESS, "Wake signal sent. Your PC may take a moment to come online.", 0);
        }

        static Result error(String message) {
            return new Result(Kind.ERROR, message, 0);
        }

        static Result network(String message) {
            return new Result(Kind.NETWORK, message, 0);
        }

        static Result rateLimited(int seconds) {
            return new Result(Kind.RATE_LIMITED, "Too many wake attempts. Please wait before trying again.", seconds);
        }
    }
}
