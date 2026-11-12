// SPDX-License-Identifier: AGPL-3.0-only
import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

test('edits a page and preserves the draft through an edit conflict', async ({ page, request }) => {
  const token = process.env.WIKI_BROWSER_TOKEN
  if (!token) throw new Error('Set WIKI_BROWSER_TOKEN to the server operator token.')
  const title = `Browser edit ${randomUUID()}`
  const headers = { Authorization: `Bearer ${token}` }
  const created = await request.post('/api/v1/pages', {
    headers, data: { title, namespaceId: 0, source: 'original' },
  })
  expect(created.status()).toBe(201)
  const initial = await created.json()

  await page.goto('/')
  await page.getByLabel('운영자 토큰').fill(token)
  await page.getByLabel('조회할 제목').fill(title)
  await page.getByRole('button', { name: '원문 조회' }).click()
  const draft = page.getByLabel('편집할 Wikitext 원문')
  const summary = page.getByLabel('편집 요약', { exact: true })
  const unicodeSummary = '📝'.repeat(500)
  await expect(draft).toHaveValue('original')
  await draft.fill('my draft')
  await summary.fill(unicodeSummary)
  await expect(summary).toHaveValue(unicodeSummary)

  const competing = await request.post(`/api/v1/pages/${initial.id}/revisions`, {
    headers, data: { baseRevisionId: initial.currentRevisionId, source: 'other edit' },
  })
  expect(competing.status()).toBe(201)
  await page.getByRole('button', { name: '편집 저장' }).click()
  await expect(page.getByRole('alert')).toContainText('다른 편집이 먼저 저장되었습니다')
  await expect(draft).toHaveValue('my draft')
  await expect(summary).toHaveValue(unicodeSummary)
  await expect(page.getByRole('button', { name: '편집 저장' })).toBeDisabled()
  await page.getByRole('button', { name: '최신 원문 확인' }).click()
  await expect(page.getByLabel('최신 Wikitext 원문')).toHaveText('other edit')
  await expect(draft).toHaveValue('my draft')
  await page.getByRole('button', { name: '최신 리비전을 저장 기준으로 선택' }).click()
  await expect(draft).toHaveValue('my draft')
  await page.getByRole('button', { name: '편집 저장' }).click()
  await expect(page.getByRole('status')).toHaveText('편집을 저장했습니다.')
  await expect(page.getByLabel('현재 Wikitext 원문')).toHaveText('my draft')
})
