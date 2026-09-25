import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
// import.meta.dirname keeps this working under Vite's native config loader.

export default defineConfig({
  plugins: [react(), tailwindcss()],

  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname,
    },
  },

  server: {
    port: 5173,
    proxy: {
      // The API is same-origin in development. This keeps CORS out of the dev loop
      // entirely and means the production build can be served from any origin without
      // the backend needing to know about it.
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          // Drop the browser's Origin header before forwarding.
          //
          // Browsers send `Origin` on every non-GET request, INCLUDING same-origin
          // ones. `changeOrigin` only rewrites Host, so that header arrived at
          // Spring untouched and its CORS filter judged the request cross-origin.
          // Open the app on 127.0.0.1 or a LAN IP rather than localhost and every
          // POST came back `403 Invalid CORS request` - as plain text, so the
          // client could not even read a code off it and surfaced "UNKNOWN".
          //
          // Through this proxy the request genuinely is same-origin, so there is
          // no origin to police. Removing the header makes that true on the wire
          // and keeps CORS out of the dev loop, which is what the comment above
          // always claimed.
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
          });
        },
      },
    },
  },

  build: {
    // The cinematic layer lands in its own chunk later. Keeping the practical routes
    // in a small eager bundle is the whole point of the boundary, so the warning
    // threshold is set low enough to notice if that ever stops being true.
    chunkSizeWarningLimit: 300,
  },
});
