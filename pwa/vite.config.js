import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import {VitePWA} from 'vite-plugin-pwa';

// No `base`: the app is served from the origin root by the Wake Remote server, and the
// API lives under /api on that same origin.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // icon.webp is the logo the UI actually renders. It was previously omitted here
      // while the unreferenced 359KB icon.png was precached instead, so installed users
      // saw a broken image offline.
      includeAssets: ['icon.webp', 'apex-shield.webp', 'favicon.ico', 'apple-touch-icon.png'],
      workbox: {
        // Never let the SW answer an API call from cache.
        navigateFallbackDenylist: [/^\/api\//],
      },
      manifest: {
        // A stable id keeps installs attached to the app if start_url ever changes.
        id: '/',
        name: 'Wake Remote',
        short_name: 'Wake Remote',
        description: 'Securely send Wake-on-LAN signals through your server.',
        theme_color: '#0C0C0C',
        background_color: '#0C0C0C',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          {src: 'icon-192.png', sizes: '192x192', type: 'image/png'},
          {src: 'icon-512.png', sizes: '512x512', type: 'image/png'},
          // Without a maskable icon Android letterboxes the mark inside a grey squircle.
          {src: 'icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable'},
        ],
      },
    }),
  ],
});
