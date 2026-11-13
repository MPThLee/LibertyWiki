// SPDX-License-Identifier: AGPL-3.0-only
import { expect, test } from '@playwright/test'

const revision = (id: string) => ({ id, pageId: 'p', title: 'Test', timestamp: '2026-01-01T00:00:00Z' })

test('uses the exact conflict revision only after explicit selection, even if the head changes again', async ({ page }) => {
  const bases: string[] = []
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    if (request.method() === 'GET') return route.fulfill({ json: { id: 'p', currentRevisionId: 'base', normalizedTitle: 'Test', source: 'original' } })
    const body = request.postDataJSON()
    bases.push(body.baseRevisionId)
    return route.fulfill({ status: 409, json: {
      code: 'EDIT_CONFLICT', currentRevisionId: bases.length === 1 ? 'conflict' : 'later',
      comparison: { from: revision(body.baseRevisionId), to: revision(bases.length === 1 ? 'conflict' : 'later'), lines: [
        { type: 'changed', fromLine: 1, toLine: 1, from: [{ text: 'original', changed: true }], to: [{ text: bases.length === 1 ? 'other edit' : 'later edit', changed: true }] },
      ] },
    } })
  })
  await page.goto('/')
  await page.getByLabel('조회할 제목').fill('Test')
  await page.getByRole('button', { name: '원문 조회' }).click()
  const draft = page.getByLabel('편집할 Wikitext 원문')
  const summary = page.getByLabel('편집 요약', { exact: true })
  await draft.fill('my draft')
  await summary.fill('my summary')
  await page.getByRole('button', { name: '편집 저장' }).click()
  await expect(page.getByRole('region', { name: '충돌 리비전 비교' })).toContainText('other edit')
  await expect(page.getByRole('button', { name: '편집 저장' })).toBeDisabled()
  expect(bases).toEqual(['base'])
  await page.getByRole('button', { name: '충돌 리비전을 저장 기준으로 선택' }).click()
  await expect(draft).toHaveValue('my draft')
  await expect(summary).toHaveValue('my summary')
  await page.getByRole('button', { name: '편집 저장' }).click()
  await expect(page.getByRole('region', { name: '충돌 리비전 비교' })).toContainText('later edit')
  expect(bases).toEqual(['base', 'conflict'])
  await expect(draft).toHaveValue('my draft')
  await expect(summary).toHaveValue('my summary')
})

for (const head of ['public-head', undefined]) {
  test(`retains the draft when comparison is unavailable (${head ?? 'hidden head'})`, async ({ page }) => {
    await page.route('**/api/v1/**', route => route.request().method() === 'GET'
      ? route.fulfill({ json: { id: 'p', currentRevisionId: 'base', normalizedTitle: 'Test', source: 'original' } })
      : route.fulfill({ status: 409, json: { code: 'EDIT_CONFLICT', currentRevisionId: head } }))
    await page.goto('/')
    await page.getByLabel('조회할 제목').fill('Test')
    await page.getByRole('button', { name: '원문 조회' }).click()
    await page.getByLabel('편집할 Wikitext 원문').fill('draft')
    await page.getByLabel('편집 요약', { exact: true }).fill('summary')
    await page.getByRole('button', { name: '편집 저장' }).click()
    await expect(page.getByText('리비전 비교를 표시할 수 없습니다.', { exact: false })).toBeVisible()
    await expect(page.getByRole('region', { name: '충돌 리비전 비교' })).toHaveCount(0)
    await expect(page.getByLabel('편집할 Wikitext 원문')).toHaveValue('draft')
    await expect(page.getByLabel('편집 요약', { exact: true })).toHaveValue('summary')
    await expect(page.getByRole('button', { name: '충돌 리비전을 저장 기준으로 선택' })).toHaveCount(head ? 1 : 0)
  })
}
