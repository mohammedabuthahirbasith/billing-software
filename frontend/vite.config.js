import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Any request starting with /api is forwarded to the Spring Boot backend
      '/api': 'http://localhost:8080',
    },
  },
})