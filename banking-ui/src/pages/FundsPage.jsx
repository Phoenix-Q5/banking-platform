import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

export default function FundsPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const [accounts, setAccounts] = useState([])
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [deposit, setDeposit] = useState({ accountId: '', amount: '100.00', memo: 'Demo deposit' })
  const [transfer, setTransfer] = useState({ fromAccountId: '', toAccountId: '', amount: '25.00', currency: 'USD' })

  const load = async () => {
    try {
      const a = await api.listAccounts(token, customerId)
      setAccounts(a)
      if (a[0]) {
        setDeposit((d) => ({ ...d, accountId: d.accountId || a[0].id }))
        setTransfer((t) => ({
          ...t,
          fromAccountId: t.fromAccountId || a[0].id,
          toAccountId: t.toAccountId || (a[1]?.id || ''),
        }))
      }
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  const doDeposit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setOk('')
    try {
      await api.deposit(token, deposit.accountId, {
        amount: Number(deposit.amount),
        memo: deposit.memo || 'Deposit',
      })
      await api.writeAudit(token, {
        actor: session.username,
        action: 'DEPOSIT',
        resourceType: 'account',
        resourceId: deposit.accountId,
        customerId,
        details: `Deposit ${deposit.amount} — ${deposit.memo || 'no memo'}`,
      })
      setOk(`Deposited ${money(deposit.amount)} successfully`)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const doTransfer = async (e) => {
    e.preventDefault()
    if (transfer.fromAccountId === transfer.toAccountId) {
      setError('Choose two different accounts')
      return
    }
    setBusy(true)
    setError('')
    setOk('')
    try {
      const txn = await api.transfer(token, {
        fromAccountId: transfer.fromAccountId,
        toAccountId: transfer.toAccountId,
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
      if (txn.status === 'PENDING_APPROVAL') {
        setOk('Transfer submitted — large transfers require admin approval before funds move.')
      } else if (txn.status === 'FAILED') {
        setError(txn.failureReason || 'Transfer failed')
      } else {
        setOk('Transfer completed')
      }
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const accountLabel = (a) => `${a.accountNumber} · ${money(a.balance, a.currency)}`

  return (
    <div>
      <h1 className="page-title">Funds</h1>
      <p className="page-sub">Deposit demo funds and move money between your Harbor Bank accounts.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}
      {ok && <div className="banner ok" style={{ marginBottom: 12 }}>{ok}</div>}

      {accounts.length === 0 ? (
        <section className="panel empty" style={{ textAlign: 'center', padding: 28 }}>
          No accounts yet — <Link to="/accounts/open" style={{ color: 'var(--sea)' }}>open an account</Link> first.
        </section>
      ) : (
        <div className="grid two">
          <section className="panel">
            <h2>Deposit / fund account</h2>
            <p className="muted" style={{ marginTop: 0, fontSize: '0.88rem' }}>
              Demo funding credits your balance instantly (no external ACH rail).
            </p>
            <form className="form" onSubmit={doDeposit}>
              <label>
                Account
                <select
                  required
                  value={deposit.accountId}
                  onChange={(e) => setDeposit({ ...deposit, accountId: e.target.value })}
                >
                  {accounts.map((a) => (
                    <option key={a.id} value={a.id}>{accountLabel(a)}</option>
                  ))}
                </select>
              </label>
              <label>
                Amount
                <input
                  required
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={deposit.amount}
                  onChange={(e) => setDeposit({ ...deposit, amount: e.target.value })}
                />
              </label>
              <label>
                Memo
                <input value={deposit.memo} onChange={(e) => setDeposit({ ...deposit, memo: e.target.value })} />
              </label>
              <div className="actions">
                <button className="primary" disabled={busy}>Deposit</button>
              </div>
            </form>
          </section>

          <section className="panel">
            <h2>Transfer between accounts</h2>
            <form className="form" onSubmit={doTransfer}>
              <label>
                From
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
                To
                <select
                  required
                  value={transfer.toAccountId}
                  onChange={(e) => setTransfer({ ...transfer, toAccountId: e.target.value })}
                >
                  <option value="">Select account</option>
                  {accounts.map((a) => (
                    <option key={a.id} value={a.id}>{accountLabel(a)}</option>
                  ))}
                </select>
              </label>
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
                <button className="primary" disabled={busy || accounts.length < 2}>
                  {accounts.length < 2 ? 'Need 2+ accounts' : 'Transfer'}
                </button>
                {accounts.length < 2 && (
                  <Link to="/accounts/open" className="secondary" style={{ padding: '10px 14px' }}>
                    Open another account
                  </Link>
                )}
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}
