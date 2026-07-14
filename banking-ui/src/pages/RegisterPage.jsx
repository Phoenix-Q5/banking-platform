import { useState } from 'react'
import { Link } from 'react-router-dom'
import { publicApi } from '../api'

const STEPS = ['Personal info', 'Address', 'Done']

const EMPTY = {
  firstName: '', lastName: '', email: '', phone: '', dateOfBirth: '',
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
  const [step, setStep] = useState(0)
  const [form, setForm] = useState(EMPTY)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState(null)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const nextStep = (e) => {
    e.preventDefault()
    setError('')
    setStep((s) => s + 1)
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const payload = { ...form }
      Object.keys(payload).forEach((k) => { if (payload[k] === '') delete payload[k] })
      const customer = await publicApi.registerCustomer(payload)
      setCreated(customer)
      setStep(2)
    } catch (err) {
      setError(err.message || 'Registration failed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="app-shell">
      {/* Minimal public header */}
      <header className="topbar">
        <Link to="/" className="brand">Harbor <span>Bank</span></Link>
        <nav className="nav">
          <Link to="/card-offers">Card offers</Link>
          <Link to="/login">Sign in</Link>
        </nav>
      </header>

      <div style={{ maxWidth: 640, margin: '0 auto' }}>
        <h1 className="page-title">Open your account</h1>
        <p className="page-sub">Join Harbor Bank in minutes — no paperwork, no branch visit.</p>

        <StepBar current={step} />

        {/* ── Step 0: Personal information ── */}
        {step === 0 && (
          <form className="form" onSubmit={nextStep}>
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
              <Link to="/" style={{ alignSelf: 'center', color: 'var(--muted)', fontSize: '0.9rem' }}>
                Already have an account? Sign in
              </Link>
            </div>
          </form>
        )}

        {/* ── Step 1: Address ── */}
        {step === 1 && (
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
              <button type="button" className="secondary" onClick={() => { setStep(0); setError('') }}>
                ← Back
              </button>
            </div>
          </form>
        )}

        {/* ── Step 2: Success ── */}
        {step === 2 && created && (
          <section className="panel" style={{ textAlign: 'center', padding: '36px 28px' }}>
            <div className="open-success-icon">✓</div>
            <div className="open-success-title">Welcome, {created.firstName}!</div>
            <p style={{ color: 'var(--muted)', marginTop: 10, lineHeight: 1.6 }}>
              Your Harbor Bank profile has been created. Sign in to open your first account,
              explore card offers, and start banking.
            </p>
            <div style={{
              background: 'var(--sand)', borderRadius: 4, padding: '10px 14px',
              margin: '18px auto', maxWidth: 320, fontSize: '0.88rem', color: 'var(--muted)',
            }}>
              Customer ID: <code style={{ color: 'var(--sea-deep)', fontSize: '0.85rem' }}>{created.id}</code>
            </div>
            <div className="actions" style={{ justifyContent: 'center', marginTop: 20 }}>
              <Link to="/login" className="btn">Sign in to your account →</Link>
              <Link to="/card-offers" style={{ alignSelf: 'center', color: 'var(--sea)', fontSize: '0.92rem' }}>
                Browse card offers
              </Link>
            </div>
          </section>
        )}
      </div>
    </div>
  )
}
