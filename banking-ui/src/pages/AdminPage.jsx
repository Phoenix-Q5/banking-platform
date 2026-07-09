import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

export default function AdminPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const [customers, setCustomers] = useState([])
  const [loans, setLoans] = useState([])
  const [audit, setAudit] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = async () => {
    try {
      const [c, l, a] = await Promise.all([
        api.listCustomers(token),
        api.listAllLoans(token),
        api.listAudit(token),
      ])
      setCustomers(c)
      setLoans(l)
      setAudit(a)
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  const setKyc = async (id, kycStatus) => {
    setBusy(true)
    setError('')
    try {
      await api.updateKyc(token, id, kycStatus)
      await api.writeAudit(token, {
        actor: session.username,
        action: 'KYC_UPDATE',
        resourceType: 'customer',
        resourceId: id,
        customerId: id,
        details: `KYC set to ${kycStatus}`,
      })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const decide = async (id, decision) => {
    setBusy(true)
    setError('')
    try {
      await api.decideLoan(token, id, decision)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">Admin console</h1>
      <p className="page-sub">KYC decisions, loan underwriting, and platform audit trail.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <div className="grid two">
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

        <section className="panel">
          <h2>Loan pipeline</h2>
          {loans.length === 0 ? <div className="empty">No loans.</div> : (
            <table className="table">
              <thead><tr><th>Loan</th><th>Amount</th><th>Status</th><th></th></tr></thead>
              <tbody>
                {loans.map((l) => (
                  <tr key={l.id}>
                    <td>{l.productCode}<div className="muted">{l.customerId?.slice(0, 8)}…</div></td>
                    <td>{money(l.principal, l.currency)}</td>
                    <td><span className={`badge ${l.status}`}>{l.status}</span></td>
                    <td className="actions">
                      <button className="secondary" disabled={busy} onClick={() => decide(l.id, 'REVIEW')}>Review</button>
                      <button className="secondary" disabled={busy} onClick={() => decide(l.id, 'APPROVE')}>Approve</button>
                      <button className="secondary" disabled={busy} onClick={() => decide(l.id, 'ACTIVATE')}>Activate</button>
                      <button className="secondary" disabled={busy} onClick={() => decide(l.id, 'REJECT')}>Reject</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>

      <section className="panel" style={{ marginTop: 16 }}>
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
    </div>
  )
}
