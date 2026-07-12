import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

function txSign(tx, accountId) {
  return tx.toAccountId === accountId ? '+' : '-'
}

function txColor(tx, accountId) {
  return tx.toAccountId === accountId ? 'var(--ok)' : 'var(--danger)'
}

export default function AccountDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { session, isAdmin, isSupport } = useAuth()
  const token = session.accessToken

  const [account, setAccount] = useState(null)
  const [transactions, setTransactions] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = async () => {
    setError('')
    try {
      const [acc, txs] = await Promise.all([
        api.getAccount(token, id),
        api.listTransactions(token, id),
      ])
      setAccount(acc)
      setTransactions(txs)
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [id])

  const copyId = () => navigator.clipboard.writeText(id)

  const statusAction = async (action) => {
    setBusy(true)
    setError('')
    try {
      await api.writeAudit(token, {
        actor: session.username,
        action: `ACCOUNT_${action}`,
        resourceType: 'account',
        resourceId: id,
        customerId: account?.customerId,
        details: `${action} action on account ${account?.accountNumber} by ${session.username}`,
      })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!account && !error) {
    return (
      <div>
        <div className="muted" style={{ padding: '40px 0' }}>Loading account…</div>
      </div>
    )
  }

  if (error && !account) {
    return (
      <div>
        <div className="error" style={{ marginBottom: 14 }}>{error}</div>
        <button className="secondary" onClick={() => navigate(-1)}>← Back</button>
      </div>
    )
  }

  const incoming = transactions.filter((t) => t.toAccountId === id).reduce((s, t) => s + Number(t.amount), 0)
  const outgoing = transactions.filter((t) => t.fromAccountId === id).reduce((s, t) => s + Number(t.amount), 0)

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
        <button className="secondary" style={{ padding: '6px 12px', fontSize: '0.88rem' }} onClick={() => navigate(-1)}>← Back</button>
        <h1 className="page-title" style={{ margin: 0 }}>{account.accountNumber}</h1>
        <span className={`badge ${account.status}`}>{account.status}</span>
      </div>

      {error && <div className="error" style={{ marginBottom: 14 }}>{error}</div>}

      <div className="grid two" style={{ marginBottom: 16 }}>
        {/* Balance card */}
        <section className="panel">
          <h2>Balance</h2>
          <div className="stat">
            <div className="label">Available balance</div>
            <div className="value" style={{ fontSize: '2.4rem' }}>{money(account.balance, account.currency)}</div>
          </div>
          <div className="grid two" style={{ marginTop: 12, gap: 0 }}>
            <div className="stat">
              <div className="label">↓ Total received</div>
              <div style={{ color: 'var(--ok)', fontWeight: 600, marginTop: 4 }}>{money(incoming, account.currency)}</div>
            </div>
            <div className="stat">
              <div className="label">↑ Total sent</div>
              <div style={{ color: 'var(--danger)', fontWeight: 600, marginTop: 4 }}>{money(outgoing, account.currency)}</div>
            </div>
          </div>
        </section>

        {/* Account info */}
        <section className="panel">
          <h2>Account details</h2>
          <div className="detail-row">
            <span className="detail-label">Account number</span>
            <span className="detail-value">{account.accountNumber}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Currency</span>
            <span className="detail-value">{account.currency}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Status</span>
            <span className={`badge ${account.status}`}>{account.status}</span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Account ID</span>
            <span className="detail-value muted" style={{ fontSize: '0.8rem' }}>
              {id.slice(0, 18)}…
              <button
                onClick={copyId}
                style={{ background: 'none', border: 'none', color: 'var(--sea)', cursor: 'pointer', marginLeft: 6, fontSize: '0.78rem' }}
              >
                copy
              </button>
            </span>
          </div>
          <div className="detail-row">
            <span className="detail-label">Opened</span>
            <span className="detail-value muted" style={{ fontSize: '0.85rem' }}>
              {account.createdAt ? new Date(account.createdAt).toLocaleDateString() : '—'}
            </span>
          </div>

          {(isAdmin || isSupport) && (
            <div className="actions" style={{ marginTop: 14 }}>
              <button className="secondary" disabled={busy} onClick={() => statusAction('REVIEWED')}>
                Mark reviewed
              </button>
              <button
                className="secondary"
                style={{ color: 'var(--danger)', borderColor: 'rgba(155,28,28,0.35)' }}
                disabled={busy}
                onClick={() => statusAction('FLAGGED')}
              >
                Flag account
              </button>
            </div>
          )}
        </section>
      </div>

      {/* Transaction history */}
      <section className="panel">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <h2 style={{ margin: 0 }}>Transaction history</h2>
          <span className="muted" style={{ fontSize: '0.85rem' }}>{transactions.length} transaction{transactions.length !== 1 ? 's' : ''}</span>
        </div>
        {transactions.length === 0 ? (
          <div className="empty">No transactions on this account yet.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Direction</th>
                <th>Counterpart</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((t) => {
                const isInbound = t.toAccountId === id
                return (
                  <tr key={t.id}>
                    <td style={{ whiteSpace: 'nowrap' }}>{new Date(t.createdAt).toLocaleString()}</td>
                    <td>
                      <span style={{ color: isInbound ? 'var(--ok)' : 'var(--danger)', fontWeight: 600 }}>
                        {isInbound ? '↓ Received' : '↑ Sent'}
                      </span>
                    </td>
                    <td className="muted" style={{ fontSize: '0.85rem' }}>
                      {isInbound ? t.fromAccountId?.slice(0, 12) : t.toAccountId?.slice(0, 12)}…
                    </td>
                    <td style={{ color: txColor(t, id), fontWeight: 600 }}>
                      {txSign(t, id)}{money(t.amount, t.currency)}
                    </td>
                    <td><span className={`badge ${t.status}`}>{t.status}</span></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
