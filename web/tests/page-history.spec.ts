// SPDX-License-Identifier: AGPL-3.0-only
import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

test('browses history and preserves a draft across credential changes', async ({ page, request }) => {
  const token = process.env.WIKI_BROWSER_TOKEN
  if (!token) throw new Error('Set WIKI_BROWSER_TOKEN to the server operator token.')
  const headers = { Authorization: `Bearer ${token}` }
  const title = `Browser history ${randomUUID()}`
  const original = '<script>literal Wikitext</script>'
  const created = await request.post('/api/v1/pages', {
    headers, data: { title, namespaceId: 0, source: original, summary: 'First revision' },
  })
  expect(created.status()).toBe(201)
  const initial = await created.json()
  const path = `/api/v1/pages/${initial.id}/revisions`
  let head: string = initial.currentRevisionId
  for (let number = 1; number <= 20; number++) {
    const edit = await request.post(path, {
      headers, data: { baseRevisionId: head, source: `Source ${number}`, summary: `Edit ${number}` },
    })
    expect(edit.status()).toBe(201)
    head = (await edit.json()).currentRevisionId
  }

  await page.goto('/')
  await page.getByLabel('조회할 제목').fill(title)
  const lookup = page.getByRole('button', { name: '원문 조회' })
  const draft = page.getByLabel('편집할 Wikitext 원문')
  const summary = page.getByLabel('편집 요약', { exact: true })
  await lookup.click()
  await expect(draft).toHaveValue('Source 20')
  await draft.fill('My draft')
  await summary.fill('My summary')
  await page.getByRole('button', { name: '문서 이력 보기' }).click()
  const firstRevision = page.getByRole('button', { name: `리비전 ${initial.currentRevisionId} 보기` })
  await expect(firstRevision).toHaveCount(0)
  await page.getByRole('button', { name: '이력 더 보기' }).click()
  await firstRevision.click()
  await expect(page.getByLabel('이전 Wikitext 원문')).toHaveText(original)
  await expect(page.locator('pre script')).toHaveCount(0)
  await expect(draft).toHaveValue('My draft')

  await page.getByLabel('운영자 토큰').fill(token)
  await expect(page.getByLabel('이전 Wikitext 원문')).toHaveCount(0)
  await expect(firstRevision).toHaveCount(0)
  const competing = await request.post(path, {
    headers, data: { baseRevisionId: head, source: 'Competing edit' },
  })
  expect(competing.status()).toBe(201)
  await lookup.click()
  await expect(draft).toHaveValue('My draft')
  await expect(summary).toHaveValue('My summary')
  await expect(page.getByRole('button', { name: '편집 저장' })).toBeDisabled()
  await expect(page.getByLabel('최신 Wikitext 원문')).toHaveText('Competing edit')
  await page.getByRole('button', { name: '최신 리비전을 저장 기준으로 선택' }).click()
  await page.getByRole('button', { name: '편집 저장' }).click()
  await expect(page.getByLabel('현재 Wikitext 원문')).toHaveText('My draft')
})
