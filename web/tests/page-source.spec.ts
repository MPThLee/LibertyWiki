// SPDX-License-Identifier: AGPL-3.0-only
import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'

test('creates and looks up literal Wikitext through the web UI', async ({ page }) => {
  const token = process.env.WIKI_BROWSER_TOKEN
  if (!token) throw new Error('Set WIKI_BROWSER_TOKEN to the server operator token.')
  const title = `Browser source ${randomUUID()}`
  const source = '<script>document.body.innerText = "injected"</script>\n== Source =='
  await page.goto('/')
  await page.getByLabel('운영자 토큰').fill(token)
  await page.getByLabel('새 문서 제목').fill(title)
  await page.getByLabel('Wikitext 원문', { exact: true }).fill(source)
  await page.getByRole('button', { name: '문서 생성' }).click()
  await expect(page.getByRole('status')).toHaveText('문서를 만들었습니다.')
  await expect(page.getByLabel('현재 Wikitext 원문')).toHaveText(source)
  await expect(page.locator('pre script')).toHaveCount(0)

  await page.reload()
  await page.getByLabel('조회할 제목').fill(title.replaceAll(' ', '_'))
  await page.getByRole('button', { name: '원문 조회' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await expect(page.getByLabel('현재 Wikitext 원문')).toHaveText(source)

  await page.getByLabel('조회할 제목').fill(`Missing ${randomUUID()}`)
  await page.getByRole('button', { name: '원문 조회' }).click()
  await expect(page.getByRole('alert')).toHaveText('이 제목의 문서를 찾을 수 없습니다.')

  await page.getByLabel('조회할 제목').fill('[invalid]')
  await page.getByRole('button', { name: '원문 조회' }).click()
  await expect(page.getByRole('alert')).toHaveText('일반 이름공간에서 사용할 수 있는 제목을 입력해 주세요.')

  await page.getByLabel('새 문서 제목').fill(`Denied ${randomUUID()}`)
  await page.getByRole('button', { name: '문서 생성' }).click()
  await expect(page.getByRole('alert')).toHaveText('이 작업을 수행할 권한이 없습니다.')
})
