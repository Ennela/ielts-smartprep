import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/__tests__/setup.js'],
    // Page-level tests (lazy routes, react-query, a dozen mocked modules) transform
    // slowly when the whole suite runs in parallel; 5 s tripped on a busy machine.
    testTimeout: 15000,
  },
});
