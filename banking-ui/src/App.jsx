import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import CardOffersPage from './pages/CardOffersPage'
import DashboardPage from './pages/DashboardPage'
import PaymentsPage from './pages/PaymentsPage'
import CardsPage from './pages/CardsPage'
import LoansPage from './pages/LoansPage'
import NotificationsPage from './pages/NotificationsPage'
import AdminPage from './pages/AdminPage'
import SupportPage from './pages/SupportPage'
import ProductsPage from './pages/ProductsPage'
import OnboardingPage from './pages/OnboardingPage'
import OpenAccountPage from './pages/OpenAccountPage'
import AccountDetailPage from './pages/AccountDetailPage'
import ProfilePage from './pages/ProfilePage'
import FundsPage from './pages/FundsPage'

function Shell({ children }) {
  const { session, logout, isAdmin, isSupport, isCustomer } = useAuth()
  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">Harbor <span>Bank</span></div>
        <nav className="nav">
          {isCustomer && (
            <>
              <NavLink to="/" end>Overview</NavLink>
              <NavLink to="/funds">Funds</NavLink>
              <NavLink to="/products">Products</NavLink>
              <NavLink to="/accounts/open">Open Account</NavLink>
              <NavLink to="/payments">Payments</NavLink>
              <NavLink to="/cards">Cards</NavLink>
              <NavLink to="/loans">Loans</NavLink>
              <NavLink to="/notifications">Alerts</NavLink>
              <NavLink to="/profile">Profile</NavLink>
            </>
          )}
          {isSupport && <NavLink to="/support">Support</NavLink>}
          {isAdmin && (
            <>
              <NavLink to="/admin">Admin</NavLink>
              <NavLink to="/onboard">Onboard</NavLink>
            </>
          )}
          <button className="linkish" onClick={logout}>Sign out</button>
        </nav>
        <div className="user-chip">
          @{session?.username}
          {session?.name ? ` · ${session.name}` : ''}
          {' · '}
          {session?.roles?.join(', ')}
        </div>
      </header>
      {children}
    </div>
  )
}

function RequireAuth({ children, role }) {
  const { isAuthenticated, session } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (role && !session.roles.includes(role) && !(role === 'SUPPORT' && session.roles.includes('ADMIN'))) {
    return <Navigate to="/" replace />
  }
  return children
}

export default function App() {
  const { isAuthenticated, isCustomer, isAdmin, isSupport } = useAuth()

  return (
    <Routes>
      <Route path="/login" element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route path="/register" element={isAuthenticated ? <Navigate to="/" replace /> : <RegisterPage />} />
      <Route path="/card-offers" element={<CardOffersPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Shell>
              {isCustomer ? <DashboardPage /> : isAdmin ? <Navigate to="/admin" replace /> : <Navigate to="/support" replace />}
            </Shell>
          </RequireAuth>
        }
      />
      <Route path="/funds" element={<RequireAuth role="CUSTOMER"><Shell><FundsPage /></Shell></RequireAuth>} />
      <Route path="/profile" element={<RequireAuth role="CUSTOMER"><Shell><ProfilePage /></Shell></RequireAuth>} />
      <Route path="/payments" element={<RequireAuth role="CUSTOMER"><Shell><PaymentsPage /></Shell></RequireAuth>} />
      <Route path="/cards" element={<RequireAuth role="CUSTOMER"><Shell><CardsPage /></Shell></RequireAuth>} />
      <Route path="/loans" element={<RequireAuth role="CUSTOMER"><Shell><LoansPage /></Shell></RequireAuth>} />
      <Route path="/notifications" element={<RequireAuth role="CUSTOMER"><Shell><NotificationsPage /></Shell></RequireAuth>} />
      <Route path="/admin" element={<RequireAuth role="ADMIN"><Shell><AdminPage /></Shell></RequireAuth>} />
      <Route path="/onboard" element={<RequireAuth role="ADMIN"><Shell><OnboardingPage /></Shell></RequireAuth>} />
      <Route path="/support" element={<RequireAuth role="SUPPORT"><Shell><SupportPage /></Shell></RequireAuth>} />
      <Route path="/products" element={<RequireAuth role="CUSTOMER"><Shell><ProductsPage /></Shell></RequireAuth>} />
      <Route path="/accounts/open" element={<RequireAuth role="CUSTOMER"><Shell><OpenAccountPage /></Shell></RequireAuth>} />
      <Route path="/accounts/:id" element={<RequireAuth><Shell><AccountDetailPage /></Shell></RequireAuth>} />
      <Route path="*" element={<Navigate to={isAuthenticated ? '/' : '/login'} replace />} />
    </Routes>
  )
}
