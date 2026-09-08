const DB = 'wake-remote';
const STORE = 'settings';
const FIELDS = ['key', 'keyId', 'serverUrl', 'targets'];

const open = () => new Promise((resolve, reject) => {
  const request = indexedDB.open(DB, 1);
  request.onupgradeneeded = () => {
    const db = request.result;
    if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
  };
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
  // Another tab holding an older version open would otherwise leave this promise
  // pending forever, and the app would sit on "Setup required" with no explanation.
  request.onblocked = () => reject(new Error('Close other Wake Remote tabs and reload.'));
});

export async function saveEnrollment(data, key) {
  const db = await open();
  try {
    // One transaction for all four fields. Writing them in separate transactions meant
    // a failure part-way left a torn enrollment: a stored key with no serverUrl still
    // looks enrolled, and every wake then posts to "undefined".
    await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, 'readwrite');
      const store = tx.objectStore(STORE);
      const record = {...data, key};
      for (const name of FIELDS) store.put(record[name], name);
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

export async function loadEnrollment() {
  const db = await open();
  try {
    const values = await Promise.all(FIELDS.map(name => new Promise((resolve, reject) => {
      const request = db.transaction(STORE).objectStore(STORE).get(name);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    })));
    const record = Object.fromEntries(FIELDS.map((name, i) => [name, values[i]]));
    // Only report an enrollment when it is complete enough to actually use.
    if (!record.key || !record.serverUrl) return {};
    return record;
  } finally {
    db.close();
  }
}

export async function clearEnrollment() {
  const db = await open();
  try {
    await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, 'readwrite');
      tx.objectStore(STORE).clear();
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}
