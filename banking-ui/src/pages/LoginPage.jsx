import { useState } from 'react'
import { Link } from 'react-router-dom'
import { forgotPasswordUrl } from '../api'
import { useAuth } from '../auth'

export default function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('demo.customer')
  const [password, setPassword] = useState('password')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const onSubmit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await login(username, password)
    } catch (err) {
      setError(err.message || 'Unable to sign in')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="app-shell">
      <div className="hero">
        <div className="hero-copy">
          <div className="brand" style={{ marginBottom: 18 }}>Harbor <span>Bank</span></div>
          <h1>Modern banking</h1>
          <p>
            Sign in to move money, manage cards and loans, and operate the contact center —
            all against the same production-shaped platform the ops agent monitors.
          </p>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Link
              to="/register"
              className="btn"
              style={{ padding: '12px 22px', fontSize: '0.95rem' }}
            >
              Open an account →
            </Link>
            <Link
              to="/card-offers"
              style={{
                alignSelf: 'center', color: 'var(--sea)', fontSize: '0.93rem',
                textDecoration: 'underline', textUnderlineOffset: 3,
              }}
            >
              Browse card offers
            </Link>
          </div>
        </div>
        <div>
          <div className="hero-visual" aria-hidden="true" />
          <div className="panel login-card" style={{ marginTop: 16 }}>
            <h2>Sign in</h2>
            <form className="form" onSubmit={onSubmit}>
              <label>
                Username
                <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
              </label>
              <label>
                Password
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
              </label>
              <div style={{ textAlign: 'right', marginTop: -4, marginBottom: 4 }}>
                <a
                  href={forgotPasswordUrl()}
                  style={{ color: 'var(--sea)', fontSize: '0.85rem', textDecoration: 'underline', textUnderlineOffset: 2 }}
                >
                  Forgot password?
                </a>
              </div>
              <div className="actions">
                <button className="primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
                <button type="button" className="secondary" onClick={() => { setUsername('demo.customer'); setPassword('password') }}>Use customer</button>
                <button type="button" className="secondary" onClick={() => { setUsername('demo.admin'); setPassword('password') }}>Use admin</button>
              </div>
              {error && <div className="error">{error}</div>}
              <p className="muted" style={{ marginTop: 10, fontSize: '0.85rem' }}>
                Demo: <code>demo.customer</code> / <code>password</code> — or register a new username.
              </p>
              <p style={{ marginTop: 12, fontSize: '0.85rem', color: 'var(--muted)', textAlign: 'center' }}>
                New here?{' '}
                <Link to="/register" style={{ color: 'var(--sea)', textDecoration: 'underline', textUnderlineOffset: 2 }}>
                  Create a free account
                </Link>
              </p>
            </form>
          </div>
        </div>
      </div>
    </div>
  )
}
