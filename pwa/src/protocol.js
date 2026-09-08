const encoder = new TextEncoder();

export const API_PREFIX = '/api';
// Signed into the HMAC canonical string. Must stay identical to the server and every
// other client. See shared/test-vectors.json for the authoritative fixture.
export const WAKE_PATH = `${API_PREFIX}/v1/wake`;
export const ENROLL_PATH = `${API_PREFIX}/v1/enroll`;

export const hex = bytes =>
  [...new Uint8Array(bytes)].map(v => v.toString(16).padStart(2, '0')).join('');

export const fromHex = value => {
  const pairs = String(value).trim().match(/../g);
  if (!pairs) throw new Error('Expected hexadecimal characters.');
  return new Uint8Array(pairs.map(v => parseInt(v, 16)));
};

export const validateKey = value => /^[0-9a-fA-F]{64}$/.test(String(value).trim());

export async function importKey(value) {
  if (!validateKey(value)) throw new Error('Enter exactly 64 hexadecimal characters.');
  // Non-extractable: the CryptoKey can be used to sign but never read back out, and it
  // survives structured-clone into IndexedDB with that property intact.
  return crypto.subtle.importKey(
    'raw', fromHex(value), {name: 'HMAC', hash: 'SHA-256'}, false, ['sign'],
  );
}

export async function signRequest(
  key,
  target,
  {keyId = 'main', timestamp = Math.floor(Date.now() / 1000), nonceBytes = crypto.getRandomValues(new Uint8Array(16))} = {},
) {
  const body = JSON.stringify({target});
  const nonce = hex(nonceBytes);
  const bodyHash = hex(await crypto.subtle.digest('SHA-256', encoder.encode(body)));
  const canonical = ['POST', WAKE_PATH, String(timestamp), nonce, bodyHash].join('\n');
  const signature = hex(await crypto.subtle.sign('HMAC', key, encoder.encode(canonical)));

  return {
    body,
    canonical,
    headers: {
      'Content-Type': 'application/json',
      // The enrolled key id, not a hardcoded "main". A server configured with a
      // different WAKE_KEY_ID rejected every PWA wake with 401 while the native
      // clients worked, because this was pinned.
      'X-Key-Id': keyId,
      'X-Timestamp': String(timestamp),
      'X-Nonce': nonce,
      'X-Signature': signature,
    },
  };
}

export function parseEnrollmentUri(value, allowHttp = false) {
  const url = new URL(value);
  if (url.protocol !== 'wakeremote:' || url.hostname !== 'enroll' || url.searchParams.get('v') !== '1') {
    throw new Error('This is not a valid Wake Remote enrollment link.');
  }
  const server = new URL(url.searchParams.get('url'));
  if (server.protocol !== 'https:' && !(allowHttp && server.protocol === 'http:')) {
    throw new Error('Enrollment requires HTTPS unless LAN HTTP is explicitly enabled.');
  }
  const token = url.searchParams.get('t');
  if (!token) throw new Error('The enrollment token is missing.');
  // The URI carries the bare origin; the client appends the API path itself.
  return {serverUrl: server.origin, token};
}

/**
 * The enroll response supplies the origin used for every later wake, so it is
 * re-validated here rather than trusted verbatim. Without this a hostile or altered
 * enroll response could silently downgrade later signed requests to plaintext.
 */
export function validateServerUrl(value, allowHttp = false) {
  const url = new URL(value);
  if (url.protocol !== 'https:' && !(allowHttp && url.protocol === 'http:')) {
    throw new Error('The server returned a non-HTTPS address.');
  }
  return url.origin;
}
