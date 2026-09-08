import {useCallback, useEffect, useRef, useState} from 'react';
import {createRoot} from 'react-dom/client';
import {registerSW} from 'virtual:pwa-register';

import {clearEnrollment, loadEnrollment, saveEnrollment} from './storage';
import {
  ENROLL_PATH, WAKE_PATH, importKey, parseEnrollmentUri, signRequest, validateKey, validateServerUrl,
} from './protocol';
import './styles.css';

registerSW({immediate: true});

const blank = {key: null, keyId: 'main', serverUrl: '', targets: []};

// Served same-origin from the Wake Remote server, so API calls are relative and need no
// CORS. The server keeps its CORS config for the native and cross-origin cases, which
// is what an enrollment pointing at a different origin still needs.
const api = (serverUrl, path) =>
  !serverUrl || serverUrl === window.location.origin ? path : `${serverUrl}${path}`;

const TIMEOUT_MS = 8000;

/** fetch with a deadline. Without one a black-holed connection hangs on "Sending" forever. */
async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    return await fetch(url, {...options, signal: controller.signal});
  } finally {
    clearTimeout(timer);
  }
}

function Footer() {
  return (
    <footer>
      <img src="/apex-shield.webp" alt="" width="18" height="18" /> Apex Tech Labs
    </footer>
  );
}

function Header({screen, setScreen}) {
  const onHome = screen === 'home';
  return (
    <header>
      <button className="nav" onClick={() => setScreen('home')} aria-label="Back" disabled={onHome}>
        {onHome ? '' : '‹ Back'}
      </button>
      <span>Wake Remote</span>
      <button
        className="nav"
        onClick={() => setScreen('settings')}
        aria-label="Settings"
        disabled={screen === 'settings'}
      >
        {screen === 'settings' ? '' : 'Settings'}
      </button>
    </header>
  );
}

const STATUS_TITLES = {
  sent: 'Wake signal sent',
  sending: 'Sending securely',
  error: 'Request not sent',
  ready: 'Ready',
  setup: 'Setup required',
};

function Status({state, detail}) {
  const isError = state === 'error' || state === 'setup';
  return (
    <section
      className={`status ${state}`}
      aria-live={isError ? 'assertive' : 'polite'}
      role={isError ? 'alert' : undefined}
    >
      <i aria-hidden="true" />
      <div>
        <strong>{STATUS_TITLES[state] ?? STATUS_TITLES.setup}</strong>
        <p>{detail}</p>
      </div>
    </section>
  );
}

function Scanner({onScan, onClose}) {
  const video = useRef();
  const [message, setMessage] = useState(null);
  const supported = typeof window !== 'undefined' && 'BarcodeDetector' in window;

  useEffect(() => {
    if (!supported) return undefined;
    let stream;
    let timer;
    let stopped = false;

    (async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({video: {facingMode: 'environment'}});
        if (stopped) {
          stream.getTracks().forEach(t => t.stop());
          return;
        }
        if (video.current) video.current.srcObject = stream;
        const detector = new BarcodeDetector({formats: ['qr_code']});
        timer = setInterval(async () => {
          try {
            const [code] = await detector.detect(video.current);
            if (code) onScan(code.rawValue);
          } catch {
            // A single failed frame is not worth surfacing; keep polling.
          }
        }, 300);
      } catch {
        // Camera denial used to fail silently, leaving a black rectangle and no
        // explanation at all.
        setMessage('Camera access is off, so the scanner cannot open. Paste the enrollment link instead.');
      }
    })();

    return () => {
      stopped = true;
      clearInterval(timer);
      stream?.getTracks().forEach(t => t.stop());
    };
  }, [supported, onScan]);

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div role="dialog" aria-modal="true" aria-label="Scan enrollment QR">
        <h2>Scan enrollment QR</h2>
        {!supported && <p>QR scanning is unavailable in this browser. Paste the enrollment link instead.</p>}
        {supported && !message && <video ref={video} autoPlay playsInline />}
        {message && <p>{message}</p>}
        <button className="secondary" onClick={onClose}>Cancel</button>
      </div>
    </div>
  );
}

function App() {
  const [data, setData] = useState(blank);
  const [screen, setScreen] = useState('home');
  const [status, setStatus] = useState(['setup', 'Enroll this device to continue.']);
  const [target, setTarget] = useState('');
  const [input, setInput] = useState('');
  const [manualServer, setManualServer] = useState(window.location.origin);
  const [mode, setMode] = useState('link');
  const [scan, setScan] = useState(false);
  const [allowHttp, setAllowHttp] = useState(false);
  const [reveal, setReveal] = useState(false);

  useEffect(() => {
    loadEnrollment()
      .then(d => {
        if (!d.key) return;
        setData(d);
        setTarget(d.targets?.[0]?.alias || '');
        setStatus(['ready', 'Secure key installed.']);
      })
      .catch(e => setStatus(['error', e.message || 'Stored enrollment could not be read.']));
  }, []);

  const enroll = useCallback(async (value = input) => {
    try {
      let next;
      if (mode === 'link' || String(value).startsWith('wakeremote://')) {
        const link = parseEnrollmentUri(value, allowHttp);
        const response = await fetchWithTimeout(`${link.serverUrl}${ENROLL_PATH}`, {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({token: link.token}),
        });
        if (!response.ok) {
          throw new Error(response.status === 401
            ? 'This enrollment link is expired or already used.'
            : `Enrollment failed (${response.status}).`);
        }
        const payload = await response.json();
        next = {
          keyId: payload.key_id,
          // Re-validated rather than trusted verbatim.
          serverUrl: validateServerUrl(payload.server_url, allowHttp),
          targets: payload.targets,
          key: await importKey(payload.key),
        };
      } else {
        if (!validateKey(value)) throw new Error('Enter exactly 64 hexadecimal characters.');
        next = {
          keyId: 'main',
          serverUrl: validateServerUrl(manualServer, allowHttp),
          targets: [{alias: 'main-pc', label: 'Main PC'}],
          key: await importKey(value),
        };
      }
      await saveEnrollment(
        {keyId: next.keyId, serverUrl: next.serverUrl, targets: next.targets}, next.key,
      );
      setData(next);
      setTarget(next.targets[0].alias);
      setInput('');
      setStatus(['ready', 'Enrollment complete.']);
      setScreen('home');
    } catch (e) {
      setStatus(['error', e.message]);
    }
  }, [input, mode, allowHttp, manualServer]);

  async function wake() {
    setStatus(['sending', 'Creating a fresh signed request…']);
    try {
      const signed = await signRequest(data.key, target, {keyId: data.keyId});
      const response = await fetchWithTimeout(api(data.serverUrl, WAKE_PATH), {
        method: 'POST',
        headers: signed.headers,
        body: signed.body,
      });
      const messages = {
        401: 'Authentication failed. Check the key and device clock.',
        404: 'The selected target is not configured.',
        429: 'Too many attempts. Wait a minute.',
        503: 'The server could not reach the target network.',
      };
      if (response.status === 204) {
        setStatus(['sent', 'Your computer may take a moment to come online.']);
      } else {
        setStatus(['error', messages[response.status] || `Unexpected server response (${response.status}).`]);
      }
    } catch (e) {
      setStatus(['error', e.name === 'AbortError'
        ? 'The server did not respond in time.'
        : 'Network unavailable. Check your connection and try again.']);
    }
  }

  async function remove() {
    if (!confirm('Remove this device’s secure enrollment?')) return;
    await clearEnrollment();
    setData(blank);
    setStatus(['setup', 'Enroll this device to continue.']);
    setScreen('home');
  }

  const onScan = useCallback(value => {
    setScan(false);
    setInput(value);
    setMode('link');
    enroll(value);
  }, [enroll]);

  return (
    <div className="app">
      <Header screen={screen} setScreen={setScreen} />
      <main>
        {screen === 'home' && (
          <>
            <img className="logo" src="/icon.webp" alt="" width="128" height="128" />
            <div className="copy">
              <h1>Wake your PC</h1>
              <p>Securely send a wake signal from anywhere.</p>
            </div>
            <Status state={status[0]} detail={status[1]} />
            {data.targets?.length > 1 && (
              <label>
                Target
                <select value={target} onChange={e => setTarget(e.target.value)}>
                  {data.targets.map(t => <option key={t.alias} value={t.alias}>{t.label}</option>)}
                </select>
              </label>
            )}
            <button
              className="primary"
              onClick={data.key ? wake : () => setScreen('enroll')}
              disabled={status[0] === 'sending'}
            >
              {data.key ? 'Wake computer' : 'Set up secure key'}
            </button>
          </>
        )}

        {screen === 'enroll' && (
          <>
            <img className="logo small" src="/icon.webp" alt="" width="96" height="96" />
            <div className="copy">
              <h1>Connect Wake&nbsp;Remote</h1>
              <p>Scan the single-use QR shown during server setup.</p>
            </div>
            <button className="primary" onClick={() => setScan(true)}>Scan QR code</button>
            <div className="tabs">
              <button onClick={() => setMode('link')} aria-pressed={mode === 'link'}>Enrollment link</button>
              <button onClick={() => setMode('key')} aria-pressed={mode === 'key'}>Manual key</button>
            </div>
            <label>
              {mode === 'link' ? 'Enrollment link' : '64-character HMAC key'}
              <span className="field">
                <input
                  type={reveal || mode === 'link' ? 'text' : 'password'}
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  placeholder={mode === 'link' ? 'wakeremote://enroll?...' : '64 hexadecimal characters'}
                  aria-invalid={mode === 'key' && input.length > 0 && !validateKey(input)}
                  autoComplete="off"
                  spellCheck="false"
                />
                {mode === 'key' && (
                  <button type="button" className="reveal" onClick={() => setReveal(v => !v)}>
                    {reveal ? 'Hide' : 'Show'}
                  </button>
                )}
              </span>
            </label>
            {mode === 'key' && (
              <label>
                Server URL
                <input
                  type="url"
                  value={manualServer}
                  onChange={e => setManualServer(e.target.value)}
                  placeholder="https://wol.example.com"
                />
              </label>
            )}
            <label className="check">
              <input type="checkbox" checked={allowHttp} onChange={e => setAllowHttp(e.target.checked)} />
              Allow plaintext HTTP on this LAN
            </label>
            <Status state={status[0]} detail={status[1]} />
            <button className="primary" onClick={() => enroll()}>Enroll securely</button>
          </>
        )}

        {screen === 'settings' && (
          <>
            <div className="copy settings">
              <h1>Settings</h1>
              <p>
                {data.serverUrl
                  ? <>Service: {new URL(data.serverUrl).host}<br />Target: {data.targets?.find(t => t.alias === target)?.label || target}</>
                  : 'Not enrolled'}
              </p>
            </div>
            <section className="danger">
              <h2>Remove enrollment</h2>
              <p>This browser will no longer be able to send wake requests.</p>
              <button onClick={remove}>Remove secure key</button>
            </section>
          </>
        )}
      </main>
      <Footer />
      {scan && <Scanner onScan={onScan} onClose={() => setScan(false)} />}
    </div>
  );
}

createRoot(document.getElementById('root')).render(<App />);
