import { defineConfig, devices } from '@playwright/test'

export default defineConfig({ testDir: './e2e', fullyParallel: true, reporter: 'list', use: { baseURL: 'http://127.0.0.1:5180', trace: 'retain-on-failure', ...devices['Desktop Chrome'] }, webServer: { command: 'node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5180 --strictPort', url: 'http://127.0.0.1:5180', reuseExistingServer: false, timeout: 30_000, gracefulShutdown: { signal: 'SIGTERM', timeout: 5_000 } } })
