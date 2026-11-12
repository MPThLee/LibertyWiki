// SPDX-License-Identifier: AGPL-3.0-only
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests',
  use: {
    baseURL: process.env.WIKI_BROWSER_URL ?? 'http://127.0.0.1:8080',
    channel: process.env.WIKI_BROWSER_CHANNEL,
  },
})
