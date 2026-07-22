const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
const KEYCLOAK_BASE = import.meta.env.VITE_KEYCLOAK_BASE || 'http://localhost:8180'
const REALM = 'banking'
const CLIENT_ID = 'banking-web'

export async function loginWithPassword(username, password) {
  const body = new URLSearchParams({
    client_id: CLIENT_ID,
    grant_type: 'password',
    username,
    password,
    scope: 'openid profile email',
  })
  const res = await fetch(`${KEYCLOAK_BASE}/realms/${REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || 'Login failed')
  }
  return res.json()
}

export async function refreshAccessToken(refreshToken) {
  const body = new URLSearchParams({
    client_id: CLIENT_ID,
    grant_type: 'refresh_token',
    refresh_token: refreshToken,
  })
  const res = await fetch(`${KEYCLOAK_BASE}/realms/${REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!res.ok) {
    throw new Error('Session expired — please sign in again')
  }
  return res.json()
}

export function isTokenExpired(token, skewSeconds = 30) {
  const payload = decodeJwt(token)
  if (!payload?.exp) return true
  return payload.exp * 1000 <= Date.now() + skewSeconds * 1000
}

export function decodeJwt(token) {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(json)
  } catch {
    return null
  }
}

export function rolesFromToken(token) {
  const payload = decodeJwt(token)
  const realm = payload?.realm_access?.roles || []
  return realm.filter((r) => ['CUSTOMER', 'ADMIN', 'SUPPORT'].includes(r))
}

async function request(path, { method = 'GET', token, body } = {}) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let detail = res.statusText
    try {
      const err = await res.json()
      detail = err.detail || err.message || detail
    } catch {
      /* ignore */
    }
    if (res.status === 401) {
      throw new Error('Unauthorized (401) — your session may have expired. Sign out and sign back in, then retry.')
    }
    if (res.status === 405) {
      throw new Error('Method not allowed (405) — the API gateway may be in fallback mode. Retry in a few seconds; if it persists, restart api-gateway.')
    }
    throw new Error(`${detail} (${res.status})`)
  }
  if (res.status === 204) return null
  return res.json()
}

export const publicApi = {
  // No token required — open endpoints
  registerCustomer: (payload) =>
    request('/api/customers/register', { method: 'POST', body: payload }),
  listCardOffers: () =>
    request('/api/cards/offers'),
}

export function forgotPasswordUrl() {
  return `${KEYCLOAK_BASE}/realms/${REALM}/login-actions/reset-credentials?client_id=${CLIENT_ID}`
}

export function accountConsoleUrl() {
  return `${KEYCLOAK_BASE}/realms/${REALM}/account`
}

export const api = {
  // customers
  listCustomers: (token, params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request(`/api/customers${q ? `?${q}` : ''}`, { token })
  },
  getCustomer: (token, id) => request(`/api/customers/${id}`, { token }),
  updateKyc: (token, id, kycStatus) =>
    request(`/api/customers/${id}/kyc`, { method: 'POST', token, body: { kycStatus } }),
  createCustomer: (token, payload) =>
    request('/api/customers', { method: 'POST', token, body: payload }),
  updateCustomer: (token, id, payload) =>
    request(`/api/customers/${id}`, { method: 'PUT', token, body: payload }),
  suspendCustomer: (token, id) =>
    request(`/api/customers/${id}/suspend`, { method: 'POST', token }),
  setSupportPin: (token, id, pin) =>
    request(`/api/customers/${id}/support-pin`, { method: 'POST', token, body: { pin } }),
  verifySupportPin: (token, id, pin) =>
    request(`/api/customers/${id}/support-pin/verify`, { method: 'POST', token, body: { pin } }),

  // accounts
  listAccounts: (token, customerId) =>
    request(`/api/accounts?customerId=${customerId}`, { token }),
  createAccount: (token, customerId, currency = 'USD') =>
    request(`/api/accounts`, { method: 'POST', token, body: { customerId, currency } }),
  getAccount: (token, id) => request(`/api/accounts/${id}`, { token }),
  deposit: (token, id, payload) =>
    request(`/api/accounts/${id}/deposit`, { method: 'POST', token, body: payload }),
  listAccountsByStatus: (token, status) =>
    request(`/api/accounts?status=${status}`, { token }),
  decideAccount: (token, id, action) =>
    request(`/api/accounts/${id}/decision`, { method: 'POST', token, body: { action } }),
  freezeAccount: (token, id) => request(`/api/accounts/${id}/freeze`, { method: 'POST', token }),
  unfreezeAccount: (token, id) => request(`/api/accounts/${id}/unfreeze`, { method: 'POST', token }),

  // transactions
  listTransactions: (token, accountId) =>
    request(`/api/transactions?accountId=${accountId}`, { token }),
  transfer: (token, payload) =>
    request(`/api/transactions`, { method: 'POST', token, body: payload }),
  listTransactionsByStatus: (token, status) =>
    request(`/api/transactions?status=${status}`, { token }),
  decideTransaction: (token, id, action) =>
    request(`/api/transactions/${id}/decision`, { method: 'POST', token, body: { action } }),

  // payments
  listPayments: (token, customerId) =>
    request(`/api/payments?customerId=${customerId}`, { token }),
  createPayment: (token, payload) =>
    request(`/api/payments`, { method: 'POST', token, body: payload }),
  listBeneficiaries: (token, customerId) =>
    request(`/api/payments/beneficiaries?customerId=${customerId}`, { token }),
  createBeneficiary: (token, payload) =>
    request(`/api/payments/beneficiaries`, { method: 'POST', token, body: payload }),

  // cards
  listCards: (token, customerId) =>
    request(`/api/cards?customerId=${customerId}`, { token }),
  issueCard: (token, payload) =>
    request(`/api/cards`, { method: 'POST', token, body: payload }),
  freezeCard: (token, id) => request(`/api/cards/${id}/freeze`, { method: 'POST', token }),
  unfreezeCard: (token, id) => request(`/api/cards/${id}/unfreeze`, { method: 'POST', token }),
  updateCardLimits: (token, id, payload) =>
    request(`/api/cards/${id}/limits`, { method: 'PUT', token, body: payload }),

  // loans
  listLoans: (token, customerId) =>
    request(`/api/loans?customerId=${customerId}`, { token }),
  getLoan: (token, id) => request(`/api/loans/${id}`, { token }),
  listAllLoans: (token, status) =>
    request(`/api/loans${status ? `?status=${status}` : ''}`, { token }),
  applyLoan: (token, payload) =>
    request(`/api/loans`, { method: 'POST', token, body: payload }),
  decideLoan: (token, id, decision) =>
    request(`/api/loans/${id}/decision`, { method: 'POST', token, body: { decision } }),

  // notifications
  listNotifications: (token, customerId) =>
    request(`/api/notifications?customerId=${customerId}`, { token }),
  createNotification: (token, payload) =>
    request(`/api/notifications`, { method: 'POST', token, body: payload }),
  markNotificationRead: (token, id) =>
    request(`/api/notifications/${id}/read`, { method: 'POST', token }),
  registerDevice: (token, payload) =>
    request(`/api/notifications/devices`, { method: 'POST', token, body: payload }),
  listDevices: (token, customerId) =>
    request(`/api/notifications/devices?customerId=${customerId}`, { token }),

  // audit
  listAudit: (token, params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request(`/api/audit/events${q ? `?${q}` : ''}`, { token })
  },
  writeAudit: (token, payload) =>
    request(`/api/audit/events`, { method: 'POST', token, body: payload }),
}

export { API_BASE, KEYCLOAK_BASE }
