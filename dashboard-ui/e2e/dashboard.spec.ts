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
  await page.route('**/api/admin/credentials/PAPER', route => route.fulfill({ json: { environment: 'PAPER', configured: false, appKey: '', accountNumber: '', active: false } }))
  await page.route('**/api/admin/credentials/REAL', route => route.fulfill({ json: { environment: 'REAL', configured: true, appKey: 'PSab••••wxyz', accountNumber: '••••5678', active: true } }))
})

test('대시보드에서 종목을 검색하고 관심 목록에 추가한다', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: /좋은 하루예요, Reals/ })).toBeVisible()
  await expect(page.getByLabel('종목명 또는 종목코드')).toHaveCount(0)
  await page.getByRole('button', { name: '종목 탐색' }).click()
  await page.getByLabel('종목명 또는 종목코드').fill('네이버')
  await page.getByRole('button', { name: '검색' }).click()
  await expect(page.getByText('네이버').last()).toBeVisible()
  await page.getByRole('button', { name: '+ 모니터링' }).first().click()
  await expect(page.getByRole('status')).toContainText('모니터링 목록에 추가')
  await page.getByRole('button', { name: '대시보드' }).click()
  await expect(page.getByText('03', { exact: true })).toBeVisible()
  const trendResponse = page.waitForResponse(response => response.url().includes('period=YEAR'))
  await page.getByRole('button', { name: '연간' }).click()
  await trendResponse
})

test('설정 파일의 API 인증 정보 상태를 읽기 전용으로 보여준다', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'API 설정' }).click()
  await expect(page.getByRole('heading', { name: 'KIS API 인증 정보' })).toBeVisible()
  await expect(page.getByTestId('credential-REAL')).toContainText('사용 중')
  await expect(page.getByTestId('credential-REAL')).toContainText('등록됨 · App Key PSab••••wxyz · 계좌 ••••5678')
  await expect(page.getByTestId('credential-PAPER')).toContainText('설정되지 않음')
  await expect(page.getByLabel('App Secret')).toHaveCount(0)
  await expect(page.getByText('REAL 모드')).toBeVisible()
  await expect(page.getByText('PRICE OVERVIEW')).toHaveCount(0)
})

test('비로그인 사용자는 Google 및 GitHub 로그인 화면을 본다', async ({ page }) => {
  await page.unroute('**/api/session')
  await page.route('**/api/session', route => route.fulfill({ json: { authenticated: false } }))
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('link', { name: 'Google로 계속하기' })).toHaveAttribute('href', '/oauth2/authorization/google')
  await expect(page.getByRole('link', { name: 'GitHub로 계속하기' })).toHaveAttribute('href', '/oauth2/authorization/github')
})

test('가격 추이 탭에서 모니터링 종목을 골라 추이를 본다', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '가격 추이', exact: true }).click()
  await expect(page.getByRole('heading', { name: '삼성전자 추이' })).toBeVisible()
  await expect(page.getByText('YOUR WATCHLIST')).toHaveCount(0)
  const trendResponse = page.waitForResponse(response => response.url().includes('/api/stocks/000660/trend'))
  await page.getByLabel('종목 선택').getByRole('button', { name: 'SK하이닉스' }).click()
  await trendResponse
  await expect(page.getByRole('heading', { name: 'SK하이닉스 추이' })).toBeVisible()
})

test('로그아웃하면 서버 세션을 종료하고 로그인 화면으로 돌아간다', async ({ page }) => {
  let logoutHeader: string | undefined
  await page.route('**/logout', route => { logoutHeader = route.request().headers()['x-xsrf-token']; return route.fulfill({ status: 200, body: '' }) })
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page.getByRole('link', { name: 'GitHub로 계속하기' })).toBeVisible()
  expect(logoutHeader).toBe('test-csrf-token')
})

test('내 계좌 탭에서 보유 종목과 평가손익을 조회한다', async ({ page }) => {
  let calls = 0
  await page.route('**/api/account/balance', route => { calls++; return route.fulfill({ json: {
    environment: 'REAL', accountNumber: '••••5678', totalEvaluation: 1718000, netAsset: 1718000, deposit: 1000000, settlementDeposit: 950000,
    purchaseAmount: 700000, evaluationAmount: 768000, profitLoss: 68000, profitRate: 9.71, fetchedAt: '2026-11-24T06:00:00Z',
    holdings: [{ code: '005930', name: '삼성전자', quantity: 10, averagePrice: 70000, currentPrice: 76800, purchaseAmount: 700000, evaluationAmount: 768000, profitLoss: 68000, profitRate: 9.71 }] } }) })
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '내 계좌' }).click()
  await expect(page.getByText('1,718,000원')).toBeVisible()
  await expect(page.getByText('계좌 ••••5678')).toBeVisible()
  const row = page.getByRole('row', { name: /삼성전자/ })
  await expect(row).toContainText('10주')
  await expect(row).toContainText('+68,000원')
  await expect(row).toContainText('+9.71%')
  await page.getByRole('button', { name: '새로고침' }).click()
  await expect.poll(() => calls).toBe(2)
})

test('계좌 조회에 실패하면 이유를 보여준다', async ({ page }) => {
  await page.route('**/api/account/balance', route => route.fulfill({ status: 502, json: { status: 502 } }))
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '내 계좌' }).click()
  await expect(page.getByRole('alert')).toContainText('잔고 조회에 실패')
})
