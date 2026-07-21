import { useEffect, useState } from 'react'
import { accountConsoleUrl, api } from '../api'
import { useAuth } from '../auth'

export default function ProfilePage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const [customer, setCustomer] = useState(null)
  const [form, setForm] = useState({
    phone: '', addressLine1: '', addressLine2: '', city: '', state: '', postalCode: '', country: 'US',
  })
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [pin, setPin] = useState('')
  const [pinConfirm, setPinConfirm] = useState('')

  const load = async () => {
    if (!customerId) {
      setError('No customer profile linked to this login.')
      return
    }
    try {
      const c = await api.getCustomer(token, customerId)
      setCustomer(c)
      setForm({
        phone: c.phone || '',
        addressLine1: c.addressLine1 || '',
        addressLine2: c.addressLine2 || '',
        city: c.city || '',
        state: c.state || '',
        postalCode: c.postalCode || '',
        country: c.country || 'US',
      })
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  const save = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    try {
      const updated = await api.updateCustomer(token, customerId, form)
      setCustomer(updated)
      setOk('Profile updated')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const savePin = async (e) => {
    e.preventDefault()
    setError('')
    setOk('')
    if (!/^\d{4}$/.test(pin)) {
      setError('Support PIN must be exactly 4 digits.')
      return
    }
    if (pin !== pinConfirm) {
      setError('PINs do not match.')
      return
    }
    setBusy(true)
    try {
      await api.setSupportPin(token, customerId, pin)
      setPin('')
      setPinConfirm('')
      setOk('Support PIN saved')
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">Profile</h1>
      <p className="page-sub">Your Harbor Bank identity, contact details, and password settings.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}
      {ok && <div className="banner ok" style={{ marginBottom: 12 }}>{ok}</div>}

      <div className="grid two">
        <section className="panel">
          <h2>Sign-in identity</h2>
          <div className="detail-row">
            <span className="detail-label">Username</span>
            <span className="detail-value"><strong>@{session.username}</strong></span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Email</span>
            <span className="detail-value">{customer?.email || session.email || '—'}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Name</span>
            <span className="detail-value">
              {customer ? `${customer.firstName} ${customer.lastName}` : session.name}
            </span>
          </div>
          <div className="detail-row">
            <span className="detail-label">KYC</span>
            <span className={`badge ${customer?.kycStatus || 'PENDING'}`}>{customer?.kycStatus || '—'}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Status</span>
            <span className={`badge ${customer?.status || ''}`}>{customer?.status || '—'}</span>
          </div>
          <div className="actions" style={{ marginTop: 16 }}>
            <a className="secondary" href={accountConsoleUrl()} target="_blank" rel="noreferrer">
              Change password (Keycloak) →
            </a>
          </div>
          <p className="muted" style={{ marginTop: 10, fontSize: '0.85rem' }}>
            Password changes and recovery are handled by Keycloak. Use “Forgot password?” on the sign-in page for email reset.
          </p>
        </section>

        <section className="panel">
          <h2>Contact & address</h2>
          <form className="form" onSubmit={save}>
            <label>
              Phone
              <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </label>
            <label>
              Address line 1
              <input value={form.addressLine1} onChange={(e) => setForm({ ...form, addressLine1: e.target.value })} />
            </label>
            <label>
              Address line 2
              <input value={form.addressLine2} onChange={(e) => setForm({ ...form, addressLine2: e.target.value })} />
            </label>
            <div className="grid two" style={{ gap: 12 }}>
              <label>
                City
                <input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} />
              </label>
              <label>
                State
                <input value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} />
              </label>
            </div>
            <div className="grid two" style={{ gap: 12 }}>
              <label>
                Postal code
                <input value={form.postalCode} onChange={(e) => setForm({ ...form, postalCode: e.target.value })} />
              </label>
              <label>
                Country
                <input maxLength={2} value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })} />
              </label>
            </div>
            <div className="actions">
              <button className="primary" disabled={busy || !customerId}>{busy ? 'Saving…' : 'Save changes'}</button>
            </div>
          </form>
        </section>
      </div>

      <section className="panel" style={{ marginTop: 16 }}>
        <h2>Support PIN</h2>
        <p className="muted" style={{ marginTop: 0 }}>
          Your secret 4-digit PIN verifies your identity when you call Harbor Bank support.
          {customer?.supportPinSet ? ' A PIN is currently on file — saving a new one replaces it.' : ' You have no PIN on file yet.'}
        </p>
        <form className="form" onSubmit={savePin}>
          <div className="grid two" style={{ gap: 12 }}>
            <label>
              New PIN
              <input
                type="password"
                inputMode="numeric"
                maxLength={4}
                value={pin}
                onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
                placeholder="••••"
              />
            </label>
            <label>
              Confirm PIN
              <input
                type="password"
                inputMode="numeric"
                maxLength={4}
                value={pinConfirm}
                onChange={(e) => setPinConfirm(e.target.value.replace(/\D/g, ''))}
                placeholder="••••"
              />
            </label>
          </div>
          <div className="actions">
            <button className="primary" disabled={busy || !customerId || pin.length !== 4 || pinConfirm.length !== 4}>
              {customer?.supportPinSet ? 'Replace PIN' : 'Set PIN'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
