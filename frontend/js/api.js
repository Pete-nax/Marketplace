// ============================================================
// API client — thin wrapper around the Spring Boot backend.
// Set API_BASE_URL to your deployed backend, or localhost:8080 for dev.
// ============================================================

const API_BASE_URL = window.API_BASE_URL || 'http://localhost:8080';

const Auth = {
  getAccessToken() {
    return localStorage.getItem('accessToken');
  },
  getUser() {
    const raw = localStorage.getItem('user');
    return raw ? JSON.parse(raw) : null;
  },
  isLoggedIn() {
    return !!this.getAccessToken();
  },
  setSession({ accessToken, refreshToken, userId, email, role }) {
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('user', JSON.stringify({ userId, email, role }));
  },
  clearSession() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
  },
};

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function apiFetch(path, { method = 'GET', body, auth = false } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = Auth.getAccessToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 && auth) {
    Auth.clearSession();
    window.location.href = 'login.html';
    throw new ApiError('Session expired', 401);
  }

  const isJson = res.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await res.json() : null;

  if (!res.ok) {
    throw new ApiError(data?.message || 'Something went wrong', res.status);
  }
  return data;
}

const Api = {
  // Auth
  register: (payload) => apiFetch('/api/auth/register', { method: 'POST', body: payload }),
  login: (payload) => apiFetch('/api/auth/login', { method: 'POST', body: payload }),

  // Products
  listProducts: (page = 0, size = 20) => apiFetch(`/api/products?page=${page}&size=${size}`),
  getProduct: (id) => apiFetch(`/api/products/${id}`),

  // Cart (server-side cart, requires auth)
  getCart: () => apiFetch('/api/cart', { auth: true }),
  addOrUpdateCartItem: (productId, quantity) =>
    apiFetch('/api/cart/items', { method: 'POST', body: { productId, quantity }, auth: true }),
  removeCartItem: (itemId) => apiFetch(`/api/cart/items/${itemId}`, { method: 'DELETE', auth: true }),
  clearCart: () => apiFetch('/api/cart', { method: 'DELETE', auth: true }),

  // Orders
  checkout: (shippingAddress) =>
    apiFetch('/api/orders/checkout', { method: 'POST', body: { shippingAddress }, auth: true }),
  myOrders: () => apiFetch('/api/orders', { auth: true }),
  getOrder: (id) => apiFetch(`/api/orders/${id}`, { auth: true }),

  // Admin
  createProduct: (payload) => apiFetch('/api/admin/products', { method: 'POST', body: payload, auth: true }),
  updateProduct: (id, payload) => apiFetch(`/api/admin/products/${id}`, { method: 'PUT', body: payload, auth: true }),
  deactivateProduct: (id) => apiFetch(`/api/admin/products/${id}`, { method: 'DELETE', auth: true }),
  allOrdersAdmin: () => apiFetch('/api/admin/orders', { auth: true }),
};

// ---- Formatting helpers ----
function formatCents(cents, currency = 'USD') {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100);
}
