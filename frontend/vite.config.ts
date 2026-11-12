import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// В разработке API проксируется на Spring Boot, поэтому cookie сессии
// и XSRF-TOKEN остаются same-origin — как в продакшене, где SPA
// раздаётся тем же jar.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
});
