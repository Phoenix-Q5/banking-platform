import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

export default function PaymentsPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const [accounts, setAccounts] = useState([])
  const [beneficiaries, setBeneficiaries] = useState([])
  const [payments, setPayments] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [payee, setPayee] = useState({ nickname: '', accountNumber: '', routingNumber: '', bankName: '' })
  const [payment, setPayment] = useState({ paymentType: 'ACH', amount: '50.00', currency: 'USD', reference: '', beneficiaryId: '' })

  const load = async () => {
    try {
      const [a, b, p] = await Promise.all([
        api.listAccounts(token, customerId),
        api.listBeneficiaries(token, customerId),
        api.listPayments(token, customerId),
      ])
      setAccounts(a)
      setBeneficiaries(b)
      setPayments(p)
      if (!payment.beneficiaryId && b[0]) setPayment((prev) => ({ ...prev, beneficiaryId: b[0].id }))
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  const addPayee = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api.createBeneficiary(token, { customerId, currency: 'USD', ...payee })
      setPayee({ nickname: '', accountNumber: '', routingNumber: '', bankName: '' })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const sendPayment = async (e) => {
    e.preventDefault()
    if (!accounts[0]) {
      setError('Open an account first from Overview')
      return
    }
    setBusy(true)
    setError('')
    try {
      await api.createPayment(token, {
        customerId,
        fromAccountId: accounts[0].id,
        beneficiaryId: payment.beneficiaryId || null,
        paymentType: payment.paymentType,
        amount: Number(payment.amount),
        currency: payment.currency,
        reference: payment.reference,
        description: `${payment.paymentType} payment`,
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
      <h1 className="page-title">Payments</h1>
      <p className="page-sub">ACH, wire, and bill-pay style payments with saved beneficiaries.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <div className="grid two">
        <section className="panel">
          <h2>Send payment</h2>
          <form className="form" onSubmit={sendPayment}>
            <label>
              Type
              <select value={payment.paymentType} onChange={(e) => setPayment({ ...payment, paymentType: e.target.value })}>
                <option>ACH</option>
                <option>WIRE</option>
                <option>BILL_PAY</option>
                <option>INTERNAL</option>
              </select>
            </label>
            <label>
              Beneficiary
              <select value={payment.beneficiaryId} onChange={(e) => setPayment({ ...payment, beneficiaryId: e.target.value })}>
                <option value="">None</option>
                {beneficiaries.map((b) => <option key={b.id} value={b.id}>{b.nickname} · {b.accountNumber}</option>)}
              </select>
            </label>
            <label>
              Amount
              <input type="number" min="0.01" step="0.01" required value={payment.amount} onChange={(e) => setPayment({ ...payment, amount: e.target.value })} />
            </label>
            <label>
              Reference
              <input value={payment.reference} onChange={(e) => setPayment({ ...payment, reference: e.target.value })} />
            </label>
            <div className="actions">
              <button className="primary" disabled={busy}>Submit payment</button>
            </div>
          </form>
        </section>

        <section className="panel">
          <h2>Add beneficiary</h2>
          <form className="form" onSubmit={addPayee}>
            <label>Nickname<input required value={payee.nickname} onChange={(e) => setPayee({ ...payee, nickname: e.target.value })} /></label>
            <label>Account number<input required value={payee.accountNumber} onChange={(e) => setPayee({ ...payee, accountNumber: e.target.value })} /></label>
            <label>Routing number<input value={payee.routingNumber} onChange={(e) => setPayee({ ...payee, routingNumber: e.target.value })} /></label>
            <label>Bank name<input value={payee.bankName} onChange={(e) => setPayee({ ...payee, bankName: e.target.value })} /></label>
            <div className="actions"><button className="primary" disabled={busy}>Save payee</button></div>
          </form>
        </section>
      </div>

      <section className="panel" style={{ marginTop: 16 }}>
        <h2>Payment history</h2>
        {payments.length === 0 ? <div className="empty">No payments yet.</div> : (
          <table className="table">
            <thead><tr><th>When</th><th>Type</th><th>Amount</th><th>Reference</th><th>Status</th></tr></thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id}>
                  <td>{new Date(p.createdAt).toLocaleString()}</td>
                  <td>{p.paymentType}</td>
                  <td>{money(p.amount, p.currency)}</td>
                  <td className="muted">{p.reference || '—'}</td>
                  <td><span className={`badge ${p.status}`}>{p.status}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
