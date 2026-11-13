import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => {
  // `npm run dev` forwards /api to the Spring Boot jar; override with API_PROXY=http://host:port.
  const apiTarget = loadEnv(mode, '.', '').API_PROXY || 'http://127.0.0.1:8080';
  const proxy = { '/api': { target: apiTarget, changeOrigin: false } };
  return {
    plugins: [react(), tailwindcss()],
    server: { port: 5173, host: true, proxy },
    preview: { proxy },
  };
});
