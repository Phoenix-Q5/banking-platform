import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import AccountCard from '../components/AccountCard'

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
  const [transfer, setTransfer] = useState({
    fromAccountId: '', toAccountId: '', amount: '25.00', currency: 'USD', externalToId: '',
  })

  const load = async () => {
    if (!customerId) {
      setError('No customer profile linked to this login. Register a new account or ask an admin to link your profile.')
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
        setTransfer((t) => ({
          ...t,
          fromAccountId: t.fromAccountId || a[0].id,
          toAccountId: t.toAccountId || (a[1]?.id || ''),
        }))
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
    const toId = transfer.toAccountId || transfer.externalToId
    if (!transfer.fromAccountId || !toId) return
    setBusy(true)
    setError('')
    try {
      await api.transfer(token, {
        fromAccountId: transfer.fromAccountId,
        toAccountId: toId,
        amount: Number(transfer.amount),
        currency: transfer.currency,
      })
      await api.writeAudit(token, {
        actor: session.username,
        action: 'TRANSFER',
        resourceType: 'transaction',
        customerId,
        details: `Transfer ${transfer.amount} ${transfer.currency}`,
      })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const total = accounts.reduce((sum, a) => sum + Number(a.balance || 0), 0)
  const accountLabel = (a) => `${a.accountNumber} (${money(a.balance, a.currency)})`

  return (
    <div>
      <h1 className="page-title">Good day, {customer?.firstName || session.name}</h1>
      <p className="page-sub">
        Signed in as <strong>@{session.username}</strong>
        {session.email ? ` · ${session.email}` : ''} — manage accounts, move funds, and track activity.
      </p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <section style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14, gap: 12, flexWrap: 'wrap' }}>
          <div>
            <h2 style={{ fontFamily: 'Fraunces, Georgia, serif', fontSize: '1.2rem', margin: 0, color: 'var(--navy)' }}>
              Your accounts
            </h2>
            <p style={{ color: 'var(--muted)', fontSize: '0.85rem', margin: '2px 0 0' }}>
              Total: <strong>{money(total)}</strong>
            </p>
          </div>
          <div className="actions" style={{ margin: 0 }}>
            <Link to="/funds" className="secondary" style={{ padding: '10px 14px' }}>Funds</Link>
            <button className="primary" disabled={busy} onClick={openAccount} style={{ whiteSpace: 'nowrap' }}>
              + Open account
            </button>
          </div>
        </div>
        {accounts.length === 0 ? (
          <div className="panel empty" style={{ textAlign: 'center', padding: '28px' }}>
            No accounts yet — open your first account to get started, or{' '}
            <Link to="/accounts/open" style={{ color: 'var(--sea)' }}>use the guided opener</Link>.
          </div>
        ) : (
          <div className="accounts-gallery">
            {accounts.map((a) => (
              <Link to={`/accounts/${a.id}`} key={a.id} style={{ textDecoration: 'none' }}>
                <AccountCard
                  accountNumber={a.accountNumber}
                  currency={a.currency}
                  balance={a.balance}
                  status={a.status}
                />
              </Link>
            ))}
          </div>
        )}
      </section>

      <div className="grid two">
        <section className="panel">
          <h2>Quick transfer</h2>
          <form className="form" onSubmit={sendTransfer}>
            <label>
              From account
              <select
                required
                value={transfer.fromAccountId}
                onChange={(e) => setTransfer({ ...transfer, fromAccountId: e.target.value })}
              >
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>{accountLabel(a)}</option>
                ))}
              </select>
            </label>
            <label>
              To my account
              <select
                value={transfer.toAccountId}
                onChange={(e) => setTransfer({ ...transfer, toAccountId: e.target.value, externalToId: '' })}
              >
                <option value="">Select or paste an external ID below</option>
                {accounts
                  .filter((a) => a.id !== transfer.fromAccountId)
                  .map((a) => (
                    <option key={a.id} value={a.id}>{accountLabel(a)}</option>
                  ))}
              </select>
            </label>
            {!transfer.toAccountId && (
              <label>
                Or destination account ID
                <input
                  value={transfer.externalToId}
                  onChange={(e) => setTransfer({ ...transfer, externalToId: e.target.value })}
                  placeholder="UUID of another account"
                />
              </label>
            )}
            <label>
              Amount
              <input
                required
                type="number"
                min="0.01"
                step="0.01"
                value={transfer.amount}
                onChange={(e) => setTransfer({ ...transfer, amount: e.target.value })}
              />
            </label>
            <div className="actions">
              <button className="primary" disabled={busy || !accounts[0]}>Send</button>
              <Link to="/funds" className="secondary" style={{ padding: '10px 14px' }}>More funding options</Link>
            </div>
          </form>
        </section>

        <section className="panel">
          <h2>Shortcuts</h2>
          <div className="shortcut-grid">
            <Link to="/funds">Deposit funds</Link>
            <Link to="/payments">Pay someone</Link>
            <Link to="/cards">Manage cards</Link>
            <Link to="/loans">Apply for a loan</Link>
            <Link to="/profile">Profile & password</Link>
            <Link to="/notifications">Alerts</Link>
          </div>
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
