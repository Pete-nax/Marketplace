// ============================================================
// Shared UI chrome across every page
// ============================================================

async function refreshCartCount() {
  const badge = document.getElementById('cart-count-badge');
  if (!badge) return;

  if (!Auth.isLoggedIn()) {
    badge.classList.add('hidden');
    return;
  }

  try {
    const items = await Api.getCart();
    const count = items.reduce((sum, i) => sum + i.quantity, 0);
    if (count > 0) {
      badge.textContent = count;
      badge.classList.remove('hidden');
    } else {
      badge.classList.add('hidden');
    }
  } catch (e) {
    // Cart badge is non-critical UI — fail silently
  }
}

function updateAccountLink() {
  const link = document.getElementById('account-link');
  if (!link) return;

  const user = Auth.getUser();
  if (user) {
    link.href = user.role === 'ADMIN' ? 'admin.html' : 'orders.html';
    link.title = user.role === 'ADMIN' ? 'Admin dashboard' : 'Order history';
  } else {
    link.href = 'login.html';
    link.title = 'Log in';
  }
}

function showToast(message, variant = 'default') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'fixed top-4 right-4 z-[100] flex flex-col gap-2 items-end';
    document.body.appendChild(container);
  }

  const colors = {
    default: 'text-on-surface',
    error: 'text-error',
    success: 'text-primary',
  };

  const toast = document.createElement('div');
  toast.className = `bg-surface-container-lowest border border-outline-variant shadow-lg rounded-lg px-4 py-3 text-sm font-semibold max-w-xs fade-in ${colors[variant] || colors.default}`;
  toast.textContent = message;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transition = 'opacity 200ms ease';
    setTimeout(() => toast.remove(), 220);
  }, 2800);
}

document.addEventListener('DOMContentLoaded', () => {
  updateAccountLink();
  refreshCartCount();
});
