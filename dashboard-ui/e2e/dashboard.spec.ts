import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/session', route => route.fulfill({ json: { authenticated: true, name: 'Reals' } }))
  await page.route('**/api/session/csrf', route => route.fulfill({ json: { headerName: 'X-XSRF-TOKEN', token: 'test-csrf-token' } }))
  await page.route('**/api/stocks/search?**', route => route.fulfill({ json: [{ code: '035420', name: '네이버', market: 'KOSPI' }, { code: '000660', name: 'SK하이닉스', market: 'KOSPI' }] }))
  await page.route('**/api/schedule', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: [{ code: '005930', name: '삼성전자', market: 'KOSPI', price: 76800 }, { code: '000660', name: 'SK하이닉스', market: 'KOSPI', price: 183500 }] })
    const body = route.request().postDataJSON() as { codes: string[] }
    const catalog = [{ code: '005930', name: '삼성전자', market: 'KOSPI', price: 76800 }, { code: '000660', name: 'SK하이닉스', market: 'KOSPI', price: 183500 }, { code: '035420', name: '네이버', market: 'KOSPI', price: 205000 }]
    return route.fulfill({ json: body.codes.map(code => catalog.find(stock => stock.code === code)).filter(Boolean) })
  })
  await page.route('**/api/stocks/*/trend?**', route => route.fulfill({ json: [{ time: '2026-11-15T09:00:00Z', price: 76800 }, { time: '2026-11-15T10:00:00Z', price: 77200 }] }))
  await page.route('**/api/admin/credentials/**', route => route.request().method() === 'GET'
    ? route.fulfill({ status: 200, json: { configured: true, appKey: 'test••••' } })
    : route.fulfill({ status: 200, json: { saved: true } }))
})

test('대시보드에서 종목을 검색하고 관심 목록에 추가한다', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: /좋은 하루예요, Reals/ })).toBeVisible()
  await page.getByLabel('종목명 또는 종목코드').fill('네이버')
  await page.getByRole('button', { name: '검색' }).click()
  await expect(page.getByText('네이버').last()).toBeVisible()
  await page.getByRole('button', { name: '+ 모니터링' }).first().click()
  await expect(page.getByRole('status')).toContainText('모니터링 목록에 추가')
  await expect(page.getByText('03', { exact: true })).toBeVisible()
  const trendResponse = page.waitForResponse(response => response.url().includes('period=YEAR'))
  await page.getByRole('button', { name: '연간' }).click()
  await trendResponse
})

test('모의투자 API 인증 정보를 저장한다', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'API 설정' }).click()
  await expect(page.getByRole('heading', { name: 'KIS API 인증 정보' })).toBeVisible()
  await expect(page.getByText(/등록됨 · App Key/)).toBeVisible()
  await page.getByLabel('App Key').fill('test-app-key')
  await page.getByLabel('App Secret').fill('test-app-secret')
  await page.getByRole('button', { name: '인증 정보 저장' }).click()
  await expect(page.getByRole('status')).toContainText('DB에 저장')
})

test('비로그인 사용자는 Google 및 GitHub 로그인 화면을 본다', async ({ page }) => {
  await page.unroute('**/api/session')
  await page.route('**/api/session', route => route.fulfill({ json: { authenticated: false } }))
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('link', { name: 'Google로 계속하기' })).toHaveAttribute('href', '/oauth2/authorization/google')
  await expect(page.getByRole('link', { name: 'GitHub로 계속하기' })).toHaveAttribute('href', '/oauth2/authorization/github')
})
