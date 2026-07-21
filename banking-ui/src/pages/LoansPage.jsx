import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

const STATUS_STEPS = ['APPLIED', 'UNDER_REVIEW', 'APPROVED', 'ACTIVE']

function StatusTimeline({ status }) {
  if (status === 'REJECTED' || status === 'CLOSED') {
    return (
      <div className="loan-timeline">
        <div className={`loan-step done`}>{status === 'REJECTED' ? 'Rejected' : 'Closed'}</div>
      </div>
    )
  }
  const idx = STATUS_STEPS.indexOf(status)
  return (
    <div className="loan-timeline">
      {STATUS_STEPS.map((step, i) => {
        const state = i < idx ? 'done' : i === idx ? 'active' : ''
        return (
          <div key={step} className={`loan-step ${state}`}>
            {step.replace('_', ' ')}
          </div>
        )
      })}
    </div>
  )
}

export default function LoansPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const [loans, setLoans] = useState([])
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState({
    productCode: 'PERSONAL_UNSECURED',
    principal: '5000',
    interestRate: '8.5',
    termMonths: '36',
    currency: 'USD',
    purpose: 'Home improvement',
  })

  const load = async () => {
    try {
      const list = await api.listLoans(token, customerId)
      setLoans(list)
      if (selected) {
        const refreshed = list.find((l) => l.id === selected.id)
        setSelected(refreshed || null)
      }
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  const apply = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const created = await api.applyLoan(token, {
        customerId,
        productCode: form.productCode,
        principal: Number(form.principal),
        interestRate: Number(form.interestRate),
        termMonths: Number(form.termMonths),
        currency: form.currency,
        purpose: form.purpose,
      })
      await load()
      setSelected(created)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">Loans</h1>
      <p className="page-sub">Apply for personal loans and track underwriting from application to funding.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <div className="grid two">
        <section className="panel">
          <h2>Apply</h2>
          <form className="form" onSubmit={apply}>
            <label>Product
              <select value={form.productCode} onChange={(e) => setForm({ ...form, productCode: e.target.value })}>
                <option value="PERSONAL_UNSECURED">Personal unsecured</option>
                <option value="AUTO">Auto loan</option>
                <option value="HOME_IMPROVEMENT">Home improvement</option>
              </select>
            </label>
            <label>Principal<input type="number" min="100" step="100" required value={form.principal} onChange={(e) => setForm({ ...form, principal: e.target.value })} /></label>
            <label>APR %<input type="number" min="0.01" step="0.01" required value={form.interestRate} onChange={(e) => setForm({ ...form, interestRate: e.target.value })} /></label>
            <label>Term (months)<input type="number" min="6" max="360" required value={form.termMonths} onChange={(e) => setForm({ ...form, termMonths: e.target.value })} /></label>
            <label>Purpose<input value={form.purpose} onChange={(e) => setForm({ ...form, purpose: e.target.value })} /></label>
            <div className="actions"><button className="primary" disabled={busy}>Submit application</button></div>
          </form>
        </section>

        <section className="panel">
          <h2>Your loans</h2>
          {loans.length === 0 ? <div className="empty">No loan applications yet.</div> : (
            <table className="table">
              <thead><tr><th>Product</th><th>Principal</th><th>Payment</th><th>Status</th></tr></thead>
              <tbody>
                {loans.map((l) => (
                  <tr
                    key={l.id}
                    onClick={() => setSelected(l)}
                    style={{ cursor: 'pointer', background: selected?.id === l.id ? 'var(--sand)' : undefined }}
                  >
                    <td>{l.productCode}<div className="muted">{l.termMonths} mo · {l.interestRate}% APR</div></td>
                    <td>{money(l.principal, l.currency)}</td>
                    <td>{money(l.monthlyPayment, l.currency)} / mo</td>
                    <td><span className={`badge ${l.status}`}>{l.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>

      {selected && (
        <section className="panel" style={{ marginTop: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
            <h2 style={{ margin: 0 }}>Loan detail</h2>
            <button className="secondary" onClick={() => setSelected(null)}>Close</button>
          </div>
          <StatusTimeline status={selected.status} />
          <div className="grid two" style={{ marginTop: 16 }}>
            <div>
              <div className="detail-row">
                <span className="detail-label">Product</span>
                <span className="detail-value">{selected.productCode}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Purpose</span>
                <span className="detail-value">{selected.purpose || '—'}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Applied</span>
                <span className="detail-value">{selected.createdAt ? new Date(selected.createdAt).toLocaleString() : '—'}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Updated</span>
                <span className="detail-value">{selected.updatedAt ? new Date(selected.updatedAt).toLocaleString() : '—'}</span>
              </div>
            </div>
            <div>
              <div className="detail-row">
                <span className="detail-label">Principal</span>
                <span className="detail-value">{money(selected.principal, selected.currency)}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Monthly payment</span>
                <span className="detail-value">{money(selected.monthlyPayment, selected.currency)}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Outstanding</span>
                <span className="detail-value">{money(selected.outstandingBalance, selected.currency)}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">APR / term</span>
                <span className="detail-value">{selected.interestRate}% · {selected.termMonths} mo</span>
              </div>
            </div>
          </div>
          <p className="muted" style={{ marginTop: 12, fontSize: '0.85rem' }}>
            Underwriting decisions are completed by bank admins. You will see status move through review → approved → active.
          </p>
        </section>
      )}
    </div>
  )
}
