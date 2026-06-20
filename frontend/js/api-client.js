/**
 * SmartBill Pro - API Client
 * Centralizes fetch calls, JWT storage, and auth-aware request handling.
 */

const SmartBillAPI = (() => {
  // Adjust if your backend runs on a different host/port.
  const BASE_URL = window.SMARTBILL_API_BASE || 'http://localhost:8080/api';

  const TOKEN_KEY = 'sb_access_token';
  const REFRESH_KEY = 'sb_refresh_token';
  const USER_KEY = 'sb_user';

  function getAccessToken() {
    return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
  }

  function getRefreshToken() {
    return localStorage.getItem(REFRESH_KEY) || sessionStorage.getItem(REFRESH_KEY);
  }

  function getCurrentUser() {
    const raw = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  function saveSession(authResponse, rememberMe) {
    const store = rememberMe ? localStorage : sessionStorage;
    store.setItem(TOKEN_KEY, authResponse.accessToken);
    store.setItem(REFRESH_KEY, authResponse.refreshToken);
    store.setItem(USER_KEY, JSON.stringify(authResponse.user));
  }

  function clearSession() {
    [localStorage, sessionStorage].forEach(store => {
      store.removeItem(TOKEN_KEY);
      store.removeItem(REFRESH_KEY);
      store.removeItem(USER_KEY);
    });
  }

  function isLoggedIn() {
    return !!getAccessToken();
  }

  async function refreshAccessToken() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) throw new Error('No refresh token available');

    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    });
    if (!res.ok) throw new Error('Refresh failed');
    const json = await res.json();

    const wasRemembered = !!localStorage.getItem(TOKEN_KEY);
    saveSession(json.data, wasRemembered);
    return json.data.accessToken;
  }

  /**
   * Core request wrapper. Attaches JWT, retries once on 401 via refresh token,
   * and normalizes error handling against the backend's ApiResponse envelope.
   */
  async function request(path, options = {}, _isRetry = false) {
    const token = getAccessToken();
    const headers = Object.assign(
      { 'Content-Type': 'application/json' },
      options.headers || {}
    );
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });

    if (res.status === 401 && !_isRetry && getRefreshToken()) {
      try {
        await refreshAccessToken();
        return request(path, options, true);
      } catch (e) {
        clearSession();
        window.location.href = '/login.html?expired=1';
        throw e;
      }
    }

    let json = null;
    const contentType = res.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      json = await res.json();
    }

    if (!res.ok) {
      const message = (json && json.message) || `Request failed with status ${res.status}`;
      const error = new Error(message);
      error.status = res.status;
      error.payload = json;
      throw error;
    }

    return json;
  }

  function get(path) {
    return request(path, { method: 'GET' });
  }
  function post(path, body) {
    return request(path, { method: 'POST', body: JSON.stringify(body) });
  }
  function put(path, body) {
    return request(path, { method: 'PUT', body: JSON.stringify(body) });
  }
  function del(path) {
    return request(path, { method: 'DELETE' });
  }

  /** Downloads a binary (e.g. PDF) endpoint, returning a Blob. */
  async function getBlob(path) {
    const token = getAccessToken();
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch(`${BASE_URL}${path}`, { headers });
    if (!res.ok) throw new Error(`Failed to download (status ${res.status})`);
    return res.blob();
  }

  return {
    BASE_URL,
    get, post, put, delete: del, getBlob,
    saveSession, clearSession, isLoggedIn,
    getCurrentUser, getAccessToken
  };
})();

/** Guards pages that require auth. Call at the top of any protected page's script. */
function requireAuth() {
  if (!SmartBillAPI.isLoggedIn()) {
    window.location.href = '/login.html';
  }
}

function logout() {
  SmartBillAPI.clearSession();
  window.location.href = '/login.html';
}

function formatCurrency(value) {
  const num = Number(value || 0);
  return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(dateStr) {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function showToast(message, type = 'success') {
  let container = document.getElementById('sb-toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'sb-toast-container';
    container.style.cssText = 'position:fixed;top:20px;right:20px;z-index:2000;display:flex;flex-direction:column;gap:8px;';
    document.body.appendChild(container);
  }
  const colors = {
    success: '#16A34A', error: '#DC2626', warning: '#F59E0B', info: '#0891B2'
  };
  const toast = document.createElement('div');
  toast.style.cssText = `background:white;border-left:4px solid ${colors[type] || colors.info};box-shadow:0 8px 24px rgba(15,23,42,0.15);border-radius:8px;padding:12px 16px;min-width:260px;font-size:13.5px;color:#0F172A;font-family:Inter,sans-serif;`;
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.transition = 'opacity 0.3s ease';
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}
