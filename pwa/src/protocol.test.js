import {describe, expect, it} from 'vitest';
import fixture from '../../shared/test-vectors.json';
import {WAKE_PATH, importKey, signRequest, validateServerUrl} from './protocol';

const v = fixture.vectors[0];
const bytes = hex => new Uint8Array(hex.match(/../g).map(x => parseInt(x, 16)));

describe('protocol fixture', () => {
  it('matches the reference signature', async () => {
    const key = await importKey(v.key_hex);
    const r = await signRequest(key, v.target, {timestamp: v.timestamp, nonceBytes: bytes(v.nonce)});
    expect(r.body).toBe(v.body);
    expect(r.canonical).toBe(v.canonical);
    expect(r.headers['X-Signature']).toBe(v.signature);
  });

  it('signs the /api path', () => {
    expect(WAKE_PATH).toBe(v.path);
    expect(WAKE_PATH).toBe('/api/v1/wake');
  });

  it('sends the enrolled key id rather than a hardcoded one', async () => {
    const key = await importKey(v.key_hex);
    const r = await signRequest(key, v.target, {keyId: 'workshop'});
    expect(r.headers['X-Key-Id']).toBe('workshop');
  });
});

describe('server url validation', () => {
  it('accepts https and returns the origin', () => {
    expect(validateServerUrl('https://wol.example.com/ignored')).toBe('https://wol.example.com');
  });

  it('rejects a plaintext origin unless LAN HTTP is opted into', () => {
    expect(() => validateServerUrl('http://wol.example.com')).toThrow();
    expect(validateServerUrl('http://192.168.1.10:8080', true)).toBe('http://192.168.1.10:8080');
  });
});
