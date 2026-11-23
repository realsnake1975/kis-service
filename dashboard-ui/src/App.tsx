import { useEffect, useState } from 'react'
import { Activity, ArrowUpRight, Bell, ChevronDown, CircleHelp, Command, Grid2X2, KeyRound, LineChart, LogOut, Search, Settings2, ShieldCheck, Star, TrendingUp } from 'lucide-react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

type Stock = { code: string; name: string; market: string; price?: number }
type Point = { time: string; price: number }
const periods = ['일간', '주간', '월간', '분기', '연간']
async function csrfHeaders(): Promise<Record<string, string>> {
  const response = await fetch('/api/session/csrf')
  if (!response.ok) throw new Error('CSRF token unavailable')
  const csrf = await response.json() as { headerName: string; token: string }
  return { [csrf.headerName]: csrf.token }
}
export default function App() {
  const [loggedIn, setLoggedIn] = useState(false)
  const [user, setUser] = useState('')
  const [tab, setTab] = useState('대시보드')
  const [period, setPeriod] = useState('일간')
  const [query, setQuery] = useState('')
  const [stocks, setStocks] = useState<Stock[]>([])
  const [scheduled, setScheduled] = useState<Stock[]>([])
  const [chartPoints, setChartPoints] = useState<Point[]>([])
  const [message, setMessage] = useState('')
  const [environment, setEnvironment] = useState('PAPER')
  const [credentialInfo, setCredentialInfo] = useState<{configured: boolean; appKey: string}>({ configured: false, appKey: '' })

  useEffect(() => { fetch('/api/session').then(r => r.json()).then(d => { setLoggedIn(Boolean(d.authenticated)); setUser(d.name || '') }).catch(() => {}) }, [])
  useEffect(() => {
    if (!loggedIn) return
    let active = true
    const refreshSchedule = () => fetch('/api/schedule').then(r => r.ok ? r.json() : []).then((rows: Stock[]) => { if (active) setScheduled(rows) }).catch(() => { if (active) setScheduled([]) })
    void refreshSchedule()
    const timer = window.setInterval(refreshSchedule, 60_000)
    return () => { active = false; window.clearInterval(timer) }
  }, [loggedIn])
  useEffect(() => {
    if (!loggedIn || tab !== 'API 설정') return
    fetch(`/api/admin/credentials/${environment}`)
      .then(r => r.ok ? r.json() : { configured: false, appKey: '' })
      .then(setCredentialInfo)
      .catch(() => setCredentialInfo({ configured: false, appKey: '' }))
  }, [loggedIn, tab, environment])
  useEffect(() => {
    if (!loggedIn || scheduled.length === 0) { setChartPoints([]); return }
    const periodCode = ({ '일간': 'DAY', '주간': 'WEEK', '월간': 'MONTH', '분기': 'QUARTER', '연간': 'YEAR' } as Record<string, string>)[period]
    const refreshTrend = () => fetch(`/api/stocks/${scheduled[0].code}/trend?period=${periodCode}`)
      .then(r => r.ok ? r.json() : [])
      .then((rows: {time: string; price: number}[]) => setChartPoints(rows.map(p => ({ time: new Intl.DateTimeFormat('ko-KR', period === '일간' ? {hour:'2-digit',minute:'2-digit'} : {month:'numeric',day:'numeric'}).format(new Date(p.time)), price: Number(p.price) }))))
      .catch(() => setChartPoints([]))
    void refreshTrend()
    const timer = window.setInterval(refreshTrend, 60_000)
    return () => window.clearInterval(timer)
  }, [loggedIn, period, scheduled])
  const search = async () => {
    if (!query.trim()) { setStocks([]); return }
    try { const r = await fetch(`/api/stocks/search?q=${encodeURIComponent(query)}`); if (r.ok) setStocks(await r.json()); else setStocks([]) } catch { setStocks([]) }
  }
  const addStock = async (stock: Stock) => {
    const next = scheduled.some(s => s.code === stock.code) ? scheduled : [...scheduled, stock]
    const response = await csrfHeaders().then(headers => fetch('/api/schedule', { method: 'PUT', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify({ codes: next.map(item => item.code) }) })).catch(() => null)
    if (response?.ok) {
      setScheduled(await response.json())
      setMessage(`${stock.name} 종목을 모니터링 목록에 추가했어요.`)
    } else setMessage('종목 저장에 실패했어요. 로그인과 API 서버 연결을 확인해 주세요.')
    setTimeout(() => setMessage(''), 3000)
  }
  const saveCredentials = async (form: FormData) => {
    const body = { appKey: String(form.get('appKey') || ''), appSecret: String(form.get('appSecret') || '') }
    const r = await csrfHeaders().then(headers => fetch(`/api/admin/credentials/${environment}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) })).catch(() => null)
    if (r?.ok) {
      setCredentialInfo({ configured: true, appKey: `${body.appKey.slice(0, 4)}••••` })
      setMessage('인증 정보가 DB에 저장됐어요.')
    } else setMessage('API 서버에 연결할 수 없어요. 서버 설정을 확인해 주세요.')
    setTimeout(() => setMessage(''), 3000)
  }

  if (!loggedIn) return <main className="login-shell"><div className="login-art"><div className="brand"><span className="brand-mark"><Activity size={19}/></span> market<span className="brand-light">desk</span></div><div className="hero-copy"><div className="eyebrow"><span className="pulse"/> YOUR MARKET, IN FOCUS</div><h1>투자의 흐름을<br/><em>한눈에</em> 읽다.</h1><p>관심 종목부터 시장의 움직임까지.<br/>나만의 투자 데스크를 시작하세요.</p><div className="hero-chart"><div className="chart-label"><span>KOSPI COMPOSITE</span><strong>2,742.11 <small>+1.28%</small></strong></div><svg viewBox="0 0 540 130" preserveAspectRatio="none"><defs><linearGradient id="fill" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#90efc0" stopOpacity=".25"/><stop offset="1" stopColor="#90efc0" stopOpacity="0"/></linearGradient></defs><path d="M0 106 C35 96 34 64 68 74 S104 105 141 77 S180 86 212 63 S255 76 286 49 S328 62 362 48 S396 61 426 31 S466 47 495 21 S522 32 540 12 V130 H0Z" fill="url(#fill)"/><path d="M0 106 C35 96 34 64 68 74 S104 105 141 77 S180 86 212 63 S255 76 286 49 S328 62 362 48 S396 61 426 31 S466 47 495 21 S522 32 540 12" fill="none" stroke="#90efc0" strokeWidth="2"/></svg><div className="chart-days"><span>09:00</span><span>11:00</span><span>13:00</span><span>15:30</span></div></div><div className="art-note"><ShieldCheck size={16}/> 내 데이터는 안전하게 보호됩니다</div></div></div><div className="login-card"><div className="mobile-brand"><span className="brand-mark"><Activity size={18}/></span> market<span className="brand-light">desk</span></div><div className="card-icon"><TrendingUp size={23}/></div><h2>다시 만나서 반가워요</h2><p>계정으로 로그인하고 투자 데스크를 열어보세요.</p><a className="oauth google" href="/oauth2/authorization/google"><span className="google-g">G</span> Google로 계속하기</a><a className="oauth github" href="/oauth2/authorization/github"><Command size={18}/> GitHub로 계속하기</a><div className="divider"><span/>안전한 OAuth 로그인<span/></div><div className="login-foot"><ShieldCheck size={14}/> 계정 비밀번호를 저장하지 않습니다</div><div className="login-error" role="status">{message}</div></div></main>

  return <div className="app-shell"><aside className="sidebar"><div className="brand"><span className="brand-mark"><Activity size={18}/></span> market<span className="brand-light">desk</span></div><div className="workspace"><div className="workspace-icon">K</div><div><strong>KIS 투자 데스크</strong><small>개인 워크스페이스</small></div><ChevronDown size={14}/></div><div className="nav-label">WORKSPACE</div><nav>{[['대시보드',Grid2X2],['종목 탐색',Search],['가격 추이',LineChart]].map(([name,Icon]: any) => <button key={name} className={`nav-item ${tab === name ? 'active' : ''}`} onClick={() => setTab(name)}><Icon size={17}/>{name}{name === '대시보드' && <span className="nav-dot"/>}</button>)}</nav><div className="nav-label portfolio-label">MY PORTFOLIO <button aria-label="관심 종목 추가" onClick={() => setTab('종목 탐색')}>+</button></div><div className="watch-list">{scheduled.slice(0, 4).map(s => <button key={s.code} onClick={() => setTab('가격 추이')}><span className="watch-symbol">{s.name.slice(0, 1)}</span><span className="watch-name">{s.name}<small>{s.code}</small></span><Star size={13} className="watch-star"/></button>)}</div><div className="sidebar-bottom"><button className="nav-item" onClick={() => setTab('API 설정')}><Settings2 size={17}/>API 설정</button><button className="nav-item"><CircleHelp size={17}/>도움말</button><div className="profile"><div className="avatar">{user?.slice(0, 1) || 'U'}</div><div className="profile-name"><strong>{user || '사용자'}</strong><small>관리자</small></div><button aria-label="로그아웃" onClick={() => { setLoggedIn(false); setUser('') }}><LogOut size={16}/></button></div></div></aside>
    <main className="main"><header className="topbar"><div className="breadcrumbs">Workspace <span>/</span> {tab}</div><div className="top-actions"><div className="market-open"><i/> 장 마감 <span>15:30</span></div><button className="icon-button" aria-label="알림"><Bell size={17}/><i/></button><div className="top-avatar">{user?.slice(0, 1) || 'U'}</div></div></header><div className="content"><div className="page-heading"><div><div className="date-label">{new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())}</div><h1>{tab === '대시보드' ? <>좋은 하루예요{user ? `, ${user}` : ''} <span>👋</span></> : tab}</h1><p>오늘 시장의 흐름을 확인하고 관심 종목을 관리해 보세요.</p></div><button className="outline-button" onClick={() => setTab('종목 탐색')}><Search size={15}/> 종목 추가</button></div>
      <div className="metric-grid"><Metric label="KOSPI" value="—" change="시장 지수 미연동" sub="종목 모니터링 데이터 기반"/><Metric label="KOSDAQ" value="—" change="시장 지수 미연동" sub="종목 모니터링 데이터 기반"/><Metric label="모니터링 종목" value={String(scheduled.length).padStart(2,'0')} change="가격 이력 수집" sub="저장된 종목을 1분 간격 조회"/><Metric label="API 연결 상태" value="설정 확인" change={`${environment} 모드`} sub="KIS 인증 설정에서 확인" status/></div>
      <div className="dashboard-grid"><section className="panel chart-panel"><div className="panel-head"><div><div className="panel-kicker">PRICE OVERVIEW</div><h3>{scheduled[0]?.name || '모니터링 종목을 추가하세요'} 추이</h3></div><div className="periods">{periods.map(p => <button key={p} className={period === p ? 'selected' : ''} onClick={() => setPeriod(p)}>{p}</button>)}</div></div><div className="chart-summary"><strong>{chartPoints.length ? `${chartPoints.at(-1)?.price.toLocaleString()}원` : '—'}</strong><span className="metric-sub">{scheduled[0]?.code || '종목 미선택'}</span></div><div className="main-chart">{chartPoints.length ? <ResponsiveContainer width="100%" height="100%"><AreaChart data={chartPoints} margin={{top:12,right:8,left:-18,bottom:0}}><defs><linearGradient id="marketFill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="#40d99a" stopOpacity={.2}/><stop offset="95%" stopColor="#40d99a" stopOpacity={0}/></linearGradient></defs><CartesianGrid stroke="#252b35" vertical={false}/><XAxis dataKey="time" axisLine={false} tickLine={false} tick={{fill:'#727c8b',fontSize:11}} dy={8}/><YAxis domain={['dataMin - 1000','dataMax + 1000']} axisLine={false} tickLine={false} tick={{fill:'#727c8b',fontSize:11}} tickFormatter={v=>`${Math.round(v/1000)}k`}/><Tooltip contentStyle={{background:'#171d27',border:'1px solid #303846',borderRadius:10,color:'#f3f6fa'}} formatter={(v:any)=>[`${Number(v).toLocaleString()}원`,'가격']}/><Area type="monotone" dataKey="price" stroke="#50dca2" strokeWidth={2.5} fill="url(#marketFill)"/></AreaChart></ResponsiveContainer> : <div className="chart-empty">가격 이력이 쌓이면 선택한 기간의 추이를 표시합니다.</div>}</div></section>
      <section className="panel movers-panel"><div className="panel-head"><div><div className="panel-kicker">YOUR WATCHLIST</div><h3>관심 종목 <span className="count-badge">{scheduled.length}</span></h3></div><button className="text-button" onClick={() => setTab('종목 탐색')}>전체 보기 <span>→</span></button></div><div className="stock-list">{scheduled.map((s,i)=><div className="stock-row" key={s.code}><div className={`stock-logo logo-${i%4}`}>{s.name.slice(0,1)}</div><div className="stock-title"><strong>{s.name}</strong><small>{s.code} · {s.market}</small></div><div className="stock-price"><strong>{s.price == null ? '가격 수집 대기' : `${s.price.toLocaleString()}원`}</strong><small>변동률은 시세 API 연동 후 표시</small></div></div>)}</div><button className="add-watch" onClick={() => setTab('종목 탐색')}>+ 관심 종목 추가</button></section></div>
      <section className="panel search-panel"><div className="panel-head"><div><div className="panel-kicker">DISCOVER</div><h3>종목 탐색</h3></div><span className="source-pill"><i/> 한국투자 종목 마스터</span></div><div className="search-form"><div className="search-input"><Search size={17}/><input value={query} onChange={e=>setQuery(e.target.value)} onKeyDown={e=>e.key==='Enter'&&search()} placeholder="종목명 또는 종목코드를 입력하세요" aria-label="종목명 또는 종목코드"/><kbd>↵</kbd></div><button className="primary-button" onClick={search}>검색</button></div>{stocks.length>0&&<div className="results">{stocks.map(s=><div className="result-row" key={s.code}><div><strong>{s.name}</strong><small>{s.code} · {s.market}</small></div><button className="outline-button compact" onClick={()=>addStock(s)}>+ 모니터링</button></div>)}</div>}</section>
      {tab==='API 설정'&&<section className="panel credential-panel"><div className="panel-head"><div><div className="panel-kicker">CONNECTION</div><h3>KIS API 인증 정보</h3></div><span className="source-pill"><ShieldCheck size={13}/> DB 저장</span></div><p className="panel-description">한국투자증권 Open API 앱키를 등록하세요. 입력한 시크릿은 저장 후 다시 표시하지 않습니다.</p><div className="credential-status"><span className={credentialInfo.configured?'configured-dot':'unconfigured-dot'}/>{credentialInfo.configured?`등록됨 · App Key ${credentialInfo.appKey}`:'아직 인증 정보가 등록되지 않았습니다.'}</div><form onSubmit={e=>{e.preventDefault();void saveCredentials(new FormData(e.currentTarget))}}><label>투자 환경<select value={environment} onChange={e=>setEnvironment(e.target.value)}><option value="PAPER">모의투자 (PAPER)</option><option value="REAL">실전투자 (REAL)</option></select></label><label>App Key<input name="appKey" autoComplete="off" placeholder={credentialInfo.configured?'새 앱키를 입력하면 교체됩니다':'앱키를 입력하세요'} required/></label><label>App Secret<input name="appSecret" type="password" autoComplete="new-password" placeholder="앱시크릿을 입력하세요" required/></label><button className="primary-button" type="submit"><KeyRound size={15}/> 인증 정보 저장</button></form></section>}
      <footer className="footer"><span>© {new Date().getFullYear()} Marketdesk</span><span>한국투자증권 Open API · {environment} 환경</span><span><i/> 서버 설정에 따라 데이터가 표시됩니다</span></footer></div></main>{message&&<div className="toast" role="status">{message}</div>}</div>
}
function Metric({label,value,change,positive,sub,status}:any){return <section className="metric-card"><div className="metric-top"><span>{label}</span><span className={status?'status-mark':''}>{status?<ShieldCheck size={16}/>:<TrendingUp size={15}/>}</span></div><div className="metric-value">{value}</div><div className={`metric-change ${positive?'positive':''}`}>{positive?<ArrowUpRight size={14}/>:null}{change}</div><div className="metric-sub">{sub}</div></section>}
