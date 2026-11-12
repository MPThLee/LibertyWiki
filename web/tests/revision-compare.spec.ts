// SPDX-License-Identifier: AGPL-3.0-only
import { expect, test } from '@playwright/test'

test('compares selected history revisions across pages safely and in the chosen order', async ({ page }) => {
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/by-title/Test')) return route.fulfill({ json: { id: 'p', currentRevisionId: '2', normalizedTitle: 'Test', source: 'new' } })
    if (url.pathname.endsWith('/pages/p/revisions')) return route.fulfill({ json: url.searchParams.has('cursor')
      ? { revisions: [{ id: '1', parentRevisionId: null, createdAt: '2026-01-01', summary: 'old' }], nextCursor: null }
      : { revisions: [{ id: '2', parentRevisionId: '1', createdAt: '2026-01-02', summary: 'new' }], nextCursor: 'next' } })
    if (url.pathname.includes('/compare/')) {
      const [, , , , from, , to] = url.pathname.split('/')
      if (from === '2' && to === '1') return route.fulfill({ status: 422, json: { code: 'COMPARISON_TOO_LARGE' } })
      const meta = (id: string) => ({ id, pageId: 'p', title: 'Test', timestamp: '2026-01-01' })
      return route.fulfill({ json: { from: meta(from), to: meta(to), lines: from === to ? [] : [
        { type: 'changed', fromLine: 1, toLine: 1, from: [{ text: '<script>old</script>', changed: true }], to: [{ text: 'new', changed: true }] },
        { type: 'added', fromLine: null, toLine: 2, from: [], to: [{ text: 'added', changed: true }] },
      ] } })
    }
    return route.fulfill({ status: 404, json: {} })
  })
  await page.goto('/')
  await page.getByLabel('조회할 제목').fill('Test')
  await page.getByRole('button', { name: '원문 조회' }).click()
  await page.getByRole('button', { name: '문서 이력 보기' }).click()
  await page.getByLabel('끝 2', { exact: true }).check()
  await page.getByRole('button', { name: '이력 더 보기' }).click()
  await page.getByLabel('시작 1', { exact: true }).check()
  await page.getByRole('button', { name: '선택한 리비전 비교' }).click()
  await expect(page.getByLabel('Wikitext 차이')).toContainText('<script>old</script>')
  await expect(page.locator('.comparison script')).toHaveCount(0)
  await expect(page.locator('.diff-added')).toContainText('added')
  await page.getByLabel('시작 2', { exact: true }).check()
  await page.getByRole('button', { name: '선택한 리비전 비교' }).click()
  await expect(page.getByText('차이가 없습니다.')).toBeVisible()
  await page.getByLabel('끝 1', { exact: true }).check()
  await page.getByRole('button', { name: '선택한 리비전 비교' }).click()
  await expect(page.getByRole('alert')).toContainText('허용된 크기')
})
