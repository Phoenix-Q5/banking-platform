import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

export default function LoansPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const [loans, setLoans] = useState([])
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
      setLoans(await api.listLoans(token, customerId))
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
      await api.applyLoan(token, {
        customerId,
        productCode: form.productCode,
        principal: Number(form.principal),
        interestRate: Number(form.interestRate),
        termMonths: Number(form.termMonths),
        currency: form.currency,
        purpose: form.purpose,
      })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">Loans</h1>
      <p className="page-sub">Apply for personal loans and track underwriting status.</p>
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
                  <tr key={l.id}>
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
    </div>
  )
}
