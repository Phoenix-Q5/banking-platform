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
  const [accounts, setAccounts] = useState([])
  const [cards, setCards] = useState([])
  const [payments, setPayments] = useState([])
  const [notifications, setNotifications] = useState([])
  const [audit, setAudit] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [note, setNote] = useState('')

  const lookup = async (e) => {
    e?.preventDefault()
    setBusy(true)
    setError('')
    try {
      const matches = await api.listCustomers(token, { email: query })
      const c = matches[0]
      if (!c) {
        setCustomer(null)
        setAccounts([])
        setCards([])
        setPayments([])
        setNotifications([])
        setAudit([])
        setError('No customer found')
        return
      }
      setCustomer(c)
      const [a, cardsRes, pays, notes, events] = await Promise.all([
        api.listAccounts(token, c.id),
        api.listCards(token, c.id),
        api.listPayments(token, c.id),
        api.listNotifications(token, c.id),
        api.listAudit(token, { customerId: c.id }),
      ])
      setAccounts(a)
      setCards(cardsRes)
      setPayments(pays)
      setNotifications(notes)
      setAudit(events)
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
      <p className="page-sub">Look up a customer, review balances and cards, and send support messages.</p>
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
        <>
          <div className="grid three">
            <section className="panel">
              <h2>Profile</h2>
              <div className="stat"><div className="label">Name</div><div className="value" style={{ fontSize: '1.3rem' }}>{customer.firstName} {customer.lastName}</div></div>
              <div className="muted">{customer.email}</div>
              <div className="muted">{customer.phone || 'No phone'}</div>
              <div style={{ marginTop: 10 }}>
                <span className={`badge ${customer.kycStatus}`}>{customer.kycStatus}</span>{' '}
                <span className={`badge ${customer.status}`}>{customer.status}</span>
              </div>
            </section>
            <section className="panel">
              <h2>Accounts</h2>
              {accounts.length === 0 ? <div className="empty">None</div> : accounts.map((a) => (
                <div className="stat" key={a.id}>
                  <div className="label">{a.accountNumber}</div>
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
          </div>

          <div className="grid two" style={{ marginTop: 16 }}>
            <section className="panel">
              <h2>Send support message</h2>
              <form className="form" onSubmit={sendNote}>
                <label>
                  Message
                  <textarea rows={4} value={note} onChange={(e) => setNote(e.target.value)} placeholder="We reviewed your transfer and…" />
                </label>
                <div className="actions"><button className="primary" disabled={busy}>Send to customer</button></div>
              </form>
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
              <h2>Customer notifications</h2>
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
