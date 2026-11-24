import { useEffect, useState, type ReactNode } from 'react'
import { Activity, ArrowUpRight, Bell, ChevronDown, CircleHelp, Command, Grid2X2, KeyRound, LineChart, LogOut, RefreshCw, Search, Settings2, ShieldCheck, Star, TrendingUp, Wallet } from 'lucide-react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

type Stock = { code: string; name: string; market: string; price?: number }
type Point = { time: string; price: number }
type CredentialStatus = { environment: string; configured: boolean; appKey: string; accountNumber: string; accountProductCode: string; active: boolean }
type Tab = '대시보드' | '내 계좌' | '종목 탐색' | '가격 추이' | 'API 설정'
type Holding = { code: string; name: string; quantity: number; averagePrice: number | null; currentPrice: number | null; purchaseAmount: number | null; evaluationAmount: number | null; profitLoss: number | null; profitRate: number | null }
type AccountBalance = { environment: string; accountNumber: string; totalEvaluation: number | null; netAsset: number | null; deposit: number | null; settlementDeposit: number | null; purchaseAmount: number | null; evaluationAmount: number | null; profitLoss: number | null; profitRate: number | null; holdings: Holding[]; fetchedAt: string }
const periods = ['일간', '주간', '월간', '분기', '연간']
const periodCodes: Record<string, string> = { '일간': 'DAY', '주간': 'WEEK', '월간': 'MONTH', '분기': 'QUARTER', '연간': 'YEAR' }
const tabDescriptions: Record<Tab, string> = {
  '대시보드': '오늘 시장의 흐름을 확인하고 관심 종목을 관리해 보세요.',
  '내 계좌': '한국투자증권 계좌의 보유 종목과 평가 손익을 조회합니다. 조회 전용이며 주문은 하지 않습니다.',
  '종목 탐색': '한국투자 종목 마스터에서 종목을 찾아 모니터링 목록에 추가하세요.',
  '가격 추이': '모니터링 중인 종목의 수집된 가격 이력을 기간별로 확인하세요.',
  'API 설정': '내 계정에 연결할 한국투자증권 Open API 앱키와 계좌를 등록하세요.',
}

async function csrfHeaders(): Promise<Record<string, string>> {
  const response = await fetch('/api/session/csrf')
  if (!response.ok) throw new Error('CSRF token unavailable')
  const csrf = await response.json() as { headerName: string; token: string }
  return { [csrf.headerName]: csrf.token }
}

export default function App() {
  const [loggedIn, setLoggedIn] = useState(false)
  const [user, setUser] = useState('')
  const [tab, setTab] = useState<Tab>('대시보드')
  const [period, setPeriod] = useState('일간')
  const [query, setQuery] = useState('')
  const [stocks, setStocks] = useState<Stock[]>([])
  const [scheduled, setScheduled] = useState<Stock[]>([])
  const [selectedCode, setSelectedCode] = useState<string | null>(null)
  const [chartPoints, setChartPoints] = useState<Point[]>([])
  const [message, setMessage] = useState('')
  const [credentials, setCredentials] = useState<CredentialStatus[]>([])
  const activeCredential = credentials.find(c => c.active)
  const environment = activeCredential?.environment ?? '—'
  const selectedStock = scheduled.find(s => s.code === selectedCode) ?? scheduled[0]

  const notify = (text: string) => { setMessage(text); window.setTimeout(() => setMessage(''), 3000) }

  useEffect(() => { fetch('/api/session').then(r => r.json()).then(d => { setLoggedIn(Boolean(d.authenticated)); setUser(d.name || '') }).catch(() => {}) }, [])
  useEffect(() => {
    if (!loggedIn) return
    let active = true
    const refreshSchedule = () => fetch('/api/schedule').then(r => r.ok ? r.json() : []).then((rows: Stock[]) => { if (active) setScheduled(rows) }).catch(() => { if (active) setScheduled([]) })
    void refreshSchedule()
    const timer = window.setInterval(refreshSchedule, 60_000)
    return () => { active = false; window.clearInterval(timer) }
  }, [loggedIn])
  const loadCredentials = () => Promise.all(['PAPER', 'REAL'].map(env => fetch(`/api/admin/credentials/${env}`)
    .then(r => r.ok ? r.json() as Promise<CredentialStatus> : null)
    .catch(() => null)))
    .then(rows => setCredentials(rows.filter((row): row is CredentialStatus => row !== null)))
  useEffect(() => { if (loggedIn) void loadCredentials() }, [loggedIn])
  const selectedStockCode = selectedStock?.code
  useEffect(() => {
    if (!loggedIn || !selectedStockCode) { setChartPoints([]); return }
    let active = true
    const format = new Intl.DateTimeFormat('ko-KR', period === '일간' ? { hour: '2-digit', minute: '2-digit' } : { month: 'numeric', day: 'numeric' })
    const refreshTrend = () => fetch(`/api/stocks/${selectedStockCode}/trend?period=${periodCodes[period]}`)
      .then(r => r.ok ? r.json() : [])
      .then((rows: { time: string; price: number }[]) => { if (active) setChartPoints(rows.map(p => ({ time: format.format(new Date(p.time)), price: Number(p.price) }))) })
      .catch(() => { if (active) setChartPoints([]) })
    void refreshTrend()
    const timer = window.setInterval(refreshTrend, 60_000)
    return () => { active = false; window.clearInterval(timer) }
  }, [loggedIn, period, selectedStockCode])

  const search = async () => {
    if (!query.trim()) { setStocks([]); return }
    try { const r = await fetch(`/api/stocks/search?q=${encodeURIComponent(query)}`); if (r.ok) setStocks(await r.json()); else setStocks([]) } catch { setStocks([]) }
  }
  const addStock = async (stock: Stock) => {
    const next = scheduled.some(s => s.code === stock.code) ? scheduled : [...scheduled, stock]
    const response = await csrfHeaders().then(headers => fetch('/api/schedule', { method: 'PUT', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify({ codes: next.map(item => item.code) }) })).catch(() => null)
    if (response?.ok) {
      setScheduled(await response.json())
      notify(`${stock.name} 종목을 모니터링 목록에 추가했어요.`)
    } else notify('종목 저장에 실패했어요. 로그인과 API 서버 연결을 확인해 주세요.')
  }
  const logout = async () => {
    const response = await csrfHeaders().then(headers => fetch('/logout', { method: 'POST', headers, redirect: 'manual' })).catch(() => null)
    // A redirect (opaqueredirect) also means the server accepted the logout.
    if (response && (response.ok || response.type === 'opaqueredirect')) {
      setLoggedIn(false); setUser(''); setScheduled([]); setCredentials([]); setStocks([]); setQuery(''); setSelectedCode(null); setTab('대시보드')
    } else notify('로그아웃에 실패했어요. API 서버 연결을 확인해 주세요.')
  }
  const openTrend = (code: string) => { setSelectedCode(code); setTab('가격 추이') }

  if (!loggedIn) return <main className="login-shell"><div className="login-art"><div className="brand"><span className="brand-mark"><Activity size={19}/></span> market<span className="brand-light">desk</span></div><div className="hero-copy"><div className="eyebrow"><span className="pulse"/> YOUR MARKET, IN FOCUS</div><h1>투자의 흐름을<br/><em>한눈에</em> 읽다.</h1><p>관심 종목부터 시장의 움직임까지.<br/>나만의 투자 데스크를 시작하세요.</p><div className="hero-chart"><div className="chart-label"><span>KOSPI COMPOSITE</span><strong>2,742.11 <small>+1.28%</small></strong></div><svg viewBox="0 0 540 130" preserveAspectRatio="none"><defs><linearGradient id="fill" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#90efc0" stopOpacity=".25"/><stop offset="1" stopColor="#90efc0" stopOpacity="0"/></linearGradient></defs><path d="M0 106 C35 96 34 64 68 74 S104 105 141 77 S180 86 212 63 S255 76 286 49 S328 62 362 48 S396 61 426 31 S466 47 495 21 S522 32 540 12 V130 H0Z" fill="url(#fill)"/><path d="M0 106 C35 96 34 64 68 74 S104 105 141 77 S180 86 212 63 S255 76 286 49 S328 62 362 48 S396 61 426 31 S466 47 495 21 S522 32 540 12" fill="none" stroke="#90efc0" strokeWidth="2"/></svg><div className="chart-days"><span>09:00</span><span>11:00</span><span>13:00</span><span>15:30</span></div></div><div className="art-note"><ShieldCheck size={16}/> 내 데이터는 안전하게 보호됩니다</div></div></div><div className="login-card"><div className="mobile-brand"><span className="brand-mark"><Activity size={18}/></span> market<span className="brand-light">desk</span></div><div className="card-icon"><TrendingUp size={23}/></div><h2>다시 만나서 반가워요</h2><p>계정으로 로그인하고 투자 데스크를 열어보세요.</p><a className="oauth google" href="/oauth2/authorization/google"><span className="google-g">G</span> Google로 계속하기</a><a className="oauth github" href="/oauth2/authorization/github"><Command size={18}/> GitHub로 계속하기</a><div className="divider"><span/>안전한 OAuth 로그인<span/></div><div className="login-foot"><ShieldCheck size={14}/> 계정 비밀번호를 저장하지 않습니다</div><div className="login-error" role="status">{message}</div></div></main>

  const chart = <TrendChart stock={selectedStock} points={chartPoints} period={period} onPeriod={setPeriod}
    picker={tab === '가격 추이' && scheduled.length > 1 ? <div className="periods stock-picker" aria-label="종목 선택">{scheduled.map(s => <button key={s.code} className={s.code === selectedStock?.code ? 'selected' : ''} onClick={() => setSelectedCode(s.code)}>{s.name}</button>)}</div> : null}
    large={tab === '가격 추이'}/>
  const watchlist = <Watchlist stocks={scheduled} onOpen={openTrend} onAdd={() => setTab('종목 탐색')} showAdd={tab !== '종목 탐색'}/>

  return <div className="app-shell"><aside className="sidebar"><div className="brand"><span className="brand-mark"><Activity size={18}/></span> market<span className="brand-light">desk</span></div><div className="workspace"><div className="workspace-icon">K</div><div><strong>KIS 투자 데스크</strong><small>개인 워크스페이스</small></div><ChevronDown size={14}/></div><div className="nav-label">WORKSPACE</div><nav>{([['대시보드', Grid2X2], ['내 계좌', Wallet], ['종목 탐색', Search], ['가격 추이', LineChart]] as const).map(([name, Icon]) => <button key={name} className={`nav-item ${tab === name ? 'active' : ''}`} aria-current={tab === name ? 'page' : undefined} onClick={() => setTab(name)}><Icon size={17}/>{name}{name === '대시보드' && <span className="nav-dot"/>}</button>)}</nav><div className="nav-label portfolio-label">MY PORTFOLIO <button aria-label="관심 종목 추가" onClick={() => setTab('종목 탐색')}>+</button></div><div className="watch-list">{scheduled.slice(0, 4).map(s => <button key={s.code} onClick={() => openTrend(s.code)}><span className="watch-symbol">{s.name.slice(0, 1)}</span><span className="watch-name">{s.name}<small>{s.code}</small></span><Star size={13} className="watch-star"/></button>)}</div><div className="sidebar-bottom"><button className={`nav-item ${tab === 'API 설정' ? 'active' : ''}`} aria-current={tab === 'API 설정' ? 'page' : undefined} onClick={() => setTab('API 설정')}><Settings2 size={17}/>API 설정</button><button className="nav-item"><CircleHelp size={17}/>도움말</button><div className="profile"><div className="avatar">{user?.slice(0, 1) || 'U'}</div><div className="profile-name"><strong>{user || '사용자'}</strong><small>관리자</small></div><button aria-label="로그아웃" onClick={() => void logout()}><LogOut size={16}/></button></div></div></aside>
    <main className="main"><header className="topbar"><div className="breadcrumbs">Workspace <span>/</span> {tab}</div><div className="top-actions"><div className="market-open"><i/> 장 마감 <span>15:30</span></div><button className="icon-button" aria-label="알림"><Bell size={17}/><i/></button><div className="top-avatar">{user?.slice(0, 1) || 'U'}</div></div></header><div className="content"><div className="page-heading"><div><div className="date-label">{new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())}</div><h1>{tab === '대시보드' ? <>좋은 하루예요{user ? `, ${user}` : ''} <span>👋</span></> : tab}</h1><p>{tabDescriptions[tab]}</p></div>{tab !== '종목 탐색' && <button className="outline-button" onClick={() => setTab('종목 탐색')}><Search size={15}/> 종목 추가</button>}</div>

      {tab === '대시보드' && <>
        <div className="metric-grid"><Metric label="KOSPI" value="—" change="시장 지수 미연동" sub="종목 모니터링 데이터 기반"/><Metric label="KOSDAQ" value="—" change="시장 지수 미연동" sub="종목 모니터링 데이터 기반"/><Metric label="모니터링 종목" value={String(scheduled.length).padStart(2, '0')} change="가격 이력 수집" sub="저장된 종목을 1분 간격 조회"/><Metric label="API 연결 상태" value={activeCredential?.configured ? '설정됨' : '미설정'} change={`${environment} 모드`} sub="서버 설정 파일 기준" status/></div>
        <div className="dashboard-grid">{chart}{watchlist}</div>
      </>}

      {tab === '내 계좌' && <AccountView/>}

      {tab === '종목 탐색' && <div className="dashboard-grid tab-grid">
        <section className="panel search-panel"><div className="panel-head"><div><div className="panel-kicker">DISCOVER</div><h3>종목 검색</h3></div><span className="source-pill"><i/> 한국투자 종목 마스터</span></div><div className="search-form"><div className="search-input"><Search size={17}/><input autoFocus value={query} onChange={e => setQuery(e.target.value)} onKeyDown={e => e.key === 'Enter' && search()} placeholder="종목명 또는 종목코드를 입력하세요" aria-label="종목명 또는 종목코드"/><kbd>↵</kbd></div><button className="primary-button" onClick={search}>검색</button></div>{stocks.length > 0 && <div className="results">{stocks.map(s => { const added = scheduled.some(w => w.code === s.code); return <div className="result-row" key={s.code}><div><strong>{s.name}</strong><small>{s.code} · {s.market}</small></div><button className="outline-button compact" disabled={added} onClick={() => addStock(s)}>{added ? '모니터링 중' : '+ 모니터링'}</button></div> })}</div>}</section>
        {watchlist}
      </div>}

      {tab === '가격 추이' && <div className="tab-grid">{scheduled.length ? chart : <section className="panel"><div className="chart-empty">모니터링 중인 종목이 없어요. 종목 탐색에서 종목을 추가하세요.</div></section>}</div>}

      {tab === 'API 설정' && <CredentialPanel credentials={credentials} onChanged={loadCredentials} notify={notify}/>}

      <footer className="footer"><span>© {new Date().getFullYear()} Marketdesk</span><span>한국투자증권 Open API · {environment} 환경</span><span><i/> 서버 설정에 따라 데이터가 표시됩니다</span></footer></div></main>{message && <div className="toast" role="status">{message}</div>}</div>
}

const won = (value: number | null | undefined) => value == null ? '—' : `${Math.round(value).toLocaleString('ko-KR')}원`
const signed = (value: number | null | undefined, suffix: string) => value == null ? '—' : `${value > 0 ? '+' : ''}${suffix === '%' ? value.toFixed(2) : Math.round(value).toLocaleString('ko-KR')}${suffix}`
const tone = (value: number | null | undefined) => value == null || value === 0 ? '' : value > 0 ? 'gain' : 'loss'

function CredentialPanel({ credentials, onChanged, notify }: { credentials: CredentialStatus[]; onChanged: () => Promise<void>; notify: (text: string) => void }) {
  const activeEnv = credentials.find(c => c.active)?.environment ?? 'REAL'
  const [env, setEnv] = useState<string | null>(null)
  const target = env ?? activeEnv
  const current = credentials.find(c => c.environment === target)
  const [appKey, setAppKey] = useState('')
  const [appSecret, setAppSecret] = useState('')
  const [accountNumber, setAccountNumber] = useState('')
  const [productCode, setProductCode] = useState('01')
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState('')
  const reset = () => { setAppKey(''); setAppSecret(''); setAccountNumber(''); setProductCode(current?.accountProductCode || '01'); setError(''); setConfirmDelete(false) }
  useEffect(reset, [target, current?.configured])
  const send = async (method: 'PUT' | 'DELETE') => {
    setSaving(true); setError('')
    try {
      const headers = await csrfHeaders()
      const response = await fetch(`/api/admin/credentials/${target}`, method === 'PUT'
        ? { method, headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify({ appKey, appSecret, accountNumber, accountProductCode: productCode }) }
        : { method, headers })
      if (response.ok) {
        await onChanged(); reset()
        notify(method === 'PUT' ? 'KIS 인증 정보를 암호화해 저장했어요.' : 'KIS 인증 정보를 삭제했어요.')
      } else {
        const body = await response.json().catch(() => null) as { message?: string } | null
        setError(response.status === 400 ? (body?.message || '입력값을 확인해 주세요.') : '저장하지 못했어요. 로그인 상태를 확인해 주세요.')
      }
    } catch { setError('API 서버에 연결할 수 없어요.') }
    setSaving(false)
  }
  const needsSecret = !current?.configured
  const canSave = appKey.trim() !== '' && /^\d{8}$/.test(accountNumber.replace(/-/g, '').trim()) && (!needsSecret || appSecret.trim() !== '')
  return <section className="panel credential-panel tab-grid"><div className="panel-head"><div><div className="panel-kicker">CONNECTION</div><h3>KIS API 인증 정보</h3></div><span className="source-pill"><KeyRound size={13}/> 내 계정 전용 · 암호화 저장</span></div>
    <p className="panel-description">앱키·시크릿·계좌번호는 로그인한 내 계정에만 연결되어 DB에 암호화 저장됩니다. 다른 사용자는 내 정보를 볼 수 없고, 시크릿은 저장 후 다시 표시하지 않습니다.</p>
    {credentials.map(c => <div className="credential-status" key={c.environment} data-testid={`credential-${c.environment}`}><span className={c.configured ? 'configured-dot' : 'unconfigured-dot'}/>{c.environment === 'REAL' ? '실전투자 (REAL)' : '모의투자 (PAPER)'}{c.active ? ' · 사용 중' : ''} — {c.configured ? `등록됨 · App Key ${c.appKey}${c.accountNumber ? ` · 계좌 ${c.accountNumber}` : ''}` : '설정되지 않음'}</div>)}
    <p className="panel-description">현재 사용 환경: {activeEnv} 모드</p>
    <form className="credential-form" onSubmit={e => { e.preventDefault(); if (canSave) void send('PUT') }}>
      <label>환경<select value={target} onChange={e => setEnv(e.target.value)}><option value="REAL">실전투자 (REAL)</option><option value="PAPER">모의투자 (PAPER)</option></select></label>
      <label>App Key<input value={appKey} onChange={e => setAppKey(e.target.value)} autoComplete="off" spellCheck={false} placeholder={current?.configured ? `현재 ${current.appKey}` : 'PS로 시작하는 앱키'}/></label>
      <label>App Secret<input type="password" value={appSecret} onChange={e => setAppSecret(e.target.value)} autoComplete="new-password" placeholder={current?.configured ? '변경할 때만 입력' : '앱 시크릿'}/></label>
      <div className="form-row"><label>계좌번호 (앞 8자리)<input value={accountNumber} onChange={e => setAccountNumber(e.target.value)} inputMode="numeric" autoComplete="off" placeholder={current?.configured ? `현재 ${current.accountNumber}` : '12345678'}/></label><label>상품코드<input value={productCode} onChange={e => setProductCode(e.target.value)} inputMode="numeric" maxLength={2}/></label></div>
      {current?.configured && <p className="form-hint">수정할 때도 App Key와 계좌번호는 다시 입력해야 해요. 화면에는 가린 값만 보여주기 때문이에요.</p>}
      {error && <div className="account-error" role="alert">{error}</div>}
      <div className="form-actions"><button type="submit" className="primary-button" disabled={!canSave || saving}>{saving ? '저장 중' : current?.configured ? '변경 저장' : '등록'}</button>
        {current?.configured && <button type="button" className="outline-button danger" disabled={saving} onClick={() => confirmDelete ? void send('DELETE') : setConfirmDelete(true)}>{confirmDelete ? '한 번 더 누르면 삭제' : '삭제'}</button>}</div>
    </form>
  </section>
}

function AccountView() {
  const [balance, setBalance] = useState<AccountBalance | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = async () => {
    setLoading(true); setError('')
    try {
      const response = await fetch('/api/account/balance')
      if (response.ok) setBalance(await response.json())
      else setError(response.status === 404 ? '등록된 KIS 계좌 정보가 없어요. API 설정에서 앱키와 계좌를 등록해 주세요.'
        : response.status === 503 ? '등록된 KIS 계좌 정보가 올바르지 않아요. API 설정을 확인해 주세요.'
        : response.status === 502 ? '한국투자증권 잔고 조회에 실패했어요. 잠시 후 다시 시도해 주세요.'
        : '계좌 정보를 불러오지 못했어요. 로그인 상태를 확인해 주세요.')
    } catch { setError('API 서버에 연결할 수 없어요.') }
    setLoading(false)
  }
  useEffect(() => { void load() }, [])
  const b = balance
  return <div className="account-view">
    <div className="metric-grid">
      <Metric label="총 평가금액" value={won(b?.totalEvaluation)} change={`계좌 ${b?.accountNumber || '—'}`} sub={`${b?.environment ?? '—'} 계좌 · 예수금 포함`}/>
      <section className="metric-card"><div className="metric-top"><span>평가손익</span><span><TrendingUp size={15}/></span></div><div className={`metric-value ${tone(b?.profitLoss)}`}>{signed(b?.profitLoss, '원')}</div><div className={`metric-change ${tone(b?.profitRate)}`}>수익률 {signed(b?.profitRate, '%')}</div><div className="metric-sub">보유 종목 평가 기준</div></section>
      <Metric label="주식 평가금액" value={won(b?.evaluationAmount)} change={`매입금액 ${won(b?.purchaseAmount)}`} sub={`보유 종목 ${b?.holdings.length ?? 0}개`}/>
      <Metric label="예수금" value={won(b?.deposit)} change={`D+2 예수금 ${won(b?.settlementDeposit)}`} sub="주문 가능 금액과 다를 수 있음"/>
    </div>
    <section className="panel holdings-panel"><div className="panel-head"><div><div className="panel-kicker">HOLDINGS</div><h3>보유 종목 <span className="count-badge">{b?.holdings.length ?? 0}</span></h3></div><div className="account-actions">{b && <span className="metric-sub">{new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(b.fetchedAt))} 조회</span>}<button className="outline-button compact" onClick={() => void load()} disabled={loading}><RefreshCw size={13}/> {loading ? '조회 중' : '새로고침'}</button></div></div>
      {error && <div className="account-error" role="alert">{error}</div>}
      {!error && b && b.holdings.length === 0 && <div className="chart-empty holdings-empty">보유 중인 종목이 없어요.</div>}
      {!error && b && b.holdings.length > 0 && <div className="holdings-table-wrap"><table className="holdings-table"><thead><tr><th>종목</th><th>보유수량</th><th>매입평균가</th><th>현재가</th><th>평가금액</th><th>평가손익</th><th>수익률</th></tr></thead><tbody>{b.holdings.map(h => <tr key={h.code}><td><strong>{h.name}</strong><small>{h.code}</small></td><td>{h.quantity.toLocaleString('ko-KR')}주</td><td>{won(h.averagePrice)}</td><td>{won(h.currentPrice)}</td><td>{won(h.evaluationAmount)}</td><td className={tone(h.profitLoss)}>{signed(h.profitLoss, '원')}</td><td className={tone(h.profitRate)}>{signed(h.profitRate, '%')}</td></tr>)}</tbody></table></div>}
    </section>
  </div>
}

function TrendChart({ stock, points, period, onPeriod, picker, large }: { stock?: Stock; points: Point[]; period: string; onPeriod: (p: string) => void; picker: ReactNode; large: boolean }) {
  return <section className={`panel chart-panel ${large ? 'chart-panel-large' : ''}`}><div className="panel-head"><div><div className="panel-kicker">PRICE OVERVIEW</div><h3>{stock?.name || '모니터링 종목을 추가하세요'} 추이</h3></div><div className="periods">{periods.map(p => <button key={p} className={period === p ? 'selected' : ''} onClick={() => onPeriod(p)}>{p}</button>)}</div></div>{picker}<div className="chart-summary"><strong>{points.length ? `${points.at(-1)?.price.toLocaleString()}원` : '—'}</strong><span className="metric-sub">{stock?.code || '종목 미선택'}</span></div><div className="main-chart">{points.length ? <ResponsiveContainer width="100%" height="100%"><AreaChart data={points} margin={{ top: 12, right: 8, left: -18, bottom: 0 }}><defs><linearGradient id="marketFill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="#40d99a" stopOpacity={.2}/><stop offset="95%" stopColor="#40d99a" stopOpacity={0}/></linearGradient></defs><CartesianGrid stroke="#252b35" vertical={false}/><XAxis dataKey="time" axisLine={false} tickLine={false} tick={{ fill: '#727c8b', fontSize: 11 }} dy={8}/><YAxis domain={['dataMin - 1000', 'dataMax + 1000']} axisLine={false} tickLine={false} tick={{ fill: '#727c8b', fontSize: 11 }} tickFormatter={v => `${Math.round(v / 1000)}k`}/><Tooltip contentStyle={{ background: '#171d27', border: '1px solid #303846', borderRadius: 10, color: '#f3f6fa' }} formatter={(v: any) => [`${Number(v).toLocaleString()}원`, '가격']}/><Area type="monotone" dataKey="price" stroke="#50dca2" strokeWidth={2.5} fill="url(#marketFill)"/></AreaChart></ResponsiveContainer> : <div className="chart-empty">가격 이력이 쌓이면 선택한 기간의 추이를 표시합니다.</div>}</div></section>
}

function Watchlist({ stocks, onOpen, onAdd, showAdd }: { stocks: Stock[]; onOpen: (code: string) => void; onAdd: () => void; showAdd: boolean }) {
  return <section className="panel movers-panel"><div className="panel-head"><div><div className="panel-kicker">YOUR WATCHLIST</div><h3>관심 종목 <span className="count-badge">{stocks.length}</span></h3></div></div><div className="stock-list">{stocks.length === 0 && <div className="chart-empty">아직 모니터링 중인 종목이 없어요.</div>}{stocks.map((s, i) => <button className="stock-row" key={s.code} onClick={() => onOpen(s.code)} aria-label={`${s.name} 가격 추이 보기`}><div className={`stock-logo logo-${i % 4}`}>{s.name.slice(0, 1)}</div><div className="stock-title"><strong>{s.name}</strong><small>{s.code} · {s.market}</small></div><div className="stock-price"><strong>{s.price == null ? '가격 수집 대기' : `${s.price.toLocaleString()}원`}</strong><small>변동률은 시세 API 연동 후 표시</small></div></button>)}</div>{showAdd && <button className="add-watch" onClick={onAdd}>+ 관심 종목 추가</button>}</section>
}

function Metric({ label, value, change, positive, sub, status }: any) { return <section className="metric-card"><div className="metric-top"><span>{label}</span><span className={status ? 'status-mark' : ''}>{status ? <ShieldCheck size={16}/> : <TrendingUp size={15}/>}</span></div><div className="metric-value">{value}</div><div className={`metric-change ${positive ? 'positive' : ''}`}>{positive ? <ArrowUpRight size={14}/> : null}{change}</div><div className="metric-sub">{sub}</div></section> }
