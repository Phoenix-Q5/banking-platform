import { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

function txColor(tx, accountId) {
  return tx.toAccountId === accountId ? 'var(--ok)' : 'var(--danger)'
}

function txSign(tx, accountId) {
  return tx.toAccountId === accountId ? '+' : '-'
}

function toCsv(rows) {
  const header = ['date', 'direction', 'counterpart', 'amount', 'currency', 'status', 'id']
  const lines = [header.join(',')]
  for (const r of rows) {
    lines.push([
      r.date, r.direction, r.counterpart, r.amount, r.currency, r.status, r.id,
    ].map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','))
  }
  return lines.join('\n')
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
  const [filter, setFilter] = useState({ direction: 'ALL', from: '', to: '' })

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

  const filtered = useMemo(() => {
    return transactions.filter((t) => {
      const isInbound = t.toAccountId === id
      if (filter.direction === 'IN' && !isInbound) return false
      if (filter.direction === 'OUT' && isInbound) return false
      const when = new Date(t.createdAt).getTime()
      if (filter.from) {
        const from = new Date(filter.from).getTime()
        if (when < from) return false
      }
      if (filter.to) {
        const to = new Date(filter.to)
        to.setHours(23, 59, 59, 999)
        if (when > to.getTime()) return false
      }
      return true
    })
  }, [transactions, filter, id])

  const copyId = () => navigator.clipboard.writeText(id)

  const exportStatement = () => {
    const rows = filtered.map((t) => {
      const isInbound = t.toAccountId === id
      return {
        date: new Date(t.createdAt).toISOString(),
        direction: isInbound ? 'IN' : 'OUT',
        counterpart: isInbound ? t.fromAccountId : t.toAccountId,
        amount: t.amount,
        currency: t.currency,
        status: t.status,
        id: t.id,
      }
    })
    const blob = new Blob([toCsv(rows)], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `harbor-${account?.accountNumber || id}-statement.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

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

  const incoming = filtered.filter((t) => t.toAccountId === id).reduce((s, t) => s + Number(t.amount), 0)
  const outgoing = filtered.filter((t) => t.fromAccountId === id).reduce((s, t) => s + Number(t.amount), 0)

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20, flexWrap: 'wrap' }}>
        <button className="secondary" style={{ padding: '6px 12px', fontSize: '0.88rem' }} onClick={() => navigate(-1)}>← Back</button>
        <h1 className="page-title" style={{ margin: 0 }}>{account.accountNumber}</h1>
        <span className={`badge ${account.status}`}>{account.status}</span>
        <Link to="/funds" className="secondary" style={{ marginLeft: 'auto', padding: '6px 12px', fontSize: '0.88rem' }}>
          Deposit / transfer
        </Link>
      </div>

      {error && <div className="error" style={{ marginBottom: 14 }}>{error}</div>}

      <div className="grid two" style={{ marginBottom: 16 }}>
        <section className="panel">
          <h2>Balance</h2>
          <div className="stat">
            <div className="label">Available balance</div>
            <div className="value" style={{ fontSize: '2.4rem' }}>{money(account.balance, account.currency)}</div>
          </div>
          <div className="grid two" style={{ marginTop: 12, gap: 0 }}>
            <div className="stat">
              <div className="label">↓ In (filtered)</div>
              <div style={{ color: 'var(--ok)', fontWeight: 600, marginTop: 4 }}>{money(incoming, account.currency)}</div>
            </div>
            <div className="stat">
              <div className="label">↑ Out (filtered)</div>
              <div style={{ color: 'var(--danger)', fontWeight: 600, marginTop: 4 }}>{money(outgoing, account.currency)}</div>
            </div>
          </div>
        </section>

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

      <section className="panel">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, gap: 12, flexWrap: 'wrap' }}>
          <h2 style={{ margin: 0 }}>Statement</h2>
          <div className="actions" style={{ margin: 0 }}>
            <button className="secondary" onClick={exportStatement} disabled={filtered.length === 0}>
              Export CSV
            </button>
          </div>
        </div>

        <div className="grid three" style={{ gap: 12, marginBottom: 14 }}>
          <label>
            Direction
            <select value={filter.direction} onChange={(e) => setFilter({ ...filter, direction: e.target.value })}>
              <option value="ALL">All</option>
              <option value="IN">Received</option>
              <option value="OUT">Sent</option>
            </select>
          </label>
          <label>
            From date
            <input type="date" value={filter.from} onChange={(e) => setFilter({ ...filter, from: e.target.value })} />
          </label>
          <label>
            To date
            <input type="date" value={filter.to} onChange={(e) => setFilter({ ...filter, to: e.target.value })} />
          </label>
        </div>

        <div className="muted" style={{ fontSize: '0.85rem', marginBottom: 10 }}>
          {filtered.length} transaction{filtered.length !== 1 ? 's' : ''}
        </div>

        {filtered.length === 0 ? (
          <div className="empty">No transactions match this filter.</div>
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
              {filtered.map((t) => {
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
