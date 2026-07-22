import { createContext, useContext, useMemo, useState } from 'react'
import { api, decodeJwt, isTokenExpired, loginWithPassword, refreshAccessToken, rolesFromToken } from './api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => {
    const raw = localStorage.getItem('harbor.session')
    return raw ? JSON.parse(raw) : null
  })

  const persist = (next) => {
    localStorage.setItem('harbor.session', JSON.stringify(next))
    setSession(next)
    return next
  }

  const value = useMemo(() => {
    const login = async (username, password) => {
      const tokens = await loginWithPassword(username, password)
      const payload = decodeJwt(tokens.access_token)
      const roles = rolesFromToken(tokens.access_token)
      let customerId = null
      if (roles.includes('CUSTOMER')) {
        const email = payload?.email || `${username}@example.com`
        try {
          const matches = await api.listCustomers(tokens.access_token, { email })
          customerId = matches[0]?.id || null
          if (!customerId) {
            const byUser = await api.listCustomers(tokens.access_token, { externalUserId: username })
            customerId = byUser[0]?.id || null
          }
        } catch {
          customerId = null
        }
      }
      return persist({
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
        username: payload?.preferred_username || username,
        email: payload?.email || '',
        name: [payload?.given_name, payload?.family_name].filter(Boolean).join(' ') || username,
        roles,
        customerId,
      })
    }

    /** Refresh the access token if it is expired / about to expire. */
    const ensureFreshToken = async () => {
      if (!session?.accessToken) return null
      if (!isTokenExpired(session.accessToken)) return session.accessToken
      if (!session.refreshToken) {
        localStorage.removeItem('harbor.session')
        setSession(null)
        throw new Error('Session expired — please sign in again')
      }
      try {
        const tokens = await refreshAccessToken(session.refreshToken)
        const payload = decodeJwt(tokens.access_token)
        const next = {
          ...session,
          accessToken: tokens.access_token,
          refreshToken: tokens.refresh_token || session.refreshToken,
          roles: rolesFromToken(tokens.access_token),
          username: payload?.preferred_username || session.username,
          email: payload?.email || session.email,
        }
        persist(next)
        return next.accessToken
      } catch {
        localStorage.removeItem('harbor.session')
        setSession(null)
        throw new Error('Session expired — please sign in again')
      }
    }

    const logout = () => {
      localStorage.removeItem('harbor.session')
      setSession(null)
    }

    return {
      session,
      login,
      logout,
      ensureFreshToken,
      isAuthenticated: Boolean(session?.accessToken),
      isAdmin: Boolean(session?.roles?.includes('ADMIN')),
      isSupport: Boolean(session?.roles?.includes('SUPPORT') || session?.roles?.includes('ADMIN')),
      isCustomer: Boolean(session?.roles?.includes('CUSTOMER')),
    }
  }, [session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
