import {describe, expect, it} from 'vitest';
import fixture from '../../shared/test-vectors.json';
import {WAKE_PATH, importKey, signRequest} from './protocol';

const v = fixture.vectors[0];
const bytes = hex => new Uint8Array(hex.match(/../g).map(x => parseInt(x, 16)));

describe('protocol fixture', () => {
  it('matches the reference signature', async () => {
    const key = await importKey(v.key_hex);
    const r = await signRequest(key, v.target, v.timestamp, bytes(v.nonce));
    expect(r.body).toBe(v.body);
    expect(r.canonical).toBe(v.canonical);
    expect(r.headers['X-Signature']).toBe(v.signature);
  });

  it('signs the /api path', () => {
    expect(WAKE_PATH).toBe(v.path);
    expect(WAKE_PATH).toBe('/api/v1/wake');
  });
});
