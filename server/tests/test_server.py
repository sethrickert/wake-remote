import hashlib
import hmac
import io
import json
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

from wake_remote.app import ENROLL_PATH, WAKE_PATH, WakeService, make_handler
from wake_remote.config import Settings, Target


class ServerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.settings = Settings(Path(self.tmp.name), "main", bytes(range(32)), "https://wake.example", {"main-pc": Target("main-pc", "Main PC", "127.0.0.1", "00:11:22:33:44:55", 9)}, "127.0.0.1", 0, False, "", "", "", "", False, ("*",))
        self.service = WakeService(self.settings)
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(self.service))
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.httpd.server_port}"

    def tearDown(self):
        self.httpd.shutdown(); self.httpd.server_close(); self.thread.join(); self.tmp.cleanup()

    def request(self, path, body=b"", headers=None, method="POST"):
        req = urllib.request.Request(self.base + path, body if method == "POST" else None, headers or {}, method=method)
        try:
            with urllib.request.urlopen(req) as response:
                return response.status, response.read(), response.headers
        except urllib.error.HTTPError as error:
            return error.code, error.read(), error.headers

    def signed(self, target="main-pc", nonce="00112233445566778899aabbccddeeff", path=WAKE_PATH):
        body = json.dumps({"target": target}, separators=(",", ":")).encode()
        timestamp = str(int(time.time()))
        canonical = "\n".join(("POST", path, timestamp, nonce, hashlib.sha256(body).hexdigest()))
        signature = hmac.new(self.settings.secret, canonical.encode(), hashlib.sha256).hexdigest()
        return body, {"Content-Type": "application/json", "X-Key-Id": "main", "X-Timestamp": timestamp, "X-Nonce": nonce, "X-Signature": signature}

    def test_health_get_and_head(self):
        # The bare /healthz stays for the container healthcheck, reverse proxy and uptime monitors.
        for path in ("/healthz", "/api/healthz"):
            self.assertEqual(self.request(path, method="GET")[0], 200)
            self.assertEqual(self.request(path, method="HEAD")[0], 200)

    @patch.object(WakeService, "send_magic")
    def test_signature_over_the_old_path_is_rejected(self, send):
        # The API moved to /api/v1/wake and the path is part of the HMAC canonical string.
        # A client still signing the pre-migration path must fail authentication outright.
        body, headers = self.signed(nonce="b0112233445566778899aabbccddeeff", path="/v1/wake")
        self.assertEqual(self.request(WAKE_PATH, body, headers)[0], 401)
        send.assert_not_called()

    def test_old_routes_are_gone(self):
        body, headers = self.signed(nonce="b1112233445566778899aabbccddeeff")
        self.assertEqual(self.request("/v1/wake", body, headers)[0], 404)
        self.assertEqual(self.request("/v1/enroll", b"{}", {"Content-Type": "application/json"})[0], 404)

    @patch.object(WakeService, "send_magic")
    def test_wake_replay_unknown_and_rate_limit(self, send):
        body, headers = self.signed()
        self.assertEqual(self.request(WAKE_PATH, body, headers)[0], 204)
        self.assertEqual(self.request(WAKE_PATH, body, headers)[0], 401)
        body, headers = self.signed("missing", "10112233445566778899aabbccddeeff")
        self.assertEqual(self.request(WAKE_PATH, body, headers)[0], 404)
        for i in range(4):
            body, headers = self.signed(nonce=f"{i+2:032x}")
            expected = 204 if i < 3 else 429
            self.assertEqual(self.request(WAKE_PATH, body, headers)[0], expected)

    def test_single_use_enrollment(self):
        token, _ = self.service.mint_token()
        body = json.dumps({"token": token}).encode()
        status, payload, _ = self.request(ENROLL_PATH, body, {"Content-Type": "application/json"})
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(payload)["key"], bytes(range(32)).hex())
        self.assertEqual(self.request(ENROLL_PATH, body, {"Content-Type": "application/json"})[0], 401)

    def test_uppercase_signature_accepted(self):
        # Hex is case-insensitive; a client emitting uppercase hex must still authenticate.
        body, headers = self.signed(nonce="a0112233445566778899aabbccddeeff")
        headers["X-Signature"] = headers["X-Signature"].upper()
        with patch.object(WakeService, "send_magic"):
            self.assertEqual(self.request(WAKE_PATH, body, headers)[0], 204)

    def test_tokens_persist_across_service_instances(self):
        # A token minted by one process (e.g. the --enroll CLI) must be redeemable
        # by a separate server process sharing the same data directory, and minting
        # a second token must not invalidate the first.
        token_a, _ = self.service.mint_token()
        fresh = WakeService(self.settings)
        token_b, _ = fresh.mint_token()
        self.assertTrue(fresh.consume_token(token_a))
        self.assertTrue(self.service.consume_token(token_b))
        self.assertFalse(self.service.consume_token(token_a))

    @patch.object(WakeService, "send_magic", side_effect=OSError("unreachable"))
    def test_packet_failure_is_503(self, send):
        body, headers = self.signed(nonce="f0112233445566778899aabbccddeeff")
        self.assertEqual(self.request(WAKE_PATH, body, headers)[0], 503)


if __name__ == "__main__":
    unittest.main()
