import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { publicApi } from '../api'
import { useAuth } from '../auth'
import CardArt from '../components/CardArt'

function money(v) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(Number(v))
}

function OfferCard({ offer, onApply, isAuthenticated }) {
  return (
    <div className="offer-tile">
      {/* Real card art in mini mode */}
      <div className="offer-tile-art">
        <div style={{ width: '100%', maxWidth: 320 }}>
          <CardArt
            cardType={offer.cardType}
            cardNetwork={offer.cardNetwork}
            productName={offer.productName}
            mini
          />
        </div>
      </div>

      <div className="offer-tile-body">
        <div className="product-card-name">{offer.productName}</div>
        <div className="product-card-sub" style={{ marginBottom: 10 }}>{offer.tagline}</div>

        {/* Key stats row */}
        <div style={{ display: 'flex', gap: 16, marginBottom: 12, flexWrap: 'wrap' }}>
          {offer.annualFee !== 'N/A' && (
            <div style={{ fontSize: '0.78rem' }}>
              <div style={{ color: 'var(--muted)' }}>Annual fee</div>
              <div style={{ fontWeight: 700, color: 'var(--navy)' }}>{offer.annualFee}</div>
            </div>
          )}
          {offer.rewardsRate !== 'N/A' && (
            <div style={{ fontSize: '0.78rem' }}>
              <div style={{ color: 'var(--muted)' }}>Rewards</div>
              <div style={{ fontWeight: 700, color: 'var(--sea-deep)' }}>{offer.rewardsRate}</div>
            </div>
          )}
          {offer.introApr !== 'N/A' && (
            <div style={{ fontSize: '0.78rem' }}>
              <div style={{ color: 'var(--muted)' }}>Intro APR</div>
              <div style={{ fontWeight: 700, color: 'var(--navy)' }}>{offer.introApr}</div>
            </div>
          )}
          <div style={{ fontSize: '0.78rem' }}>
            <div style={{ color: 'var(--muted)' }}>Daily limit</div>
            <div style={{ fontWeight: 700, color: 'var(--navy)' }}>{money(offer.defaultDailyLimit)}</div>
          </div>
        </div>

        {/* Benefits */}
        <ul className="product-perks" style={{ flex: 1 }}>
          {offer.benefits.map((b) => <li key={b}>{b}</li>)}
        </ul>

        {offer.regularApr !== 'N/A' && (
          <p style={{ fontSize: '0.72rem', color: 'var(--muted)', margin: '10px 0 0', lineHeight: 1.4 }}>
            Regular APR: {offer.regularApr}
          </p>
        )}

        <div className="actions" style={{ marginTop: 14 }}>
          {isAuthenticated ? (
            <button className="primary" onClick={() => onApply(offer)}>Get this card</button>
          ) : (
            <Link to="/register" className="btn">Apply now</Link>
          )}
        </div>
      </div>
    </div>
  )
}

export default function CardOffersPage() {
  const { isAuthenticated } = useAuth()
  const [offers, setOffers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [applied, setApplied] = useState('')

  useEffect(() => {
    publicApi.listCardOffers()
      .then(setOffers)
      .catch(() => setError('Unable to load card offers. Please try again later.'))
      .finally(() => setLoading(false))
  }, [])

  const handleApply = (offer) => {
    // Authenticated users are directed to the Products page for the full apply flow
    setApplied(`To get the ${offer.productName}, visit the Products page in your dashboard.`)
    setTimeout(() => setApplied(''), 5000)
  }

  return (
    <div className="app-shell">
      {/* Minimal public header */}
      <header className="topbar">
        <Link to="/" className="brand">Harbor <span>Bank</span></Link>
        <nav className="nav">
          {isAuthenticated ? (
            <Link to="/">Dashboard</Link>
          ) : (
            <>
              <Link to="/register">Open account</Link>
              <Link to="/login">Sign in</Link>
            </>
          )}
        </nav>
      </header>

      {/* Hero */}
      <div style={{
        background: 'linear-gradient(135deg, var(--navy) 0%, var(--sea-deep) 100%)',
        borderRadius: 4, padding: '48px 36px', marginBottom: 40, color: 'white', textAlign: 'center',
      }}>
        <h1 style={{
          fontFamily: 'Fraunces, Georgia, serif', fontSize: 'clamp(2rem, 4vw, 3rem)',
          margin: '0 0 12px', lineHeight: 1.1,
        }}>
          Find your perfect card
        </h1>
        <p style={{ color: 'rgba(255,255,255,0.75)', fontSize: '1.05rem', maxWidth: 500, margin: '0 auto 24px', lineHeight: 1.55 }}>
          From everyday spending to world-class travel perks — Harbor Bank has a card built for your life.
        </p>
        {!isAuthenticated && (
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link to="/register" className="btn" style={{ background: 'white', color: 'var(--navy)', fontWeight: 700, padding: '12px 24px' }}>
              Open an account →
            </Link>
            <Link to="/login" style={{ color: 'rgba(255,255,255,0.8)', alignSelf: 'center', fontSize: '0.95rem' }}>
              Already a member? Sign in
            </Link>
          </div>
        )}
      </div>

      {applied && <div className="success-banner">{applied}</div>}
      {error && <div className="error" style={{ marginBottom: 16 }}>{error}</div>}

      {loading ? (
        <div className="empty" style={{ textAlign: 'center', padding: '48px 0' }}>Loading offers…</div>
      ) : (
        <>
          {/* Debit */}
          {offers.filter((o) => o.cardType === 'DEBIT').length > 0 && (
            <section style={{ marginBottom: 40 }}>
              <div className="section-heading">
                <h2 className="section-title">Debit Cards</h2>
                <p className="section-sub">Spend from your account with no interest — ever.</p>
              </div>
              <div className="product-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
                {offers.filter((o) => o.cardType === 'DEBIT').map((o) => (
                  <OfferCard key={`${o.cardType}-${o.cardNetwork}`} offer={o} onApply={handleApply} isAuthenticated={isAuthenticated} />
                ))}
              </div>
            </section>
          )}

          {/* Credit */}
          {offers.filter((o) => o.cardType === 'CREDIT').length > 0 && (
            <section style={{ marginBottom: 40 }}>
              <div className="section-heading">
                <h2 className="section-title">Credit Cards</h2>
                <p className="section-sub">Earn rewards, build credit, and enjoy exclusive perks.</p>
              </div>
              <div className="product-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
                {offers.filter((o) => o.cardType === 'CREDIT').map((o) => (
                  <OfferCard key={`${o.cardType}-${o.cardNetwork}`} offer={o} onApply={handleApply} isAuthenticated={isAuthenticated} />
                ))}
              </div>
            </section>
          )}
        </>
      )}

      {/* Bottom CTA for unauthenticated visitors */}
      {!isAuthenticated && !loading && (
        <section style={{
          background: 'var(--panel)', border: '1px solid var(--line)', borderRadius: 4,
          padding: '36px 28px', textAlign: 'center', marginTop: 8, boxShadow: 'var(--shadow)',
        }}>
          <h2 style={{ fontFamily: 'Fraunces, Georgia, serif', fontSize: '1.5rem', margin: '0 0 8px', color: 'var(--navy)' }}>
            Ready to apply?
          </h2>
          <p style={{ color: 'var(--muted)', margin: '0 0 20px', lineHeight: 1.55 }}>
            Open a Harbor Bank account in minutes. No branch visit, no paperwork.
          </p>
          <div className="actions" style={{ justifyContent: 'center' }}>
            <Link to="/register" className="btn" style={{ padding: '12px 28px', fontSize: '1rem' }}>
              Get started — it's free
            </Link>
            <Link to="/login" style={{ alignSelf: 'center', color: 'var(--sea)', fontSize: '0.92rem' }}>
              Sign in instead
            </Link>
          </div>
        </section>
      )}
    </div>
  )
}
