import { useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

const EMPTY_FORM = {
  firstName: '', lastName: '', email: '', phone: '',
  dateOfBirth: '', externalUserId: '',
  addressLine1: '', addressLine2: '', city: '',
  state: '', postalCode: '', country: 'US',
}

export default function OnboardingPage() {
  const { session } = useAuth()
  const token = session.accessToken

  const [form, setForm] = useState(EMPTY_FORM)
  const [openAccount, setOpenAccount] = useState(true)
  const [accountCurrency, setAccountCurrency] = useState('USD')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState(null)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setCreated(null)
    try {
      const payload = { ...form }
      Object.keys(payload).forEach((k) => { if (payload[k] === '') delete payload[k] })
      const customer = await api.createCustomer(token, payload)

      await api.writeAudit(token, {
        actor: session.username,
        action: 'CUSTOMER_CREATED',
        resourceType: 'customer',
        resourceId: customer.id,
        customerId: customer.id,
        details: `Admin onboarded new customer: ${customer.email}`,
      })

      let account = null
      if (openAccount) {
        account = await api.createAccount(token, customer.id, accountCurrency)
        await api.writeAudit(token, {
          actor: session.username,
          action: 'ACCOUNT_OPENED',
          resourceType: 'account',
          resourceId: account.id,
          customerId: customer.id,
          details: `Admin opened ${accountCurrency} account during onboarding`,
        })
      }

      setCreated({ customer, account })
      setForm(EMPTY_FORM)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">Onboard New Customer</h1>
      <p className="page-sub">Create a customer profile and optionally open their first account.</p>

      {created && (
        <div className="success-banner" style={{ marginBottom: 20 }}>
          <strong>Customer created successfully.</strong>
          <div style={{ marginTop: 6, fontSize: '0.9rem' }}>
            ID: <code>{created.customer.id}</code> · {created.customer.firstName} {created.customer.lastName} · {created.customer.email}
            {created.account && (
              <span> · Account <code>{created.account.accountNumber}</code> ({created.account.currency}) opened.</span>
            )}
          </div>
        </div>
      )}

      {error && <div className="error" style={{ marginBottom: 14 }}>{error}</div>}

      <form className="form" onSubmit={submit}>
        <div className="grid two" style={{ gap: 20 }}>

          {/* Personal Information */}
          <section className="panel">
            <h2>Personal information</h2>
            <div className="form" style={{ gap: 10 }}>
              <div className="grid two" style={{ gap: 10 }}>
                <label>
                  First name <span className="req">*</span>
                  <input required value={form.firstName} onChange={set('firstName')} placeholder="Jane" />
                </label>
                <label>
                  Last name <span className="req">*</span>
                  <input required value={form.lastName} onChange={set('lastName')} placeholder="Smith" />
                </label>
              </div>
              <label>
                Email address <span className="req">*</span>
                <input required type="email" value={form.email} onChange={set('email')} placeholder="jane.smith@example.com" />
              </label>
              <label>
                Phone
                <input type="tel" value={form.phone} onChange={set('phone')} placeholder="+1 555 000 0000" />
              </label>
              <label>
                Date of birth
                <input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} />
              </label>
              <label>
                Keycloak user ID
                <input value={form.externalUserId} onChange={set('externalUserId')} placeholder="Keycloak sub (optional)" />
              </label>
            </div>
          </section>

          {/* Address */}
          <section className="panel">
            <h2>Address</h2>
            <div className="form" style={{ gap: 10 }}>
              <label>
                Address line 1
                <input value={form.addressLine1} onChange={set('addressLine1')} placeholder="123 Main St" />
              </label>
              <label>
                Address line 2
                <input value={form.addressLine2} onChange={set('addressLine2')} placeholder="Apt 4B" />
              </label>
              <div className="grid two" style={{ gap: 10 }}>
                <label>
                  City
                  <input value={form.city} onChange={set('city')} placeholder="New York" />
                </label>
                <label>
                  State / Province
                  <input value={form.state} onChange={set('state')} placeholder="NY" />
                </label>
              </div>
              <div className="grid two" style={{ gap: 10 }}>
                <label>
                  Postal code
                  <input value={form.postalCode} onChange={set('postalCode')} placeholder="10001" />
                </label>
                <label>
                  Country (2-letter)
                  <input maxLength={2} value={form.country} onChange={set('country')} placeholder="US" />
                </label>
              </div>
            </div>
          </section>
        </div>

        {/* Open account option */}
        <section className="panel" style={{ marginTop: 4 }}>
          <h2>Initial account</h2>
          <label style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', color: 'var(--ink)', fontSize: '0.94rem' }}>
            <input
              type="checkbox"
              checked={openAccount}
              onChange={(e) => setOpenAccount(e.target.checked)}
              style={{ width: 16, height: 16, accentColor: 'var(--sea)' }}
            />
            Open a bank account for this customer during onboarding
          </label>
          {openAccount && (
            <label style={{ display: 'grid', gap: 6, marginTop: 14, color: 'var(--muted)', fontSize: '0.88rem', maxWidth: 200 }}>
              Account currency
              <select value={accountCurrency} onChange={(e) => setAccountCurrency(e.target.value)}>
                <option>USD</option>
                <option>EUR</option>
                <option>GBP</option>
                <option>CAD</option>
                <option>CHF</option>
              </select>
            </label>
          )}
        </section>

        <div className="actions" style={{ marginTop: 4 }}>
          <button className="primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create customer'}
          </button>
          <button type="button" className="secondary" onClick={() => { setForm(EMPTY_FORM); setError(''); setCreated(null) }}>
            Reset
          </button>
        </div>
      </form>
    </div>
  )
}
