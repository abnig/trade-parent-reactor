import { useEffect, useRef, useState } from 'react'
import BrokerAccounts from './components/BrokerAccounts'
import MutualFunds from './components/MutualFunds'
import MutualFundTransactions from './components/MutualFundTransactions'
import MutualFundValues from './components/MutualFundValues'
import Analytics from './components/Analytics'
import AuthLinks from './components/AuthLinks'
import Register from './components/Register'
import Login from './components/Login'
import ResetPassword from './components/ResetPassword'
import ForgotUsername from './components/ForgotUsername'
import Profile from './components/Profile'
import api from './api/api'

const tabs = [
  ['brokers', 'Broker Accounts'], ['funds', 'Mutual Funds'],
  ['transactions', 'Transactions'], ['values', 'Fund Values'], ['analytics', 'Analytics']
]

export default function App() {
  const sessionGeneration = useRef(0)
  const [user, setUser] = useState(null)
  const [checking, setChecking] = useState(true)
  const [path, setPath] = useState(window.location.pathname)
  const [activeTab, setActiveTab] = useState('brokers')
  const [error, setError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)

  function navigate(next) {
    window.history.pushState(null, '', next)
    setPath(next)
    setError('')
  }

  useEffect(() => {
    let active = true
    async function checkSession() {
      const current = ++sessionGeneration.current
      try {
        const account = await api.auth.me()
        if (active && current === sessionGeneration.current) { setUser(account); setError('') }
      } catch (failure) {
        if (active && current === sessionGeneration.current) {
          setUser(null)
          if (failure.status !== 401) setError('Unable to check your session. Refresh to try again.')
        }
      } finally {
        if (active && current === sessionGeneration.current) setChecking(false)
      }
    }
    function expired() { sessionGeneration.current++; setUser(null); setActiveTab('brokers'); setChecking(false) }
    function popState() { setPath(window.location.pathname) }
    checkSession()
    window.addEventListener('popstate', popState)
    window.addEventListener('session-expired', expired)
    window.addEventListener('focus', checkSession)
    // Sync session changes between tabs without storing authentication data in the browser.
    const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('trade-session') : null
    if (channel) channel.onmessage = checkSession
    const timer = window.setInterval(checkSession, 60000)
    return () => {
      active = false
      window.removeEventListener('popstate', popState)
      window.removeEventListener('session-expired', expired)
      window.removeEventListener('focus', checkSession)
      window.clearInterval(timer)
      channel?.close()
    }
  }, [])

  function notifySessionChange() {
    if (typeof BroadcastChannel !== 'undefined') {
      const channel = new BroadcastChannel('trade-session')
      channel.postMessage('changed')
      channel.close()
    }
  }

  async function loggedIn() {
    sessionGeneration.current++
    const account = await api.auth.me()
    setUser(account)
    setActiveTab('brokers')
    navigate('/')
    notifySessionChange()
  }

  async function logout() {
    setLoggingOut(true)
    setError('')
    try {
      await api.auth.logout()
      sessionGeneration.current++
      setUser(null)
      setActiveTab('brokers')
      navigate('/')
      notifySessionChange()
    } catch (failure) {
      setError(failure.message)
      if (failure.status === 401) { setUser(null); navigate('/'); notifySessionChange() }
    } finally { setLoggingOut(false) }
  }

  return <AppView user={user} checking={checking} path={path} activeTab={activeTab} error={error}
    loggingOut={loggingOut} navigate={navigate} logout={logout} loggedIn={loggedIn} setActiveTab={setActiveTab} />
}

export function AppView({ user, checking, path, activeTab, error, loggingOut, navigate, logout, loggedIn, setActiveTab }) {
  return (
    <div className="app-shell">
      <header className="topbar">
        <div><div className="eyebrow">TRADE PLATFORM</div><h1>Trade Management</h1></div>
        {!checking && (user ? (
          <div className="auth-links">
            <button type="button" className="header-profile" aria-label={`${user.username}: Profile settings`}
              aria-current={activeTab === 'profile' ? 'page' : undefined}
              onClick={() => { setActiveTab('profile'); navigate('/') }}>
              <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                <circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" />
              </svg>
              <span><strong>{user.username}</strong><span className="header-profile-caption">Profile settings</span></span>
              <span aria-hidden="true">›</span>
            </button>
            <button className="header-logout" onClick={logout} disabled={loggingOut}>{loggingOut ? 'Logging out…' : 'Logout'}</button>
          </div>
        ) : <AuthLinks registerUrl="/register" loginUrl="/login" onNavigate={navigate} />)}
      </header>
      {!checking && user && (
        <nav className="tabs" aria-label="Portfolio">
          {tabs.map(([key, label]) => <button key={key} className={activeTab === key ? 'tab active' : 'tab'}
            onClick={() => { setActiveTab(key); navigate('/') }}>{label}</button>)}
        </nav>
      )}
      <main className="content">
        {error && <div className="error" role="alert">{error}</div>}
        {checking ? <p role="status">Checking session…</p> : user ? (
          <div key={user.id}>
            {activeTab === 'brokers' && <BrokerAccounts />}
            {activeTab === 'funds' && <MutualFunds />}
            {activeTab === 'transactions' && <MutualFundTransactions />}
            {activeTab === 'values' && <MutualFundValues />}
            {activeTab === 'analytics' && <Analytics />}
            {activeTab === 'profile' && <Profile />}
          </div>
        ) : (
          <>
            {path === '/register' && <Register />}
            {path === '/login' && <Login onLoggedIn={loggedIn} />}
            {path === '/reset-password' && <ResetPassword />}
            {path === '/forgot-username' && <ForgotUsername />}
          </>
        )}
      </main>
    </div>
  )
}
