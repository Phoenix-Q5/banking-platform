import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { publicApi } from '../api'
import { useAuth } from '../auth'

const STEPS = ['Personal info', 'Credentials', 'Address', 'Done']

const EMPTY = {
  firstName: '', lastName: '', email: '', phone: '', dateOfBirth: '',
  username: '', password: '', confirmPassword: '',
  addressLine1: '', addressLine2: '', city: '', state: '', postalCode: '', country: 'US',
}

function StepBar({ current }) {
  return (
    <div className="step-bar" style={{ marginBottom: 28 }}>
      {STEPS.map((label, i) => {
        const state = i < current ? 'done' : i === current ? 'active' : ''
        return (
          <div key={label} className={`step-item ${state}`}>
            <div className="step-dot">{i < current ? '✓' : i + 1}</div>
            <span className="step-label">{label}</span>
          </div>
        )
      })}
    </div>
  )
}

export default function RegisterPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState(EMPTY)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState(null)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const nextFromPersonal = (e) => {
    e.preventDefault()
    setError('')
    setStep(1)
  }

  const nextFromCredentials = (e) => {
    e.preventDefault()
    setError('')
    if (form.password !== form.confirmPassword) {
      setError('Passwords do not match')
      return
    }
    if (form.password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }
    if (!/^[a-zA-Z0-9._-]{3,64}$/.test(form.username.trim())) {
      setError('Username must be 3–64 characters (letters, numbers, . _ -)')
      return
    }
    setStep(2)
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const payload = {
        username: form.username.trim().toLowerCase(),
        password: form.password,
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        phone: form.phone || undefined,
        dateOfBirth: form.dateOfBirth || undefined,
        addressLine1: form.addressLine1 || undefined,
        addressLine2: form.addressLine2 || undefined,
        city: form.city || undefined,
        state: form.state || undefined,
        postalCode: form.postalCode || undefined,
        country: form.country || undefined,
      }
      Object.keys(payload).forEach((k) => { if (payload[k] === '') delete payload[k] })
      const customer = await publicApi.registerCustomer(payload)
      setCreated(customer)
      setStep(3)
      try {
        await login(payload.username, form.password)
        setTimeout(() => navigate('/'), 800)
      } catch {
        /* profile created; user can sign in manually */
      }
    } catch (err) {
      setError(err.message || 'Registration failed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/" className="brand">Harbor <span>Bank</span></Link>
        <nav className="nav">
          <Link to="/card-offers">Card offers</Link>
          <Link to="/login">Sign in</Link>
        </nav>
      </header>

      <div style={{ maxWidth: 640, margin: '0 auto' }}>
        <h1 className="page-title">Open your account</h1>
        <p className="page-sub">Create your Harbor Bank username and profile — then start banking.</p>

        <StepBar current={step} />

        {step === 0 && (
          <form className="form" onSubmit={nextFromPersonal}>
            <section className="panel">
              <h2>Personal information</h2>
              <div className="grid two" style={{ gap: 12 }}>
                <label>
                  First name <span className="req">*</span>
                  <input required value={form.firstName} onChange={set('firstName')} placeholder="Jane" />
                </label>
                <label>
                  Last name <span className="req">*</span>
                  <input required value={form.lastName} onChange={set('lastName')} placeholder="Smith" />
                </label>
              </div>
              <label style={{ marginTop: 12 }}>
                Email address <span className="req">*</span>
                <input required type="email" value={form.email} onChange={set('email')} placeholder="jane.smith@example.com" />
              </label>
              <label style={{ marginTop: 12 }}>
                Phone number
                <input type="tel" value={form.phone} onChange={set('phone')} placeholder="+1 555 000 0000" />
              </label>
              <label style={{ marginTop: 12 }}>
                Date of birth
                <input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} />
              </label>
            </section>
            {error && <div className="error">{error}</div>}
            <div className="actions">
              <button className="primary" type="submit">Continue →</button>
              <Link to="/login" style={{ alignSelf: 'center', color: 'var(--muted)', fontSize: '0.9rem' }}>
                Already have an account? Sign in
              </Link>
            </div>
          </form>
        )}

        {step === 1 && (
          <form className="form" onSubmit={nextFromCredentials}>
            <section className="panel">
              <h2>Choose your login</h2>
              <p className="muted" style={{ marginTop: 0, marginBottom: 14, fontSize: '0.9rem' }}>
                This username and password are how you sign in to Harbor Bank.
              </p>
              <label>
                Username <span className="req">*</span>
                <input
                  required
                  autoComplete="username"
                  value={form.username}
                  onChange={set('username')}
                  placeholder="jane.smith"
                  pattern="[a-zA-Z0-9._\-]{3,64}"
                />
              </label>
              <label style={{ marginTop: 12 }}>
                Password <span className="req">*</span>
                <input
                  required
                  type="password"
                  autoComplete="new-password"
                  minLength={8}
                  value={form.password}
                  onChange={set('password')}
                  placeholder="At least 8 characters"
                />
              </label>
              <label style={{ marginTop: 12 }}>
                Confirm password <span className="req">*</span>
                <input
                  required
                  type="password"
                  autoComplete="new-password"
                  minLength={8}
                  value={form.confirmPassword}
                  onChange={set('confirmPassword')}
                />
              </label>
            </section>
            {error && <div className="error">{error}</div>}
            <div className="actions">
              <button className="primary" type="submit">Continue →</button>
              <button type="button" className="secondary" onClick={() => { setStep(0); setError('') }}>← Back</button>
            </div>
          </form>
        )}

        {step === 2 && (
          <form className="form" onSubmit={submit}>
            <section className="panel">
              <h2>Your address</h2>
              <label>
                Address line 1
                <input value={form.addressLine1} onChange={set('addressLine1')} placeholder="123 Main St" />
              </label>
              <label style={{ marginTop: 12 }}>
                Address line 2
                <input value={form.addressLine2} onChange={set('addressLine2')} placeholder="Apt 4B (optional)" />
              </label>
              <div className="grid two" style={{ gap: 12, marginTop: 12 }}>
                <label>
                  City
                  <input value={form.city} onChange={set('city')} placeholder="New York" />
                </label>
                <label>
                  State / Province
                  <input value={form.state} onChange={set('state')} placeholder="NY" />
                </label>
              </div>
              <div className="grid two" style={{ gap: 12, marginTop: 12 }}>
                <label>
                  Postal code
                  <input value={form.postalCode} onChange={set('postalCode')} placeholder="10001" />
                </label>
                <label>
                  Country (2-letter)
                  <input maxLength={2} value={form.country} onChange={set('country')} placeholder="US" />
                </label>
              </div>
            </section>
            {error && <div className="error">{error}</div>}
            <div className="actions">
              <button className="primary" type="submit" disabled={busy}>
                {busy ? 'Creating account…' : 'Create my account'}
              </button>
              <button type="button" className="secondary" onClick={() => { setStep(1); setError('') }}>← Back</button>
            </div>
          </form>
        )}

        {step === 3 && created && (
          <section className="panel" style={{ textAlign: 'center', padding: '36px 28px' }}>
            <div className="open-success-icon">✓</div>
            <div className="open-success-title">Welcome, {created.firstName}!</div>
            <p style={{ color: 'var(--muted)', marginTop: 10, lineHeight: 1.6 }}>
              Your login <strong>@{created.externalUserId || form.username}</strong> is ready.
              Signing you in…
            </p>
            <div className="actions" style={{ justifyContent: 'center', marginTop: 20 }}>
              <Link to="/" className="btn">Go to Overview →</Link>
              <Link to="/login" style={{ alignSelf: 'center', color: 'var(--sea)', fontSize: '0.92rem' }}>
                Sign in manually
              </Link>
            </div>
          </section>
        )}
      </div>
    </div>
  )
}
