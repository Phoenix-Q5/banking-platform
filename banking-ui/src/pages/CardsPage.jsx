import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import CardArt from '../components/CardArt'

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
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [issueForm, setIssueForm] = useState({
    accountId: '', cardType: 'DEBIT', cardNetwork: 'VISA', dailyLimit: '1000', monthlyLimit: '10000',
  })
  const [limitsEdit, setLimitsEdit] = useState({})

  const load = async () => {
    try {
      const [a, c] = await Promise.all([
        api.listAccounts(token, customerId),
        api.listCards(token, customerId),
      ])
      setAccounts(a)
      setCards(c)
      if (a[0]) {
        setIssueForm((f) => ({ ...f, accountId: f.accountId || a[0].id }))
      }
      const next = {}
      c.forEach((card) => {
        next[card.id] = {
          dailyLimit: String(card.dailyLimit ?? 1000),
          monthlyLimit: String(card.monthlyLimit ?? 10000),
        }
      })
      setLimitsEdit(next)
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  const issue = async (e) => {
    e.preventDefault()
    if (!issueForm.accountId) { setError('Open an account first'); return }
    setBusy(true)
    setError('')
    setOk('')
    try {
      await api.issueCard(token, {
        customerId,
        accountId: issueForm.accountId,
        cardType: issueForm.cardType,
        cardNetwork: issueForm.cardNetwork,
        dailyLimit: Number(issueForm.dailyLimit),
        monthlyLimit: Number(issueForm.monthlyLimit),
      })
      setOk('Card issued')
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

  const saveLimits = async (cardId) => {
    const lim = limitsEdit[cardId]
    if (!lim) return
    setBusy(true)
    setError('')
    setOk('')
    try {
      await api.updateCardLimits(token, cardId, {
        dailyLimit: Number(lim.dailyLimit),
        monthlyLimit: Number(lim.monthlyLimit),
      })
      setOk('Limits updated')
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const holderName = [session.name].filter(Boolean).join(' ').toUpperCase() || 'HARBOR MEMBER'

  return (
    <div>
      <h1 className="page-title">Cards</h1>
      <p className="page-sub">Issue cards, freeze them instantly, and manage spend limits.</p>

      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}
      {ok && <div className="banner ok" style={{ marginBottom: 12 }}>{ok}</div>}

      <section className="panel" style={{ marginBottom: 24 }}>
        <h2>Issue a card</h2>
        {accounts.length === 0 ? (
          <div className="empty">
            Open an account first from <Link to="/accounts/open" style={{ color: 'var(--sea)' }}>Open Account</Link>.
          </div>
        ) : (
          <form className="form" onSubmit={issue}>
            <div className="grid two" style={{ gap: 12 }}>
              <label>
                Linked account
                <select
                  required
                  value={issueForm.accountId}
                  onChange={(e) => setIssueForm({ ...issueForm, accountId: e.target.value })}
                >
                  {accounts.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.accountNumber} · {money(a.balance, a.currency)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Type
                <select value={issueForm.cardType} onChange={(e) => setIssueForm({ ...issueForm, cardType: e.target.value })}>
                  <option value="DEBIT">Debit</option>
                  <option value="CREDIT">Credit</option>
                </select>
              </label>
              <label>
                Network
                <select value={issueForm.cardNetwork} onChange={(e) => setIssueForm({ ...issueForm, cardNetwork: e.target.value })}>
                  <option value="VISA">Visa</option>
                  <option value="MASTERCARD">Mastercard</option>
                </select>
              </label>
              <label>
                Daily limit
                <input type="number" min="1" step="1" value={issueForm.dailyLimit} onChange={(e) => setIssueForm({ ...issueForm, dailyLimit: e.target.value })} />
              </label>
              <label>
                Monthly limit
                <input type="number" min="1" step="1" value={issueForm.monthlyLimit} onChange={(e) => setIssueForm({ ...issueForm, monthlyLimit: e.target.value })} />
              </label>
            </div>
            <div className="actions">
              <button className="primary" disabled={busy}>+ Issue card</button>
              <Link to="/products" className="secondary" style={{ padding: '10px 14px' }}>Browse products</Link>
            </div>
          </form>
        )}
      </section>

      {cards.length === 0 ? (
        <section className="panel" style={{ textAlign: 'center', padding: '40px 24px', color: 'var(--muted)' }}>
          <div style={{ fontWeight: 600, marginBottom: 6 }}>No cards yet</div>
          <div style={{ fontSize: '0.9rem' }}>Issue your first card above.</div>
        </section>
      ) : (
        <div className="cards-gallery">
          {cards.map((card) => (
            <div className="card-tile" key={card.id}>
              <CardArt
                cardType={card.cardType}
                cardNetwork={card.cardNetwork}
                last4={card.cardNumberLast4}
                holderName={holderName}
                expiresOn={card.expiresOn}
                status={card.status}
              />
              <div className="card-tile-meta">
                <div className="card-meta-row">
                  <span className="card-meta-label">Type</span>
                  <span className="card-meta-value">{card.cardType} · {card.cardNetwork}</span>
                </div>
                <div className="card-meta-row">
                  <span className="card-meta-label">Status</span>
                  <span className={`badge ${card.status}`}>{card.status}</span>
                </div>
                <label style={{ marginTop: 8, fontSize: '0.85rem' }}>
                  Daily limit
                  <input
                    type="number"
                    min="1"
                    value={limitsEdit[card.id]?.dailyLimit ?? ''}
                    onChange={(e) => setLimitsEdit({
                      ...limitsEdit,
                      [card.id]: { ...limitsEdit[card.id], dailyLimit: e.target.value },
                    })}
                  />
                </label>
                <label style={{ marginTop: 8, fontSize: '0.85rem' }}>
                  Monthly limit
                  <input
                    type="number"
                    min="1"
                    value={limitsEdit[card.id]?.monthlyLimit ?? ''}
                    onChange={(e) => setLimitsEdit({
                      ...limitsEdit,
                      [card.id]: { ...limitsEdit[card.id], monthlyLimit: e.target.value },
                    })}
                  />
                </label>
                <div className="actions" style={{ marginTop: 10, flexDirection: 'column' }}>
                  <button className="secondary" style={{ width: '100%' }} disabled={busy} onClick={() => saveLimits(card.id)}>
                    Save limits
                  </button>
                  {card.status !== 'CANCELLED' && card.status !== 'EXPIRED' && (
                    <button
                      className={card.status === 'FROZEN' ? 'primary' : 'secondary'}
                      style={{ width: '100%' }}
                      disabled={busy}
                      onClick={() => toggleFreeze(card)}
                    >
                      {card.status === 'FROZEN' ? 'Unfreeze card' : 'Freeze card'}
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
