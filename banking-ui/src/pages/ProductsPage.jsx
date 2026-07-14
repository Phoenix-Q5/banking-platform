import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import CardArt from '../components/CardArt'
import AccountCard from '../components/AccountCard'

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount || 0))
}

const CARDS = [
  {
    id: 'rewards',
    network: 'VISA',
    type: 'DEBIT',
    title: 'Harbor Rewards Visa',
    subtitle: '2% cashback on every purchase',
    perks: ['2% cashback everywhere', 'No annual fee', 'Zero foreign transaction fees', 'Contactless payments'],
    accent: 'var(--sea)',
    dailyLimit: 2000,
    monthlyLimit: 10000,
  },
  {
    id: 'travel',
    network: 'VISA',
    type: 'DEBIT',
    title: 'Harbor Travel Visa',
    subtitle: '3× points on travel & dining',
    perks: ['3× points on travel & dining', '1× on everything else', 'Airport lounge access', 'Travel insurance included'],
    accent: 'var(--navy)',
    dailyLimit: 5000,
    monthlyLimit: 20000,
  },
  {
    id: 'student',
    network: 'VISA',
    type: 'DEBIT',
    title: 'Harbor Student Visa',
    subtitle: 'Build credit from day one',
    perks: ['No credit history needed', 'Low monthly limit', 'Real-time spend alerts', 'Free ATM withdrawals'],
    accent: '#2d6a4f',
    dailyLimit: 200,
    monthlyLimit: 1000,
  },
]

const LOAN_PRODUCTS = [
  {
    code: 'PERSONAL_UNSECURED',
    title: 'Personal Loan',
    subtitle: 'For life\'s big moments',
    description: 'Consolidate debt, fund a renovation, or cover any major expense with fixed monthly payments.',
    rate: 'From 6.9% APR',
    range: '$1,000 – $50,000',
    term: 'Up to 60 months',
    icon: '🏦',
    defaultAmount: 10000,
    defaultTerm: 36,
    defaultInterestRate: 6.9,
    purpose: 'Personal financing',
  },
  {
    code: 'AUTO',
    title: 'Auto Loan',
    subtitle: 'Drive away today',
    description: 'New or used vehicle financing with competitive rates and up to 84-month terms.',
    rate: 'From 5.4% APR',
    range: '$5,000 – $100,000',
    term: 'Up to 84 months',
    icon: '🚗',
    defaultAmount: 25000,
    defaultTerm: 60,
    defaultInterestRate: 5.4,
    purpose: 'Auto purchase',
  },
  {
    code: 'HOME_IMPROVEMENT',
    title: 'Home Improvement',
    subtitle: 'Transform your space',
    description: 'Finance your renovation project with no collateral required on amounts up to $100,000.',
    rate: 'From 7.2% APR',
    range: '$2,500 – $100,000',
    term: 'Up to 120 months',
    icon: '🏠',
    defaultAmount: 30000,
    defaultTerm: 84,
    defaultInterestRate: 7.2,
    purpose: 'Home improvement',
  },
]

const ACCOUNT_TYPES = [
  {
    id: 'checking',
    title: 'Everyday Checking',
    subtitle: 'Your primary spending account',
    perks: ['No minimum balance', 'Free bill pay', 'Instant transfer to any Harbor account', 'Debit card included'],
    currency: 'USD',
    icon: '💳',
  },
  {
    id: 'savings',
    title: 'High-Yield Savings',
    subtitle: '4.2% APY — make your money work',
    perks: ['4.2% APY', 'FDIC insured up to $250,000', 'No fees, no lock-in', 'Auto-save rules available'],
    currency: 'USD',
    icon: '📈',
  },
  {
    id: 'fx',
    title: 'Multi-Currency Account',
    subtitle: 'Hold EUR, GBP, CAD and more',
    perks: ['Hold 5+ currencies', 'Real exchange rates', 'Free international transfers', 'Local account details'],
    currency: 'EUR',
    icon: '🌍',
  },
]

export default function ProductsPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const navigate = useNavigate()

  const [busy, setBusy] = useState(null)
  const [success, setSuccess] = useState('')
  const [error, setError] = useState('')

  const [loanModal, setLoanModal] = useState(null)
  const [loanForm, setLoanForm] = useState({ amount: '', currency: 'USD' })

  const clearMessages = () => { setSuccess(''); setError('') }

  const applyForCard = async (card) => {
    clearMessages()
    setBusy(card.id)
    try {
      const accounts = await api.listAccounts(token, customerId)
      if (!accounts || accounts.length === 0) {
        setError('You need at least one account to get a card. Open an account first.')
        return
      }
      const expiresOn = new Date()
      expiresOn.setFullYear(expiresOn.getFullYear() + 4)
      const expiresStr = expiresOn.toISOString().slice(0, 10)
      await api.issueCard(token, {
        customerId,
        accountId: accounts[0].id,
        cardNetwork: card.network,
        cardType: card.type,
        dailyLimit: card.dailyLimit,
        monthlyLimit: card.monthlyLimit,
        expiresOn: expiresStr,
      })
      await api.writeAudit(token, {
        actor: session.username,
        action: 'CARD_APPLIED',
        resourceType: 'card',
        customerId,
        details: `Applied for ${card.title} from products page`,
      })
      setSuccess(`${card.title} has been issued and linked to your account. Check the Cards page.`)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const openLoanModal = (product) => {
    clearMessages()
    setLoanForm({ amount: String(product.defaultAmount), currency: 'USD', termMonths: String(product.defaultTerm), interestRate: String(product.defaultInterestRate) })
    setLoanModal(product)
  }

  const submitLoan = async (e) => {
    e.preventDefault()
    clearMessages()
    setBusy('loan')
    try {
      await api.applyLoan(token, {
        customerId,
        productCode: loanModal.code,
        principal: Number(loanForm.amount),
        interestRate: Number(loanForm.interestRate),
        termMonths: Number(loanForm.termMonths),
        currency: loanForm.currency,
        purpose: loanModal.purpose,
      })
      await api.writeAudit(token, {
        actor: session.username,
        action: 'LOAN_APPLIED',
        resourceType: 'loan',
        customerId,
        details: `Applied for ${loanModal.title} — ${money(loanForm.amount, loanForm.currency)}`,
      })
      setSuccess(`Loan application submitted! Visit the Loans page to track your application.`)
      setLoanModal(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div>
      <h1 className="page-title">Products & Offers</h1>
      <p className="page-sub">Explore Harbor Bank's full range of cards, loans, and accounts.</p>

      {success && <div className="success-banner">{success}</div>}
      {error && <div className="error" style={{ marginBottom: 16 }}>{error}</div>}

      {/* Credit Cards */}
      <section style={{ marginBottom: 36 }}>
        <div className="section-heading">
          <h2 className="section-title">Credit & Debit Cards</h2>
          <p className="section-sub">Tap, swipe, or pay online — pick the card that fits your life.</p>
        </div>
        <div className="product-grid three">
          {CARDS.map((card) => (
            <div className="offer-tile" key={card.id}>
              <div className="offer-tile-art">
                <div style={{ width: '100%' }}>
                  <CardArt cardType={card.type} cardNetwork={card.network} productName={card.title} mini />
                </div>
              </div>
              <div className="offer-tile-body">
                <div className="product-card-name">{card.title}</div>
                <div className="product-card-sub">{card.subtitle}</div>
                <ul className="product-perks" style={{ flex: 1 }}>
                  {card.perks.map((p) => <li key={p}>{p}</li>)}
                </ul>
                <div className="actions" style={{ marginTop: 14 }}>
                  <button className="primary" disabled={busy === card.id} onClick={() => applyForCard(card)}>
                    {busy === card.id ? 'Issuing…' : 'Get this card'}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Loans */}
      <section style={{ marginBottom: 36 }}>
        <div className="section-heading">
          <h2 className="section-title">Loans</h2>
          <p className="section-sub">Competitive rates, flexible terms, and a fast decision.</p>
        </div>
        <div className="product-grid three">
          {LOAN_PRODUCTS.map((loan) => (
            <div className="product-card loan-card" key={loan.code}>
              <div className="loan-icon">{loan.icon}</div>
              <div className="product-card-name">{loan.title}</div>
              <div className="product-card-sub">{loan.subtitle}</div>
              <p className="loan-desc">{loan.description}</p>
              <div className="loan-meta">
                <span className="loan-meta-item"><strong>{loan.rate}</strong></span>
                <span className="loan-meta-item">{loan.range}</span>
                <span className="loan-meta-item">{loan.term}</span>
              </div>
              <div className="actions" style={{ marginTop: 14 }}>
                <button className="primary" onClick={() => openLoanModal(loan)}>Apply now</button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Accounts */}
      <section style={{ marginBottom: 36 }}>
        <div className="section-heading">
          <h2 className="section-title">Accounts</h2>
          <p className="section-sub">Open as many accounts as you need — no paperwork, no waiting.</p>
        </div>
        <div className="product-grid three">
          {ACCOUNT_TYPES.map((acc) => (
            <div className="offer-tile" key={acc.id}>
              <div className="offer-tile-art">
                <div style={{ width: '100%' }}>
                  <AccountCard accountNumber="•••• ••••" currency={acc.currency} label={acc.title} mini />
                </div>
              </div>
              <div className="offer-tile-body">
                <div className="product-card-name">{acc.title}</div>
                <div className="product-card-sub">{acc.subtitle}</div>
                <ul className="product-perks" style={{ flex: 1 }}>
                  {acc.perks.map((p) => <li key={p}>{p}</li>)}
                </ul>
                <div className="actions" style={{ marginTop: 14 }}>
                  <button className="primary" onClick={() => navigate('/accounts/open', { state: { currency: acc.currency, label: acc.title } })}>
                    Open account
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Loan application modal */}
      {loanModal && (
        <div className="modal-backdrop" onClick={() => setLoanModal(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 6px', fontFamily: 'Fraunces, Georgia, serif' }}>{loanModal.title}</h2>
            <p style={{ color: 'var(--muted)', marginTop: 0, marginBottom: 18 }}>{loanModal.description}</p>
            <form className="form" onSubmit={submitLoan}>
              <label>
                Loan amount
                <input
                  required type="number" min="100" step="100"
                  value={loanForm.amount}
                  onChange={(e) => setLoanForm({ ...loanForm, amount: e.target.value })}
                />
              </label>
              <label>
                Currency
                <select value={loanForm.currency} onChange={(e) => setLoanForm({ ...loanForm, currency: e.target.value })}>
                  <option>USD</option><option>EUR</option><option>GBP</option><option>CAD</option>
                </select>
              </label>
              <label>
                Interest rate (% APR)
                <input
                  required type="number" min="0.01" max="99" step="0.1"
                  value={loanForm.interestRate}
                  onChange={(e) => setLoanForm({ ...loanForm, interestRate: e.target.value })}
                />
              </label>
              <label>
                Term (months)
                <input
                  required type="number" min="6" max="120" step="6"
                  value={loanForm.termMonths}
                  onChange={(e) => setLoanForm({ ...loanForm, termMonths: e.target.value })}
                />
              </label>
              {error && <div className="error">{error}</div>}
              <div className="actions">
                <button className="primary" disabled={busy === 'loan'}>
                  {busy === 'loan' ? 'Submitting…' : 'Submit application'}
                </button>
                <button type="button" className="secondary" onClick={() => setLoanModal(null)}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
