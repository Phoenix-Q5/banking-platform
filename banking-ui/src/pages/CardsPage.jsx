import { useEffect, useState } from 'react'
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
    if (!accounts[0]) { setError('Open an account first from Overview'); return }
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

  const holderName = [session.name].filter(Boolean).join(' ').toUpperCase() || 'HARBOR MEMBER'

  return (
    <div>
      <h1 className="page-title">Cards</h1>
      <p className="page-sub">Your Harbor Bank cards — freeze, unfreeze, and manage limits instantly.</p>

      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

      <div className="actions" style={{ marginBottom: 24 }}>
        <button className="primary" disabled={busy} onClick={issue}>+ Issue debit card</button>
      </div>

      {cards.length === 0 ? (
        <section className="panel" style={{ textAlign: 'center', padding: '40px 24px', color: 'var(--muted)' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>💳</div>
          <div style={{ fontWeight: 600, marginBottom: 6 }}>No cards yet</div>
          <div style={{ fontSize: '0.9rem' }}>Issue your first debit card above, or visit <strong>Products</strong> to browse all card options.</div>
        </section>
      ) : (
        <div className="cards-gallery">
          {cards.map((card) => (
            <div className="card-tile" key={card.id}>
              {/* Physical card render */}
              <CardArt
                cardType={card.cardType}
                cardNetwork={card.cardNetwork}
                last4={card.cardNumberLast4}
                holderName={holderName}
                expiresOn={card.expiresOn}
                status={card.status}
              />

              {/* Metadata + controls */}
              <div className="card-tile-meta">
                <div className="card-meta-row">
                  <span className="card-meta-label">Type</span>
                  <span className="card-meta-value">{card.cardType} · {card.cardNetwork}</span>
                </div>
                <div className="card-meta-row">
                  <span className="card-meta-label">Daily limit</span>
                  <span className="card-meta-value">{money(card.dailyLimit)}</span>
                </div>
                <div className="card-meta-row">
                  <span className="card-meta-label">Monthly limit</span>
                  <span className="card-meta-value">{money(card.monthlyLimit)}</span>
                </div>
                <div className="card-meta-row">
                  <span className="card-meta-label">Status</span>
                  <span className={`badge ${card.status}`}>{card.status}</span>
                </div>
                <div style={{ marginTop: 4 }}>
                  {card.status !== 'CANCELLED' && card.status !== 'EXPIRED' && (
                    <button
                      className={card.status === 'FROZEN' ? 'primary' : 'secondary'}
                      style={{ width: '100%' }}
                      disabled={busy}
                      onClick={() => toggleFreeze(card)}
                    >
                      {card.status === 'FROZEN' ? '🔓 Unfreeze card' : '🔒 Freeze card'}
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
