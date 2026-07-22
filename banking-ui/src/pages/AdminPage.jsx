import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

const TABS = [
  { id: 'transfers', label: 'Transfer approvals' },
  { id: 'accounts', label: 'Account approvals' },
  { id: 'kyc', label: 'Customers / KYC' },
  { id: 'loans', label: 'Loans' },
  { id: 'freeze', label: 'Freeze' },
  { id: 'audit', label: 'Audit trail' },
]

export default function AdminPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const [tab, setTab] = useState('loans')
  const [customers, setCustomers] = useState([])
  const [loans, setLoans] = useState([])
  const [audit, setAudit] = useState([])
  const [pendingTransfers, setPendingTransfers] = useState([])
  const [pendingAccounts, setPendingAccounts] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  // Freeze + loan tabs: look up one customer by email
  const [lookupEmail, setLookupEmail] = useState('')
  const [lookupCustomer, setLookupCustomer] = useState(null)
  const [lookupAccounts, setLookupAccounts] = useState([])
  const [lookupCards, setLookupCards] = useState([])
  const [lookupLoans, setLookupLoans] = useState([])

  const load = async () => {
    setError('')
    const results = await Promise.allSettled([
      api.listCustomers(token),
      api.listAllLoans(token),
      api.listAudit(token),
      api.listTransactionsByStatus(token, 'PENDING_APPROVAL'),
      api.listAccountsByStatus(token, 'PENDING_APPROVAL'),
    ])
    const [c, l, a, pt, pa] = results
    if (c.status === 'fulfilled') setCustomers(c.value)
    if (l.status === 'fulfilled') setLoans(l.value)
    if (a.status === 'fulfilled') setAudit(a.value)
    if (pt.status === 'fulfilled') setPendingTransfers(pt.value)
    if (pa.status === 'fulfilled') setPendingAccounts(pa.value)
    const failed = results.filter((r) => r.status === 'rejected')
    if (failed.length) {
      setError(failed.map((r) => r.reason?.message || String(r.reason)).join(' · '))
    }
  }

  useEffect(() => { load() }, [])

  const actionableLoans = loans.filter((l) =>
    ['APPLIED', 'UNDER_REVIEW', 'APPROVED'].includes(l.status)
  )

  const writeAudit = (action, resourceType, resourceId, details, customerId) =>
    api.writeAudit(token, {
      actor: session.username,
      action,
      resourceType,
      resourceId,
      customerId: customerId || null,
      details,
    })

  const run = async (fn) => {
    setBusy(true)
    setError('')
    try {
      await fn()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const setKyc = (id, kycStatus) => run(async () => {
    await api.updateKyc(token, id, kycStatus)
    await writeAudit('KYC_UPDATE', 'customer', id, `KYC set to ${kycStatus}`, id)
    await load()
  })

  const decideLoan = (id, decision) => run(async () => {
    await api.decideLoan(token, id, decision)
    await writeAudit('LOAN_DECISION', 'loan', id, `Loan decision: ${decision}`)
    await load()
    if (lookupCustomer) setLookupLoans(await api.listLoans(token, lookupCustomer.id))
  })

  const decideTransfer = (id, action) => run(async () => {
    await api.decideTransaction(token, id, action)
    await writeAudit('TRANSFER_DECISION', 'transaction', id, `Transfer ${action === 'APPROVE' ? 'approved' : 'rejected'}`)
    await load()
  })

  const decideAccount = (id, action, customerId) => run(async () => {
    await api.decideAccount(token, id, action)
    await writeAudit('ACCOUNT_DECISION', 'account', id, `Account ${action === 'APPROVE' ? 'approved' : 'rejected'}`, customerId)
    await load()
  })

  const lookup = (e) => {
    e?.preventDefault()
    return run(async () => {
      const matches = await api.listCustomers(token, { email: lookupEmail })
      const c = matches[0]
      if (!c) {
        setLookupCustomer(null)
        setLookupAccounts([])
        setLookupCards([])
        setLookupLoans([])
        setError('No customer found')
        return
      }
      setLookupCustomer(c)
      const [accts, cards, lns] = await Promise.all([
        api.listAccounts(token, c.id),
        api.listCards(token, c.id),
        api.listLoans(token, c.id),
      ])
      setLookupAccounts(accts)
      setLookupCards(cards)
      setLookupLoans(lns)
    })
  }

  const refreshLookup = async () => {
    if (!lookupCustomer) return
    const [accts, cards] = await Promise.all([
      api.listAccounts(token, lookupCustomer.id),
      api.listCards(token, lookupCustomer.id),
    ])
    setLookupAccounts(accts)
    setLookupCards(cards)
  }

  const toggleAccountFreeze = (a) => run(async () => {
    if (a.status === 'FROZEN') {
      await api.unfreezeAccount(token, a.id)
      await writeAudit('ACCOUNT_UNFREEZE', 'account', a.id, `Account ${a.accountNumber} unfrozen`, a.customerId)
    } else {
      await api.freezeAccount(token, a.id)
      await writeAudit('ACCOUNT_FREEZE', 'account', a.id, `Temporary freeze placed on account ${a.accountNumber}`, a.customerId)
    }
    await refreshLookup()
  })

  const toggleCardFreeze = (c) => run(async () => {
    if (c.status === 'FROZEN') {
      await api.unfreezeCard(token, c.id)
      await writeAudit('CARD_UNFREEZE', 'card', c.id, `Card ••••${c.cardNumberLast4} unfrozen`, lookupCustomer?.id)
    } else {
      await api.freezeCard(token, c.id)
      await writeAudit('CARD_FREEZE', 'card', c.id, `Temporary freeze placed on card ••••${c.cardNumberLast4}`, lookupCustomer?.id)
    }
    await refreshLookup()
  })

  const customerEmail = (id) => customers.find((c) => c.id === id)?.email

  const lookupForm = (
    <section className="panel" style={{ marginBottom: 16 }}>
      <h2>Customer lookup</h2>
      <form className="form" onSubmit={lookup}>
        <label>
          Email
          <input value={lookupEmail} onChange={(e) => setLookupEmail(e.target.value)} placeholder="customer@example.com" />
        </label>
        <div className="actions"><button className="primary" disabled={busy}>Search</button></div>
      </form>
      {lookupCustomer && (
        <div style={{ marginTop: 10 }}>
          <strong>{lookupCustomer.firstName} {lookupCustomer.lastName}</strong>{' '}
          <span className="muted">{lookupCustomer.email}</span>{' '}
          <span className={`badge ${lookupCustomer.status}`}>{lookupCustomer.status}</span>
        </div>
      )}
    </section>
  )

  return (
    <div>
      <h1 className="page-title">Admin console</h1>
      <p className="page-sub">Approvals, KYC, loans, freezes, and the platform audit trail.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <div className="tabs">
        {TABS.map((t) => {
          const count = t.id === 'transfers' ? pendingTransfers.length
            : t.id === 'accounts' ? pendingAccounts.length
            : t.id === 'loans' ? actionableLoans.length
            : 0
          return (
            <button key={t.id} className={`tab ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
              {t.label}
              {count > 0 && <span className="count">{count}</span>}
            </button>
          )
        })}
      </div>

      {tab === 'transfers' && (
        <section className="panel">
          <h2>Transfers pending approval</h2>
          {pendingTransfers.length === 0 ? <div className="empty">No transfers awaiting approval.</div> : (
            <table className="table">
              <thead><tr><th>When</th><th>From</th><th>To</th><th>Amount</th><th></th></tr></thead>
              <tbody>
                {pendingTransfers.map((t) => (
                  <tr key={t.id}>
                    <td>{new Date(t.createdAt).toLocaleString()}</td>
                    <td className="muted">{t.fromAccountId.slice(0, 8)}…</td>
                    <td className="muted">{t.toAccountId.slice(0, 8)}…</td>
                    <td>{money(t.amount, t.currency)}</td>
                    <td className="actions">
                      <button className="secondary" disabled={busy} onClick={() => decideTransfer(t.id, 'APPROVE')}>Approve</button>
                      <button className="secondary" disabled={busy} onClick={() => decideTransfer(t.id, 'REJECT')}>Reject</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      {tab === 'accounts' && (
        <section className="panel">
          <h2>Accounts pending approval</h2>
          {pendingAccounts.length === 0 ? <div className="empty">No accounts awaiting approval.</div> : (
            <table className="table">
              <thead><tr><th>Opened</th><th>Account</th><th>Customer</th><th>Currency</th><th></th></tr></thead>
              <tbody>
                {pendingAccounts.map((a) => (
                  <tr key={a.id}>
                    <td>{new Date(a.createdAt).toLocaleString()}</td>
                    <td>{a.accountNumber}</td>
                    <td className="muted">{customerEmail(a.customerId) || `${a.customerId.slice(0, 8)}…`}</td>
                    <td>{a.currency}</td>
                    <td className="actions">
                      <button className="secondary" disabled={busy} onClick={() => decideAccount(a.id, 'APPROVE', a.customerId)}>Approve</button>
                      <button className="secondary" disabled={busy} onClick={() => decideAccount(a.id, 'REJECT', a.customerId)}>Reject</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      {tab === 'kyc' && (
        <section className="panel">
          <h2>Customers / KYC</h2>
          <table className="table">
            <thead><tr><th>Customer</th><th>KYC</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.id}>
                  <td>{c.firstName} {c.lastName}<div className="muted">{c.email}</div></td>
                  <td><span className={`badge ${c.kycStatus}`}>{c.kycStatus}</span></td>
                  <td><span className={`badge ${c.status}`}>{c.status}</span></td>
                  <td className="actions">
                    <button className="secondary" disabled={busy} onClick={() => setKyc(c.id, 'VERIFIED')}>Verify</button>
                    <button className="secondary" disabled={busy} onClick={() => setKyc(c.id, 'REJECTED')}>Reject</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {tab === 'loans' && (
        <>
          {lookupForm}
          {lookupCustomer && (
            <section className="panel" style={{ marginBottom: 16 }}>
              <h2>Loans for {lookupCustomer.firstName} {lookupCustomer.lastName}</h2>
              {lookupLoans.length === 0 ? <div className="empty">No loans for this customer.</div> : (
                <table className="table">
                  <thead><tr><th>Product</th><th>Principal</th><th>Rate</th><th>Term</th><th>Monthly</th><th>Outstanding</th><th>Status</th><th></th></tr></thead>
                  <tbody>
                    {lookupLoans.map((l) => (
                      <tr key={l.id}>
                        <td>{l.productCode}<div className="muted">{l.purpose || '—'}</div></td>
                        <td>{money(l.principal, l.currency)}</td>
                        <td>{l.interestRate}%</td>
                        <td>{l.termMonths} mo</td>
                        <td>{money(l.monthlyPayment, l.currency)}</td>
                        <td>{money(l.outstandingBalance, l.currency)}</td>
                        <td><span className={`badge ${l.status}`}>{l.status}</span></td>
                        <td className="actions">
                          <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'REVIEW')}>Review</button>
                          <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'APPROVE')}>Approve</button>
                          <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'ACTIVATE')}>Activate</button>
                          <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'REJECT')}>Reject</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          )}
          <section className="panel">
            <h2>Needs decision ({actionableLoans.length})</h2>
            <p className="muted" style={{ marginTop: 0 }}>
              APPLIED → Review → Approve → Activate, or Reject at any step.
            </p>
            {actionableLoans.length === 0 ? (
              <div className="empty">
                {loans.length === 0
                  ? 'No loans in the database. Rebuild/restart and re-run db-seed (see infra/seed).'
                  : 'No loans awaiting a decision.'}
              </div>
            ) : (
              <table className="table">
                <thead><tr><th>Loan</th><th>Customer</th><th>Amount</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  {actionableLoans.map((l) => (
                    <tr key={l.id}>
                      <td>{l.productCode}<div className="muted">{l.purpose || '—'}</div></td>
                      <td className="muted">{customerEmail(l.customerId) || `${l.customerId?.slice(0, 8)}…`}</td>
                      <td>{money(l.principal, l.currency)}</td>
                      <td><span className={`badge ${l.status}`}>{l.status}</span></td>
                      <td className="actions">
                        <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'REVIEW')}>Review</button>
                        <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'APPROVE')}>Approve</button>
                        <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'ACTIVATE')}>Activate</button>
                        <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'REJECT')}>Reject</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
          <section className="panel" style={{ marginTop: 16 }}>
            <h2>All loans ({loans.length})</h2>
            {loans.length === 0 ? <div className="empty">No loans.</div> : (
              <table className="table">
                <thead><tr><th>Loan</th><th>Customer</th><th>Amount</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  {loans.map((l) => (
                    <tr key={l.id}>
                      <td>{l.productCode}</td>
                      <td className="muted">{customerEmail(l.customerId) || `${l.customerId?.slice(0, 8)}…`}</td>
                      <td>{money(l.principal, l.currency)}</td>
                      <td><span className={`badge ${l.status}`}>{l.status}</span></td>
                      <td className="actions">
                        {['APPLIED', 'UNDER_REVIEW', 'APPROVED'].includes(l.status) && (
                          <>
                            <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'REVIEW')}>Review</button>
                            <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'APPROVE')}>Approve</button>
                            <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'ACTIVATE')}>Activate</button>
                            <button className="secondary" disabled={busy} onClick={() => decideLoan(l.id, 'REJECT')}>Reject</button>
                          </>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}

      {tab === 'freeze' && (
        <>
          {lookupForm}
          {lookupCustomer && (
            <div className="grid two">
              <section className="panel">
                <h2>Accounts</h2>
                {lookupAccounts.length === 0 ? <div className="empty">None</div> : (
                  <table className="table">
                    <thead><tr><th>Account</th><th>Balance</th><th>Status</th><th></th></tr></thead>
                    <tbody>
                      {lookupAccounts.map((a) => (
                        <tr key={a.id}>
                          <td>{a.accountNumber}</td>
                          <td>{money(a.balance, a.currency)}</td>
                          <td><span className={`badge ${a.status}`}>{a.status}</span></td>
                          <td className="actions">
                            {(a.status === 'ACTIVE' || a.status === 'FROZEN') && (
                              <button className="secondary" disabled={busy} onClick={() => toggleAccountFreeze(a)}>
                                {a.status === 'FROZEN' ? 'Unfreeze' : 'Freeze'}
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </section>
              <section className="panel">
                <h2>Cards</h2>
                {lookupCards.length === 0 ? <div className="empty">None</div> : (
                  <table className="table">
                    <thead><tr><th>Card</th><th>Status</th><th></th></tr></thead>
                    <tbody>
                      {lookupCards.map((c) => (
                        <tr key={c.id}>
                          <td>•••• {c.cardNumberLast4}</td>
                          <td><span className={`badge ${c.status}`}>{c.status}</span></td>
                          <td className="actions">
                            <button className="secondary" disabled={busy} onClick={() => toggleCardFreeze(c)}>
                              {c.status === 'FROZEN' ? 'Unfreeze' : 'Freeze'}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </section>
            </div>
          )}
        </>
      )}

      {tab === 'audit' && (
        <section className="panel">
          <h2>Audit trail</h2>
          {audit.length === 0 ? <div className="empty">No audit events yet.</div> : (
            <table className="table">
              <thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Resource</th><th>Details</th></tr></thead>
              <tbody>
                {audit.slice(0, 40).map((e) => (
                  <tr key={e.id}>
                    <td>{new Date(e.createdAt).toLocaleString()}</td>
                    <td>{e.actor}</td>
                    <td>{e.action}</td>
                    <td className="muted">{e.resourceType}{e.resourceId ? `:${e.resourceId.slice(0, 8)}…` : ''}</td>
                    <td className="muted">{e.details || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </div>
  )
}
