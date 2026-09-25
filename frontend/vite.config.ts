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
