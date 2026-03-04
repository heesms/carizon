import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    hmr: { host: '127.0.0.1', port: 5173, protocol: 'ws' },
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        bypass(req) {
          // 브라우저 페이지 내비게이션(text/html)은 React가 처리
          // axios API 호출은 백엔드로 프록시
          const accept = req.headers?.accept || ''
          if (accept.includes('text/html')) {
            return '/index.html'
          }
        },
      },
    }
  },
})
