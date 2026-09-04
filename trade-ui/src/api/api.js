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
    all: (paging) => request(pagedUrl('/api/mutual-fund-txns', paging)),
    get: (id) => request(`/api/mutual-fund-txns/${id}`),
    byFund: (mutualFundId, paging) => request(pagedUrl(`/api/mutual-fund-txns/mutual-fund/${mutualFundId}`, paging)),
    summary: ({ mutualFundId = 0 } = {}) => request(queryUrl('/api/mutual-fund-txns/summary', { mutualFundId })),
    create: (data) => request('/api/mutual-fund-txns', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/mutual-fund-txns/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/mutual-fund-txns/${id}`, { method: 'DELETE' })
  },

  values: {
    all: (paging) => request(pagedUrl('/api/mutual-fund-values', paging)),
    get: (id) => request(`/api/mutual-fund-values/${id}`),
    byFund: (mutualFundId, paging) => request(pagedUrl(`/api/mutual-fund-values/mutual-fund/${mutualFundId}`, paging)),
    create: (data) => request('/api/mutual-fund-values', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data) => request(`/api/mutual-fund-values/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id) => request(`/api/mutual-fund-values/${id}`, { method: 'DELETE' })
  }
}

export default api
