const request = async (url, options = {}) => {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  })

  if (!response.ok) {
    let message = `Request failed: ${response.status}`
    try {
      const body = await response.json()
      message = body.message || body.error || message
    } catch {
      // Ignore non-JSON error responses.
    }
    throw new Error(message)
  }

  if (response.status === 204) return null
  return response.json()
}

export const api = {
  brokerAccounts: {
    all: () => request('/api/broker-accounts'),
    get: (id) => request(`/api/broker-accounts/${id}`),
    create: (data) => request('/api/broker-accounts', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/broker-accounts/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/broker-accounts/${id}`, { method: 'DELETE' })
  },

  mutualFunds: {
    all: () => request('/api/mutual-funds'),
    get: (id) => request(`/api/mutual-funds/${id}`),
    byBrokerAccount: (brokerAccountId) => request(`/api/mutual-funds/broker-account/${brokerAccountId}`),
    create: (data) => request('/api/mutual-funds', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/mutual-funds/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/mutual-funds/${id}`, { method: 'DELETE' })
  },

  transactions: {
    all: () => request('/api/mutual-fund-txns'),
    get: (id) => request(`/api/mutual-fund-txns/${id}`),
    byFund: (mutualFundId) => request(`/api/mutual-fund-txns/mutual-fund/${mutualFundId}`),
    create: (data) => request('/api/mutual-fund-txns', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/mutual-fund-txns/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/mutual-fund-txns/${id}`, { method: 'DELETE' })
  },

  values: {
    all: () => request('/api/mutual-fund-values'),
    get: (id) => request(`/api/mutual-fund-values/${id}`),
    byFund: (mutualFundId) => request(`/api/mutual-fund-values/mutual-fund/${mutualFundId}`),
    create: (data) => request('/api/mutual-fund-values', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/mutual-fund-values/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/mutual-fund-values/${id}`, { method: 'DELETE' })
  }
}

export default api