import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

export default function DashboardPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const [customer, setCustomer] = useState(null)
  const [accounts, setAccounts] = useState([])
  const [transactions, setTransactions] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [transfer, setTransfer] = useState({ toAccountId: '', amount: '25.00', currency: 'USD' })

  const load = async () => {
    if (!customerId) {
      setError('No customer profile linked to this login. Ask an admin to create one in customer-service.')
      return
    }
    try {
      const [c, a] = await Promise.all([
        api.getCustomer(token, customerId),
        api.listAccounts(token, customerId),
      ])
      setCustomer(c)
      setAccounts(a)
      if (a[0]) {
        const tx = await api.listTransactions(token, a[0].id)
        setTransactions(tx)
      } else {
        setTransactions([])
      }
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  const openAccount = async () => {
    setBusy(true)
    setError('')
    try {
      await api.createAccount(token, customerId, 'USD')
      await api.writeAudit(token, {
        actor: session.username,
        action: 'ACCOUNT_OPENED',
        resourceType: 'account',
        customerId,
        details: 'Customer opened a new USD account from UI',
      })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const sendTransfer = async (e) => {
    e.preventDefault()
    if (!accounts[0]) return
    setBusy(true)
    setError('')
    try {
      await api.transfer(token, {
        fromAccountId: accounts[0].id,
        toAccountId: transfer.toAccountId,
        amount: Number(transfer.amount),
        currency: transfer.currency,
      })
      await api.writeAudit(token, {
        actor: session.username,
        action: 'TRANSFER',
        resourceType: 'transaction',
        customerId,
        details: `Transfer ${transfer.amount} ${transfer.currency} to ${transfer.toAccountId}`,
      })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const total = accounts.reduce((sum, a) => sum + Number(a.balance || 0), 0)

  return (
    <div>
      <h1 className="page-title">Good day, {customer?.firstName || session.name}</h1>
      <p className="page-sub">Accounts, recent activity, and quick transfers for your Harbor Bank profile.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <div className="grid two">
        <section className="panel">
          <h2>Balances</h2>
          <div className="stat">
            <div className="label">Total available</div>
            <div className="value">{money(total)}</div>
          </div>
          {accounts.length === 0 && <div className="empty">No accounts yet.</div>}
          {accounts.map((a) => (
            <div className="stat" key={a.id}>
              <div className="label">{a.accountNumber} · <span className={`badge ${a.status}`}>{a.status}</span></div>
              <div className="value">{money(a.balance, a.currency)}</div>
            </div>
          ))}
          <div className="actions">
            <button className="primary" disabled={busy} onClick={openAccount}>Open account</button>
          </div>
        </section>

        <section className="panel">
          <h2>Quick transfer</h2>
          <form className="form" onSubmit={sendTransfer}>
            <label>
              From account
              <select disabled value={accounts[0]?.id || ''}>
                {accounts.map((a) => <option key={a.id} value={a.id}>{a.accountNumber} ({money(a.balance, a.currency)})</option>)}
              </select>
            </label>
            <label>
              To account ID
              <input required value={transfer.toAccountId} onChange={(e) => setTransfer({ ...transfer, toAccountId: e.target.value })} placeholder="UUID of destination account" />
            </label>
            <label>
              Amount
              <input required type="number" min="0.01" step="0.01" value={transfer.amount} onChange={(e) => setTransfer({ ...transfer, amount: e.target.value })} />
            </label>
            <div className="actions">
              <button className="primary" disabled={busy || !accounts[0]}>Send</button>
            </div>
          </form>
          <p className="muted" style={{ marginTop: 10, fontSize: '0.85rem' }}>
            Tip: open a second account (or use another customer account ID) as the destination.
          </p>
        </section>
      </div>

      <section className="panel" style={{ marginTop: 16 }}>
        <h2>Recent transfers</h2>
        {transactions.length === 0 ? (
          <div className="empty">No transfers yet.</div>
        ) : (
          <table className="table">
            <thead>
              <tr><th>When</th><th>From</th><th>To</th><th>Amount</th><th>Status</th></tr>
            </thead>
            <tbody>
              {transactions.slice(0, 10).map((t) => (
                <tr key={t.id}>
                  <td>{new Date(t.createdAt).toLocaleString()}</td>
                  <td className="muted">{t.fromAccountId?.slice(0, 8)}…</td>
                  <td className="muted">{t.toAccountId?.slice(0, 8)}…</td>
                  <td>{money(t.amount, t.currency)}</td>
                  <td><span className={`badge ${t.status}`}>{t.status}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
