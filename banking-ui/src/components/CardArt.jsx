/**
 * CardArt — renders a realistic ISO-7810 bank card for any card type / network combo.
 *
 * Props
 *  cardType    : 'DEBIT' | 'CREDIT'
 *  cardNetwork : 'VISA' | 'MASTERCARD' | 'AMEX'
 *  productName : display name, e.g. "Harbor Rewards Visa"
 *  last4       : last 4 digits string, e.g. "4242"
 *  holderName  : cardholder name string
 *  expiresOn   : ISO date string "2028-04-01" or display string "04/28"
 *  status      : 'ACTIVE' | 'FROZEN' | 'CANCELLED' | 'EXPIRED'
 *  mini        : boolean — renders a compact version for product tiles
 */

// ── Network SVG logos ──────────────────────────────────────────────────────────

function VisaLogo({ style }) {
  return (
    <svg viewBox="0 0 60 20" style={{ height: 22, width: 'auto', ...style }}>
      <text
        x="0" y="16"
        fontFamily="'Times New Roman', serif"
        fontWeight="bold"
        fontSize="20"
        fill="white"
        letterSpacing="-0.5"
      >
        VISA
      </text>
    </svg>
  )
}

function MastercardLogo() {
  return (
    <svg viewBox="0 0 50 32" style={{ height: 32, width: 'auto' }}>
      <circle cx="18" cy="16" r="13" fill="#eb001b" opacity="0.95" />
      <circle cx="32" cy="16" r="13" fill="#f79e1b" opacity="0.95" />
      <path
        d="M25 6.3 a13 13 0 0 1 0 19.4 A13 13 0 0 1 25 6.3z"
        fill="#ff5f00" opacity="0.9"
      />
    </svg>
  )
}

function AmexLogo() {
  return (
    <svg viewBox="0 0 80 22" style={{ height: 20, width: 'auto' }}>
      <text
        x="0" y="17"
        fontFamily="Arial, sans-serif"
        fontWeight="900"
        fontSize="18"
        fill="white"
        letterSpacing="2"
      >
        AMEX
      </text>
    </svg>
  )
}

function NetworkLogo({ network }) {
  if (network === 'MASTERCARD') return <MastercardLogo />
  if (network === 'AMEX') return <AmexLogo />
  return <VisaLogo />
}

// ── Chip SVG ───────────────────────────────────────────────────────────────────

function Chip() {
  return (
    <svg viewBox="0 0 44 34" style={{ height: 34, width: 44, flexShrink: 0 }}>
      <rect x="1" y="1" width="42" height="32" rx="5" fill="#d4a843" stroke="#b8902e" strokeWidth="1" />
      {/* contact pads grid */}
      <rect x="6"  y="6"  width="11" height="9"  rx="1" fill="#b8902e" />
      <rect x="21" y="6"  width="11" height="9"  rx="1" fill="#b8902e" />
      <rect x="6"  y="19" width="11" height="9"  rx="1" fill="#b8902e" />
      <rect x="21" y="19" width="11" height="9"  rx="1" fill="#b8902e" />
      {/* center lines */}
      <line x1="1"  y1="17" x2="43" y2="17" stroke="#b8902e" strokeWidth="1" />
      <line x1="16" y1="1"  x2="16" y2="33" stroke="#b8902e" strokeWidth="1" />
      <line x1="28" y1="1"  x2="28" y2="33" stroke="#b8902e" strokeWidth="1" />
    </svg>
  )
}

// ── NFC / Contactless icon ─────────────────────────────────────────────────────

function NfcIcon() {
  return (
    <svg viewBox="0 0 24 24" style={{ height: 22, width: 22, opacity: 0.75 }}>
      <path
        d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"
        fill="none"
      />
      <path d="M8 12c0-2.21 1.79-4 4-4" stroke="white" strokeWidth="1.8" fill="none" strokeLinecap="round" />
      <path d="M5 12c0-3.87 3.13-7 7-7" stroke="white" strokeWidth="1.8" fill="none" strokeLinecap="round" />
      <path d="M11 12c0-0.55 0.45-1 1-1s1 0.45 1 1-.45 1-1 1-1-.45-1-1z" fill="white" />
    </svg>
  )
}

// ── Card themes ────────────────────────────────────────────────────────────────

const THEMES = {
  'DEBIT/VISA': {
    bg: 'linear-gradient(135deg, #0f6a6a 0%, #0b4f4f 60%, #083838 100%)',
    pattern: 'wave',
    label: 'DEBIT',
  },
  'CREDIT/VISA': {
    bg: 'linear-gradient(135deg, #1a3a8f 0%, #0d2463 60%, #071642 100%)',
    pattern: 'dots',
    label: 'CREDIT',
  },
  'CREDIT/MASTERCARD': {
    bg: 'linear-gradient(135deg, #1c1c2e 0%, #2d1b4e 50%, #1a0a3e 100%)',
    pattern: 'geo',
    label: 'CREDIT',
  },
  'CREDIT/AMEX': {
    bg: 'linear-gradient(135deg, #2d2414 0%, #5a4a1e 50%, #3d2e08 100%)',
    pattern: 'lines',
    label: 'CREDIT',
  },
}

const DEFAULT_THEME = {
  bg: 'linear-gradient(135deg, #243b53 0%, #102a43 100%)',
  pattern: 'wave',
  label: 'CARD',
}

// ── Decorative SVG patterns ────────────────────────────────────────────────────

function WavePattern() {
  return (
    <svg
      style={{ position: 'absolute', bottom: 0, left: 0, right: 0, width: '100%', height: '100%', opacity: 0.12, borderRadius: 14 }}
      preserveAspectRatio="xMidYMid slice"
      viewBox="0 0 400 240"
    >
      <path d="M-40 160 Q60 100 160 160 Q260 220 360 160 Q460 100 560 160" fill="none" stroke="white" strokeWidth="60" />
      <path d="M-40 200 Q60 140 160 200 Q260 260 360 200 Q460 140 560 200" fill="none" stroke="white" strokeWidth="40" />
      <circle cx="340" cy="60" r="100" fill="white" />
    </svg>
  )
}

function DotsPattern() {
  return (
    <svg
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.1, borderRadius: 14 }}
      preserveAspectRatio="xMidYMid slice"
      viewBox="0 0 400 240"
    >
      {Array.from({ length: 6 }, (_, row) =>
        Array.from({ length: 10 }, (_, col) => (
          <circle key={`${row}-${col}`} cx={col * 44 + 10} cy={row * 44 + 10} r="6" fill="white" />
        ))
      )}
      <circle cx="320" cy="50" r="90" fill="white" opacity="0.15" />
    </svg>
  )
}

function GeoPattern() {
  return (
    <svg
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.12, borderRadius: 14 }}
      preserveAspectRatio="xMidYMid slice"
      viewBox="0 0 400 240"
    >
      <polygon points="200,0 400,120 300,240 0,240 100,120" fill="white" opacity="0.18" />
      <polygon points="350,0 400,0 400,80" fill="white" opacity="0.25" />
      <polygon points="0,160 0,240 80,240" fill="white" opacity="0.2" />
      <circle cx="350" cy="40" r="70" fill="none" stroke="white" strokeWidth="1.5" />
    </svg>
  )
}

function LinesPattern() {
  return (
    <svg
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.1, borderRadius: 14 }}
      preserveAspectRatio="xMidYMid slice"
      viewBox="0 0 400 240"
    >
      {Array.from({ length: 12 }, (_, i) => (
        <line key={i} x1={i * 36 - 20} y1="0" x2={i * 36 + 60} y2="240" stroke="white" strokeWidth="18" />
      ))}
      <rect x="260" y="20" width="120" height="120" rx="60" fill="white" opacity="0.12" />
    </svg>
  )
}

function Pattern({ type }) {
  if (type === 'dots') return <DotsPattern />
  if (type === 'geo') return <GeoPattern />
  if (type === 'lines') return <LinesPattern />
  return <WavePattern />
}

// ── Frozen overlay ─────────────────────────────────────────────────────────────

function FrozenOverlay() {
  return (
    <div style={{
      position: 'absolute', inset: 0, borderRadius: 14,
      background: 'rgba(16, 42, 67, 0.72)',
      backdropFilter: 'blur(3px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexDirection: 'column', gap: 6, zIndex: 4,
    }}>
      <svg viewBox="0 0 24 24" style={{ width: 36, height: 36, fill: 'rgba(255,255,255,0.85)' }}>
        <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z" />
      </svg>
      <span style={{ color: 'rgba(255,255,255,0.9)', fontWeight: 700, fontSize: '0.85rem', letterSpacing: '0.12em', textTransform: 'uppercase' }}>
        Frozen
      </span>
    </div>
  )
}

// ── Format helpers ─────────────────────────────────────────────────────────────

function formatExpiry(expiresOn) {
  if (!expiresOn) return '••/••'
  if (expiresOn.includes('/')) return expiresOn
  const d = new Date(expiresOn)
  if (isNaN(d)) return '••/••'
  return `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getFullYear()).slice(2)}`
}

// ── Main export ────────────────────────────────────────────────────────────────

export default function CardArt({
  cardType = 'DEBIT',
  cardNetwork = 'VISA',
  productName,
  last4 = '0000',
  holderName = 'HARBOR MEMBER',
  expiresOn,
  status = 'ACTIVE',
  mini = false,
}) {
  const key = `${cardType}/${cardNetwork}`
  const theme = THEMES[key] || DEFAULT_THEME

  const frozen = status === 'FROZEN'
  const cancelled = status === 'CANCELLED' || status === 'EXPIRED'

  const cardStyle = {
    position: 'relative',
    background: theme.bg,
    borderRadius: 14,
    overflow: 'hidden',
    color: 'white',
    fontFamily: "'Manrope', system-ui, sans-serif",
    userSelect: 'none',
    boxShadow: cancelled
      ? '0 4px 20px rgba(0,0,0,0.2)'
      : '0 8px 32px rgba(0,0,0,0.28), 0 2px 8px rgba(0,0,0,0.18)',
    opacity: cancelled ? 0.55 : 1,
    filter: cancelled ? 'grayscale(0.4)' : 'none',
    transition: 'box-shadow 0.2s, transform 0.2s',
    ...(mini ? {
      width: '100%',
      aspectRatio: '1.586 / 1',
    } : {
      width: 340,
      height: 214,
    }),
  }

  return (
    <div style={cardStyle} className="bank-card-art">
      {/* Background decorative pattern */}
      <Pattern type={theme.pattern} />

      {frozen && <FrozenOverlay />}

      <div style={{
        position: 'relative', zIndex: 2,
        padding: mini ? 16 : 22,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        boxSizing: 'border-box',
      }}>
        {/* Row 1: Bank name + card type badge + NFC */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <div style={{
              fontFamily: "'Fraunces', Georgia, serif",
              fontWeight: 700,
              fontSize: mini ? '0.8rem' : '1rem',
              letterSpacing: '-0.01em',
              color: 'rgba(255,255,255,0.95)',
            }}>
              Harbor <span style={{ color: 'rgba(255,255,255,0.65)' }}>Bank</span>
            </div>
            {productName && !mini && (
              <div style={{ fontSize: '0.7rem', color: 'rgba(255,255,255,0.5)', marginTop: 2, letterSpacing: '0.04em' }}>
                {productName}
              </div>
            )}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{
              fontSize: '0.6rem', letterSpacing: '0.1em', textTransform: 'uppercase',
              color: 'rgba(255,255,255,0.55)', border: '1px solid rgba(255,255,255,0.25)',
              borderRadius: 3, padding: '2px 6px',
            }}>
              {theme.label}
            </span>
            <NfcIcon />
          </div>
        </div>

        {/* Row 2: Chip */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Chip />
        </div>

        {/* Row 3: Card number + expiry + network */}
        <div>
          <div style={{
            fontFamily: "'Courier New', monospace",
            fontSize: mini ? '0.85rem' : '1.1rem',
            letterSpacing: mini ? '0.12em' : '0.2em',
            color: 'rgba(255,255,255,0.92)',
            marginBottom: mini ? 6 : 10,
          }}>
            •••• •••• •••• {last4}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
            <div>
              <div style={{ fontSize: '0.6rem', color: 'rgba(255,255,255,0.45)', letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: 2 }}>
                Card holder
              </div>
              <div style={{ fontSize: mini ? '0.7rem' : '0.8rem', fontWeight: 600, letterSpacing: '0.06em', color: 'rgba(255,255,255,0.88)', textTransform: 'uppercase' }}>
                {holderName.length > 20 ? holderName.slice(0, 19) + '…' : holderName}
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 2 }}>
              <div style={{ fontSize: '0.55rem', color: 'rgba(255,255,255,0.4)', letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                Expires
              </div>
              <div style={{ fontSize: mini ? '0.7rem' : '0.82rem', fontWeight: 600, letterSpacing: '0.1em', color: 'rgba(255,255,255,0.88)' }}>
                {formatExpiry(expiresOn)}
              </div>
            </div>
            <div style={{ marginLeft: 8 }}>
              <NetworkLogo network={cardNetwork} />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
