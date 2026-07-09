import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

export default function CardsPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const [accounts, setAccounts] = useState([])
  const [cards, setCards] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = async () => {
    try {
      const [a, c] = await Promise.all([
        api.listAccounts(token, customerId),
        api.listCards(token, customerId),
      ])
      setAccounts(a)
      setCards(c)
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  const issue = async () => {
    if (!accounts[0]) {
      setError('Open an account first from Overview')
      return
    }
    setBusy(true)
    setError('')
    try {
      await api.issueCard(token, {
        customerId,
        accountId: accounts[0].id,
        cardType: 'DEBIT',
        cardNetwork: 'VISA',
        dailyLimit: 1000,
        monthlyLimit: 10000,
      })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const toggleFreeze = async (card) => {
    setBusy(true)
    setError('')
    try {
      if (card.status === 'FROZEN') await api.unfreezeCard(token, card.id)
      else await api.freezeCard(token, card.id)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h1 className="page-title">Cards</h1>
      <p className="page-sub">Issue debit cards, set limits, and freeze instantly.</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}
      <div className="actions" style={{ marginBottom: 16 }}>
        <button className="primary" disabled={busy} onClick={issue}>Issue debit card</button>
      </div>
      <section className="panel">
        <h2>Your cards</h2>
        {cards.length === 0 ? <div className="empty">No cards yet.</div> : (
          <table className="table">
            <thead><tr><th>Card</th><th>Network</th><th>Limits</th><th>Expires</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {cards.map((c) => (
                <tr key={c.id}>
                  <td>•••• {c.cardNumberLast4} <span className="muted">({c.cardType})</span></td>
                  <td>{c.cardNetwork}</td>
                  <td>{money(c.dailyLimit)} / day · {money(c.monthlyLimit)} / mo</td>
                  <td>{c.expiresOn}</td>
                  <td><span className={`badge ${c.status}`}>{c.status}</span></td>
                  <td><button className="secondary" disabled={busy} onClick={() => toggleFreeze(c)}>{c.status === 'FROZEN' ? 'Unfreeze' : 'Freeze'}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
