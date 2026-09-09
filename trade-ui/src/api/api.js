const request = async (url, options = {}) => {
  const method = (options.method || 'GET').toUpperCase()
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    // Obtain a fresh token after session rotation at login/logout as well as on first use.
    const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
    if (!csrfResponse.ok) throw new Error('Unable to verify your session. Please refresh and try again.')
    const csrf = await csrfResponse.json()
    headers[csrf.headerName] = csrf.token
  }
  const response = await fetch(url, { ...options, method, headers, credentials: 'same-origin', cache: 'no-store' })

  if (!response.ok) {
    let message = `Request failed: ${response.status}`
    try {
      const body = await response.json()
      message = body.message || body.error || message
    } catch {
      // Ignore non-JSON error responses.
    }
    if (response.status === 401 && (!url.startsWith('/api/auth/') || url.startsWith('/api/auth/profile')) && typeof window !== 'undefined') {
      window.dispatchEvent(new Event('session-expired'))
    }
    const error = new Error(message)
    error.status = response.status
    throw error
  }

  if (response.status === 204) return null
  return response.json()
}

const pagedUrl = (path, { page = 0, size = 20 } = {}) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return `${path}?${query}`
}

const queryUrl = (path, parameters) => {
  const query = new URLSearchParams()
  Object.entries(parameters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  })
  const search = query.toString()
  return search ? `${path}?${search}` : path
}

export const api = {
  analytics: {
    funds: () => request('/api/analytics/funds'),
    valueHistory: (fundId) => request(`/api/analytics/funds/${encodeURIComponent(fundId)}/values`),
    transactionHistory: async (fundId) => {
      const transactions = []
      let page = 0
      let response
      do {
        response = await request(pagedUrl(`/api/mutual-fund-txns/mutual-fund/${encodeURIComponent(fundId)}`, { page, size: 100 }))
        transactions.push(...response.content)
        page += 1
      } while (page < response.totalPages)
      return transactions
    }
  },
  profile: {
    get: () => request('/api/auth/profile'),
    update: (data) => request('/api/auth/profile', { method: 'PUT', body: JSON.stringify(data) }),
    questions: () => request('/api/auth/profile/hint-questions')
  },
  auth: {
    forgotUsername: (data) => request('/api/auth/forgot-username', { method: 'POST', body: JSON.stringify(data) }),
    startPasswordReset: (data) => request('/api/auth/password-reset/challenges', { method: 'POST', body: JSON.stringify(data) }),
    verifyPasswordReset: (data) => request('/api/auth/password-reset/verify', { method: 'POST', body: JSON.stringify(data) }),
    completePasswordReset: (data) => request('/api/auth/password-reset/complete', { method: 'POST', body: JSON.stringify(data) }),
    register: (data) => request('/api/auth/register', { method: 'POST', body: JSON.stringify(data) }),
    me: () => request('/api/auth/me'),
    login: (data) => request('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(data).toString()
    }),
    logout: () => request('/api/auth/logout', { method: 'POST' })
  },
  brokerAccounts: {
    all: (paging) => request(pagedUrl('/api/broker-accounts', paging)),
    get: (id) => request(`/api/broker-accounts/${id}`),
    create: (data) => request('/api/broker-accounts', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/broker-accounts/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/broker-accounts/${id}`, { method: 'DELETE' })
  },

  mutualFunds: {
    all: (paging) => request(pagedUrl('/api/mutual-funds', paging)),
    get: (id) => request(`/api/mutual-funds/${id}`),
    byBrokerAccount: (brokerAccountId, paging) => request(pagedUrl(`/api/mutual-funds/broker-account/${brokerAccountId}`, paging)),
    create: (data) => request('/api/mutual-funds', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/mutual-funds/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/mutual-funds/${id}`, { method: 'DELETE' })
  },

  transactions: {
    fundInvestments: () => request('/api/mutual-fund-txns/summary/by-fund'),
    all: (paging) => request(pagedUrl('/api/mutual-fund-txns', paging)),
    get: (id) => request(`/api/mutual-fund-txns/${id}`),
    byFund: (mutualFundId, paging) => request(pagedUrl(`/api/mutual-fund-txns/mutual-fund/${mutualFundId}`, paging)),
    summary: ({ mutualFundId = 0 } = {}) => request(queryUrl('/api/mutual-fund-txns/summary', { mutualFundId })),
    create: (data) => request('/api/mutual-fund-txns', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/mutual-fund-txns/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/mutual-fund-txns/${id}`, { method: 'DELETE' })
  },

  values: {
    latestByFund: async () => {
      const funds = await api.analytics.funds()
      return Promise.all(funds.map(async (fund) => {
        const response = await api.values.byFund(fund.mutualFundId, { page: 0, size: 1 })
        return { ...fund, latestValue: response.content[0] ?? null }
      }))
    },
    all: (paging) => request(pagedUrl('/api/mutual-fund-values', paging)),
    get: (id) => request(`/api/mutual-fund-values/${id}`),
    byFund: (mutualFundId, paging) => request(pagedUrl(`/api/mutual-fund-values/mutual-fund/${mutualFundId}`, paging)),
    create: (data) => request('/api/mutual-fund-values', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/mutual-fund-values/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/mutual-fund-values/${id}`, { method: 'DELETE' })
  }
}

export default api
