import { useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

export default function SupportPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const [query, setQuery] = useState('demo.customer@example.com')
  const [customer, setCustomer] = useState(null)
  const [pin, setPin] = useState('')
  const [verified, setVerified] = useState(false)
  const [pinMessage, setPinMessage] = useState('')
  const [accounts, setAccounts] = useState([])
  const [cards, setCards] = useState([])
  const [loans, setLoans] = useState([])
  const [transfers, setTransfers] = useState([])
  const [payments, setPayments] = useState([])
  const [notifications, setNotifications] = useState([])
  const [audit, setAudit] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [note, setNote] = useState('')

  const resetDetails = () => {
    setVerified(false)
    setPin('')
    setPinMessage('')
    setAccounts([])
    setCards([])
    setLoans([])
    setTransfers([])
    setPayments([])
    setNotifications([])
    setAudit([])
  }

  const lookup = async (e) => {
    e?.preventDefault()
    setBusy(true)
    setError('')
    resetDetails()
    try {
      const matches = await api.listCustomers(token, { email: query })
      const c = matches[0]
      if (!c) {
        setCustomer(null)
        setError('No customer found')
        return
      }
      setCustomer(c)
      await api.writeAudit(token, {
        actor: session.username,
        action: 'SUPPORT_LOOKUP',
        resourceType: 'customer',
        resourceId: c.id,
        customerId: c.id,
        details: `Support lookup by ${session.username}`,
      })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const verifyPin = async (e) => {
    e?.preventDefault()
    if (!customer || pin.length !== 4) return
    setBusy(true)
    setError('')
    setPinMessage('')
    try {
      const result = await api.verifySupportPin(token, customer.id, pin)
      if (!result.verified) {
        if (result.locked) {
          const until = result.lockedUntil ? ` until ${new Date(result.lockedUntil).toLocaleTimeString()}` : ''
          setPinMessage(`PIN verification locked${until} after too many failed attempts.`)
        } else {
          setPinMessage(`Incorrect PIN. ${result.attemptsRemaining} attempt(s) remaining.`)
        }
        await api.writeAudit(token, {
          actor: session.username,
          action: 'SUPPORT_PIN_FAILED',
          resourceType: 'customer',
          resourceId: customer.id,
          customerId: customer.id,
          details: `Failed support PIN verification by ${session.username}`,
        })
        return
      }
      setVerified(true)
      setPin('')
      await api.writeAudit(token, {
        actor: session.username,
        action: 'SUPPORT_PIN_VERIFIED',
        resourceType: 'customer',
        resourceId: customer.id,
        customerId: customer.id,
        details: `Customer identity verified via support PIN by ${session.username}`,
      })
      const [a, cardsRes, loansRes, pays, notes, events] = await Promise.all([
        api.listAccounts(token, customer.id),
        api.listCards(token, customer.id),
        api.listLoans(token, customer.id),
        api.listPayments(token, customer.id),
        api.listNotifications(token, customer.id),
        api.listAudit(token, { customerId: customer.id }),
      ])
      setAccounts(a)
      setCards(cardsRes)
      setLoans(loansRes)
      setPayments(pays)
      setNotifications(notes)
      setAudit(events)
      const txnLists = await Promise.all(a.map((acct) => api.listTransactions(token, acct.id)))
      const seen = new Set()
      const merged = []
      for (const txn of txnLists.flat()) {
        if (!seen.has(txn.id)) {
          seen.add(txn.id)
          merged.push(txn)
        }
      }
      merged.sort((x, y) => new Date(y.createdAt) - new Date(x.createdAt))
      setTransfers(merged)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const sendNote = async (e) => {
    e.preventDefault()
    if (!customer || !note.trim()) return
    setBusy(true)
    setError('')
    try {
      await api.createNotification(token, {
        customerId: customer.id,
        channel: 'IN_APP',
        category: 'SUPPORT',
        title: 'Message from Harbor Bank support',
        body: note.trim(),
      })
      await api.writeAudit(token, {
        actor: session.username,
        action: 'SUPPORT_MESSAGE',
        resourceType: 'customer',
        resourceId: customer.id,
        customerId: customer.id,
        details: note.trim(),
      })
      setNote('')
      setNotifications(await api.listNotifications(token, customer.id))
      setAudit(await api.listAudit(token, { customerId: customer.id }))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">Contact center</h1>
      <p className="page-sub">Look up a customer, verify their identity with their secret PIN, then review balances, cards, loans, and transfers.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <section className="panel" style={{ marginBottom: 16 }}>
        <h2>Customer lookup</h2>
        <form className="form" onSubmit={lookup}>
          <label>
            Email
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="customer@example.com" />
          </label>
          <div className="actions">
            <button className="primary" disabled={busy}>Search</button>
          </div>
        </form>
      </section>

      {customer && (
        <section className="panel" style={{ marginBottom: 16 }}>
          <h2>Profile</h2>
          <div className="stat"><div className="label">Name</div><div className="value" style={{ fontSize: '1.3rem' }}>{customer.firstName} {customer.lastName}</div></div>
          <div className="muted">{customer.email}</div>
          <div className="muted">{customer.phone || 'No phone'}</div>
          <div style={{ marginTop: 10 }}>
            <span className={`badge ${customer.kycStatus}`}>{customer.kycStatus}</span>{' '}
            <span className={`badge ${customer.status}`}>{customer.status}</span>
          </div>
        </section>
      )}

      {customer && !verified && (
        <section className="panel" style={{ marginBottom: 16 }}>
          <h2>Identity verification</h2>
          {customer.supportPinSet === false ? (
            <div className="empty">This customer has no support PIN on file. Sensitive details cannot be shown.</div>
          ) : (
            <>
              <p className="muted">Ask the customer for their secret 4-digit PIN before discussing account details.</p>
              <form className="form" onSubmit={verifyPin}>
                <label>
                  Customer PIN
                  <input
                    type="password"
                    inputMode="numeric"
                    maxLength={4}
                    value={pin}
                    onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
                    placeholder="••••"
                  />
                </label>
                <div className="actions">
                  <button className="primary" disabled={busy || pin.length !== 4}>Verify</button>
                </div>
              </form>
              {pinMessage && <div className="error" style={{ marginTop: 10 }}>{pinMessage}</div>}
            </>
          )}
        </section>
      )}

      {customer && verified && (
        <>
          <div className="grid three">
            <section className="panel">
              <h2>Accounts</h2>
              {accounts.length === 0 ? <div className="empty">None</div> : accounts.map((a) => (
                <div className="stat" key={a.id}>
                  <div className="label">{a.accountNumber} <span className={`badge ${a.status}`}>{a.status}</span></div>
                  <div className="value" style={{ fontSize: '1.3rem' }}>{money(a.balance, a.currency)}</div>
                </div>
              ))}
            </section>
            <section className="panel">
              <h2>Cards</h2>
              {cards.length === 0 ? <div className="empty">None</div> : cards.map((c) => (
                <div className="stat" key={c.id}>
                  <div className="label">•••• {c.cardNumberLast4}</div>
                  <div><span className={`badge ${c.status}`}>{c.status}</span></div>
                </div>
              ))}
            </section>
            <section className="panel">
              <h2>Loans</h2>
              {loans.length === 0 ? <div className="empty">None</div> : loans.map((l) => (
                <div className="stat" key={l.id}>
                  <div className="label">{l.productCode} · {l.termMonths} mo · <span className={`badge ${l.status}`}>{l.status}</span></div>
                  <div className="value" style={{ fontSize: '1.3rem' }}>{money(l.outstandingBalance, l.currency)}</div>
                  <div className="muted">of {money(l.principal, l.currency)} · {money(l.monthlyPayment, l.currency)}/mo</div>
                </div>
              ))}
            </section>
          </div>

          <div className="grid two" style={{ marginTop: 16 }}>
            <section className="panel">
              <h2>Recent transfers</h2>
              {transfers.length === 0 ? <div className="empty">None</div> : (
                <table className="table">
                  <thead><tr><th>When</th><th>Amount</th><th>Status</th></tr></thead>
                  <tbody>
                    {transfers.slice(0, 8).map((t) => (
                      <tr key={t.id}>
                        <td>{new Date(t.createdAt).toLocaleString()}</td>
                        <td>{money(t.amount, t.currency)}</td>
                        <td><span className={`badge ${t.status}`}>{t.status}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <h2 style={{ marginTop: 18 }}>Recent payments</h2>
              {payments.length === 0 ? <div className="empty">None</div> : (
                <table className="table">
                  <thead><tr><th>Type</th><th>Amount</th><th>Status</th></tr></thead>
                  <tbody>
                    {payments.slice(0, 5).map((p) => (
                      <tr key={p.id}>
                        <td>{p.paymentType}</td>
                        <td>{money(p.amount, p.currency)}</td>
                        <td><span className={`badge ${p.status}`}>{p.status}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
            <section className="panel">
              <h2>Send support message</h2>
              <form className="form" onSubmit={sendNote}>
                <label>
                  Message
                  <textarea rows={4} value={note} onChange={(e) => setNote(e.target.value)} placeholder="We reviewed your transfer and…" />
                </label>
                <div className="actions"><button className="primary" disabled={busy}>Send to customer</button></div>
              </form>
              <h2 style={{ marginTop: 18 }}>Customer notifications</h2>
              {notifications.length === 0 ? <div className="empty">None</div> : (
                <table className="table">
                  <thead><tr><th>When</th><th>Title</th><th>Status</th></tr></thead>
                  <tbody>
                    {notifications.slice(0, 8).map((n) => (
                      <tr key={n.id}>
                        <td>{new Date(n.createdAt).toLocaleString()}</td>
                        <td>{n.title}</td>
                        <td><span className={`badge ${n.status}`}>{n.status}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <h2 style={{ marginTop: 18 }}>Audit</h2>
              {audit.length === 0 ? <div className="empty">None</div> : (
                <table className="table">
                  <thead><tr><th>When</th><th>Action</th><th>Actor</th></tr></thead>
                  <tbody>
                    {audit.slice(0, 8).map((e) => (
                      <tr key={e.id}>
                        <td>{new Date(e.createdAt).toLocaleString()}</td>
                        <td>{e.action}</td>
                        <td className="muted">{e.actor}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  )
}
