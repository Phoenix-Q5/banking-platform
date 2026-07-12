import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

const CURRENCIES = [
  { code: 'USD', label: 'US Dollar', flag: '🇺🇸' },
  { code: 'EUR', label: 'Euro', flag: '🇪🇺' },
  { code: 'GBP', label: 'British Pound', flag: '🇬🇧' },
  { code: 'CAD', label: 'Canadian Dollar', flag: '🇨🇦' },
  { code: 'CHF', label: 'Swiss Franc', flag: '🇨🇭' },
]

const ACCOUNT_TYPES = [
  {
    id: 'checking',
    label: 'Checking',
    description: 'Everyday spending and bill payments',
    icon: '💳',
  },
  {
    id: 'savings',
    label: 'Savings',
    description: 'Grow your money with 4.2% APY',
    icon: '🏦',
  },
  {
    id: 'fx',
    label: 'Foreign Currency',
    description: 'Hold and send in multiple currencies',
    icon: '🌍',
  },
]

export default function OpenAccountPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const navigate = useNavigate()
  const location = useLocation()

  const prefill = location.state || {}

  const [step, setStep] = useState(1)
  const [accountType, setAccountType] = useState('checking')
  const [currency, setCurrency] = useState(prefill.currency || 'USD')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [opened, setOpened] = useState(null)

  const selectedCurrency = CURRENCIES.find((c) => c.code === currency) || CURRENCIES[0]

  const handleConfirm = async () => {
    setBusy(true)
    setError('')
    try {
      const account = await api.createAccount(token, customerId, currency)
      await api.writeAudit(token, {
        actor: session.username,
        action: 'ACCOUNT_OPENED',
        resourceType: 'account',
        resourceId: account.id,
        customerId,
        details: `Opened ${accountType} account in ${currency}`,
      })
      setOpened(account)
      setStep(4)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (step === 4 && opened) {
    return (
      <div>
        <h1 className="page-title">Account opened!</h1>
        <p className="page-sub">Your new account is ready to use.</p>
        <section className="panel" style={{ maxWidth: 520 }}>
          <div className="open-success-icon">✓</div>
          <div className="open-success-title">
            {prefill.label || ACCOUNT_TYPES.find((t) => t.id === accountType)?.label} Account
          </div>
          <div className="open-success-number">{opened.accountNumber}</div>
          <div className="open-success-meta">
            <span className="badge ACTIVE">ACTIVE</span>
            <span style={{ marginLeft: 8, color: 'var(--muted)', fontSize: '0.9rem' }}>{selectedCurrency.flag} {currency}</span>
          </div>
          <div className="actions" style={{ marginTop: 24, justifyContent: 'center' }}>
            <button className="primary" onClick={() => navigate('/')}>Go to overview</button>
            <button className="secondary" onClick={() => { setStep(1); setOpened(null) }}>Open another</button>
          </div>
        </section>
      </div>
    )
  }

  return (
    <div>
      <h1 className="page-title">Open a New Account</h1>
      <p className="page-sub">Choose your account type and currency — it takes seconds.</p>

      <div className="step-bar">
        {['Account type', 'Currency', 'Review'].map((label, i) => (
          <div key={label} className={`step-item ${step === i + 1 ? 'active' : step > i + 1 ? 'done' : ''}`}>
            <div className="step-dot">{step > i + 1 ? '✓' : i + 1}</div>
            <div className="step-label">{label}</div>
          </div>
        ))}
      </div>

      {error && <div className="error" style={{ marginBottom: 14 }}>{error}</div>}

      {step === 1 && (
        <div>
          <div className="product-grid three" style={{ maxWidth: 780 }}>
            {ACCOUNT_TYPES.map((type) => (
              <div
                key={type.id}
                className={`selectable-card ${accountType === type.id ? 'selected' : ''}`}
                onClick={() => setAccountType(type.id)}
              >
                <div className="selectable-icon">{type.icon}</div>
                <div className="selectable-label">{type.label}</div>
                <div className="selectable-desc">{type.description}</div>
              </div>
            ))}
          </div>
          <div className="actions" style={{ marginTop: 20 }}>
            <button className="primary" onClick={() => setStep(2)}>Continue</button>
            <button className="secondary" onClick={() => navigate(-1)}>Back</button>
          </div>
        </div>
      )}

      {step === 2 && (
        <div>
          <div className="currency-grid">
            {CURRENCIES.map((c) => (
              <div
                key={c.code}
                className={`selectable-card ${currency === c.code ? 'selected' : ''}`}
                onClick={() => setCurrency(c.code)}
              >
                <div className="selectable-icon" style={{ fontSize: '2rem' }}>{c.flag}</div>
                <div className="selectable-label">{c.code}</div>
                <div className="selectable-desc">{c.label}</div>
              </div>
            ))}
          </div>
          <div className="actions" style={{ marginTop: 20 }}>
            <button className="primary" onClick={() => setStep(3)}>Continue</button>
            <button className="secondary" onClick={() => setStep(1)}>Back</button>
          </div>
        </div>
      )}

      {step === 3 && (
        <div>
          <section className="panel" style={{ maxWidth: 520 }}>
            <h2>Review your account</h2>
            <div className="review-row">
              <span className="review-label">Account type</span>
              <span className="review-value">
                {ACCOUNT_TYPES.find((t) => t.id === accountType)?.icon}{' '}
                {ACCOUNT_TYPES.find((t) => t.id === accountType)?.label}
              </span>
            </div>
            <div className="review-row">
              <span className="review-label">Currency</span>
              <span className="review-value">{selectedCurrency.flag} {currency} — {selectedCurrency.label}</span>
            </div>
            <div className="review-row">
              <span className="review-label">Opening balance</span>
              <span className="review-value">$0.00</span>
            </div>
            <div className="review-row">
              <span className="review-label">Monthly fee</span>
              <span className="review-value" style={{ color: 'var(--ok)' }}>None</span>
            </div>
            <div className="actions" style={{ marginTop: 20 }}>
              <button className="primary" disabled={busy} onClick={handleConfirm}>
                {busy ? 'Opening…' : 'Open account'}
              </button>
              <button className="secondary" onClick={() => setStep(2)}>Back</button>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
