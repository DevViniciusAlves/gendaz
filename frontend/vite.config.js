import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { sentryVitePlugin } from '@sentry/vite-plugin'

const sentryPlugins = []

if (process.env.SENTRY_AUTH_TOKEN) {
  sentryPlugins.push(
    sentryVitePlugin({
      org: 'gendaz',
      project: 'gendaz',
      authToken: process.env.SENTRY_AUTH_TOKEN,
      sourcemaps: {
        assets: './dist/**',
      },
    })
  )
}

export default defineConfig({
  plugins: [
    react(),
    ...sentryPlugins,
    {
      name: 'add-security-headers',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          res.setHeader('X-Frame-Options', 'DENY');
          res.setHeader('X-Content-Type-Options', 'nosniff');
          res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
          next();
        });
      }
    }
  ],
  build: {
    sourcemap: true,
  },
})
