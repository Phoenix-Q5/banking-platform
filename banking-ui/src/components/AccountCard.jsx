/**
 * AccountCard — renders a visual account tile.
 *
 * Props
 *  accountNumber : display account number string
 *  currency      : 'USD' | 'EUR' | 'GBP' | 'CAD' | etc.
 *  balance       : numeric or string balance
 *  status        : 'ACTIVE' | 'CLOSED' | etc.
 *  label         : optional friendly label override ('Checking', 'Savings', etc.)
 *  mini          : compact version
 */

function money(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(Number(amount || 0))
}

// ── Account type detection ─────────────────────────────────────────────────────

function detectType(currency, label = '') {
  const l = label.toLowerCase()
  if (l.includes('sav') || l.includes('high-yield')) return 'SAVINGS'
  if (l.includes('multi') || l.includes('fx') || l.includes('foreign') || currency !== 'USD') return 'FX'
  return 'CHECKING'
}

// ── Themes ─────────────────────────────────────────────────────────────────────

const ACC_THEMES = {
  CHECKING: {
    bg: 'linear-gradient(135deg, #102a43 0%, #243b53 60%, #1a4060 100%)',
    icon: '🏦',
    name: 'Everyday Checking',
    pattern: 'check',
  },
  SAVINGS: {
    bg: 'linear-gradient(135deg, #5a3e10 0%, #8a6020 55%, #5a3e10 100%)',
    icon: '📈',
    name: 'High-Yield Savings',
    pattern: 'savings',
  },
  FX: {
    bg: 'linear-gradient(135deg, #1a0f3a 0%, #2d1b6e 55%, #1a0f3a 100%)',
    icon: '🌍',
    name: 'Multi-Currency',
    pattern: 'fx',
  },
}

// ── Patterns ───────────────────────────────────────────────────────────────────

function CheckingPattern() {
  return (
    <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.08 }}
      viewBox="0 0 300 180" preserveAspectRatio="xMidYMid slice">
      {Array.from({ length: 8 }, (_, i) => (
        <line key={i} x1={i * 42} y1="0" x2={i * 42} y2="180" stroke="white" strokeWidth="1" />
      ))}
      {Array.from({ length: 6 }, (_, i) => (
        <line key={i} x1="0" y1={i * 36} x2="300" y2={i * 36} stroke="white" strokeWidth="1" />
      ))}
      <circle cx="240" cy="36" r="60" fill="white" opacity="0.3" />
    </svg>
  )
}

function SavingsPattern() {
  return (
    <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.12 }}
      viewBox="0 0 300 180" preserveAspectRatio="xMidYMid slice">
      {/* Rising bars */}
      {[20, 40, 55, 75, 95, 110, 130, 155, 175, 200].map((h, i) => (
        <rect key={i} x={i * 30 + 2} y={180 - h} width="20" height={h} fill="white" rx="2" />
      ))}
      <circle cx="255" cy="30" r="50" fill="white" opacity="0.15" />
    </svg>
  )
}

function FxPattern() {
  return (
    <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.12 }}
      viewBox="0 0 300 180" preserveAspectRatio="xMidYMid slice">
      <circle cx="150" cy="90" r="80" fill="none" stroke="white" strokeWidth="1.5" />
      <circle cx="150" cy="90" r="55" fill="none" stroke="white" strokeWidth="1" />
      <ellipse cx="150" cy="90" rx="30" ry="80" fill="none" stroke="white" strokeWidth="1.5" />
      <ellipse cx="150" cy="90" rx="80" ry="30" fill="none" stroke="white" strokeWidth="1" />
      <line x1="70" y1="90" x2="230" y2="90" stroke="white" strokeWidth="1" />
      <line x1="150" y1="10" x2="150" y2="170" stroke="white" strokeWidth="1" />
      <circle cx="245" cy="30" r="40" fill="white" opacity="0.12" />
    </svg>
  )
}

function AccPattern({ type }) {
  if (type === 'SAVINGS') return <SavingsPattern />
  if (type === 'FX') return <FxPattern />
  return <CheckingPattern />
}

// ── Apt rate / yield display ───────────────────────────────────────────────────

function AccentStat({ type }) {
  if (type === 'SAVINGS') return (
    <div style={{ fontSize: '0.65rem', color: 'rgba(255,220,120,0.85)', letterSpacing: '0.06em', marginTop: 2 }}>
      4.20% APY
    </div>
  )
  if (type === 'FX') return (
    <div style={{ fontSize: '0.65rem', color: 'rgba(180,150,255,0.85)', letterSpacing: '0.06em', marginTop: 2 }}>
      5+ currencies
    </div>
  )
  return (
    <div style={{ fontSize: '0.65rem', color: 'rgba(120,180,255,0.8)', letterSpacing: '0.06em', marginTop: 2 }}>
      No fees · FDIC insured
    </div>
  )
}

// ── Main export ────────────────────────────────────────────────────────────────

export default function AccountCard({ accountNumber, currency = 'USD', balance, status = 'ACTIVE', label, mini = false }) {
  const type = detectType(currency, label)
  const theme = ACC_THEMES[type]
  const closed = status !== 'ACTIVE'

  const cardStyle = {
    position: 'relative',
    background: theme.bg,
    borderRadius: 14,
    overflow: 'hidden',
    color: 'white',
    fontFamily: "'Manrope', system-ui, sans-serif",
    userSelect: 'none',
    boxShadow: closed
      ? '0 4px 20px rgba(0,0,0,0.15)'
      : '0 8px 32px rgba(0,0,0,0.22), 0 2px 8px rgba(0,0,0,0.14)',
    opacity: closed ? 0.6 : 1,
    ...(mini ? {
      width: '100%',
      aspectRatio: '1.586 / 1',
    } : {
      width: 300,
      height: 189,
    }),
  }

  return (
    <div style={cardStyle}>
      <AccPattern type={type} />

      <div style={{
        position: 'relative', zIndex: 2,
        padding: mini ? 14 : 20,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        boxSizing: 'border-box',
      }}>
        {/* Top row */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <div style={{
              fontFamily: "'Fraunces', Georgia, serif",
              fontWeight: 700,
              fontSize: mini ? '0.78rem' : '0.92rem',
              color: 'rgba(255,255,255,0.95)',
            }}>
              Harbor <span style={{ color: 'rgba(255,255,255,0.5)' }}>Bank</span>
            </div>
            <div style={{ fontSize: mini ? '0.65rem' : '0.72rem', color: 'rgba(255,255,255,0.55)', marginTop: 2, letterSpacing: '0.04em' }}>
              {label || theme.name}
            </div>
            <AccentStat type={type} />
          </div>
          <span style={{ fontSize: mini ? '1.4rem' : '1.8rem' }}>{theme.icon}</span>
        </div>

        {/* Balance */}
        <div>
          <div style={{ fontSize: '0.6rem', color: 'rgba(255,255,255,0.4)', letterSpacing: '0.1em', textTransform: 'uppercase', marginBottom: 4 }}>
            Available balance
          </div>
          <div style={{
            fontFamily: "'Fraunces', Georgia, serif",
            fontSize: mini ? '1.3rem' : '1.7rem',
            fontWeight: 600,
            lineHeight: 1.1,
            color: closed ? 'rgba(255,255,255,0.5)' : 'rgba(255,255,255,0.96)',
          }}>
            {balance !== undefined ? money(balance, currency) : '—'}
          </div>
          {closed && (
            <div style={{ fontSize: '0.65rem', color: 'rgba(255,80,80,0.85)', marginTop: 4, letterSpacing: '0.08em', textTransform: 'uppercase' }}>
              {status}
            </div>
          )}
        </div>

        {/* Account number */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <div>
            <div style={{ fontSize: '0.55rem', color: 'rgba(255,255,255,0.35)', letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: 2 }}>
              Account no.
            </div>
            <div style={{ fontFamily: "'Courier New', monospace", fontSize: mini ? '0.72rem' : '0.82rem', color: 'rgba(255,255,255,0.75)', letterSpacing: '0.12em' }}>
              {accountNumber || '•••• ••••'}
            </div>
          </div>
          <div style={{
            fontSize: '0.6rem', color: 'rgba(255,255,255,0.4)', letterSpacing: '0.08em',
            textTransform: 'uppercase', border: '1px solid rgba(255,255,255,0.2)',
            borderRadius: 3, padding: '2px 6px',
          }}>
            {currency}
          </div>
        </div>
      </div>
    </div>
  )
}
