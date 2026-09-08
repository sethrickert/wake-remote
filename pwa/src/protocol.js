const encoder=new TextEncoder();
export const API_PREFIX='/api';
// Signed into the HMAC canonical string. Must stay identical to the server and every other client.
export const WAKE_PATH=`${API_PREFIX}/v1/wake`;
export const ENROLL_PATH=`${API_PREFIX}/v1/enroll`;
export const hex=bytes=>[...new Uint8Array(bytes)].map(v=>v.toString(16).padStart(2,'0')).join('');
export const fromHex=value=>new Uint8Array(value.match(/../g).map(v=>parseInt(v,16)));
export const validateKey=value=>/^[0-9a-fA-F]{64}$/.test(value.trim());
export async function importKey(value){return crypto.subtle.importKey('raw',fromHex(value.trim()),{name:'HMAC',hash:'SHA-256'},false,['sign']);}
export async function signRequest(key,target,timestamp=Math.floor(Date.now()/1000),nonceBytes=crypto.getRandomValues(new Uint8Array(16))){
  const body=JSON.stringify({target}); const nonce=hex(nonceBytes); const bodyHash=hex(await crypto.subtle.digest('SHA-256',encoder.encode(body)));
  const canonical=['POST',WAKE_PATH,String(timestamp),nonce,bodyHash].join('\n');
  const signature=hex(await crypto.subtle.sign('HMAC',key,encoder.encode(canonical)));
  return {body,canonical,headers:{'Content-Type':'application/json','X-Key-Id':'main','X-Timestamp':String(timestamp),'X-Nonce':nonce,'X-Signature':signature}};
}
export function parseEnrollmentUri(value,allowHttp=false){const url=new URL(value);if(url.protocol!=='wakeremote:'||url.hostname!=='enroll'||url.searchParams.get('v')!=='1')throw new Error('This is not a valid Wake Remote enrollment link.');const server=new URL(url.searchParams.get('url'));if(server.protocol!=='https:'&&!(allowHttp&&server.protocol==='http:'))throw new Error('Enrollment requires HTTPS unless LAN HTTP is explicitly enabled.');const token=url.searchParams.get('t');if(!token)throw new Error('The enrollment token is missing.');return {serverUrl:server.origin,token};}
