/**
 * SmartBill Pro - Shared App Shell
 * Injects sidebar navigation + topbar into any page with a <div id="sb-app-shell-mount">.
 */

const SB_NAV_ITEMS = [
  { section: 'Main' },
  { href: 'dashboard.html', icon: 'bi-grid-1x2-fill', label: 'Dashboard', key: 'dashboard' },
  { href: 'new-bill.html', icon: 'bi-receipt', label: 'New Bill', key: 'new-bill' },
  { href: 'billing.html', icon: 'bi-cash-stack', label: 'Billing', key: 'billing' },
  { section: 'Catalog' },
  { href: 'products.html', icon: 'bi-box-seam', label: 'Products', key: 'products' },
  { href: 'customers.html', icon: 'bi-people-fill', label: 'Customers', key: 'customers' },
  { href: 'purchases.html', icon: 'bi-truck', label: 'Purchases', key: 'purchases' },
  { section: 'Finance' },
  { href: 'bank-accounts.html', icon: 'bi-bank', label: 'Bank Accounts', key: 'bank-accounts' },
  { href: 'tax-management.html', icon: 'bi-percent', label: 'GST & Tax', key: 'tax-management' },
  { href: 'reports.html', icon: 'bi-bar-chart-fill', label: 'Reports', key: 'reports' },
  { section: 'Settings' },
  { href: 'shop-settings.html', icon: 'bi-shop', label: 'Shop Settings', key: 'shop-settings' },
];

function renderAppShell(activeKey) {
  const mount = document.getElementById('sb-app-shell-mount');
  if (!mount) return;

  const user = SmartBillAPI.getCurrentUser() || { fullName: 'User', role: 'ROLE_CASHIER' };
  const roleLabel = (user.role || '').replace('ROLE_', '');
  const initials = (user.fullName || 'U').split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase();

  const navHtml = SB_NAV_ITEMS.map(item => {
    if (item.section) {
      return `<div class="sb-nav-section-label">${item.section}</div>`;
    }
    const active = item.key === activeKey ? 'active' : '';
    return `<a href="${item.href}" class="sb-nav-link ${active}">
        <i class="bi ${item.icon}"></i><span>${item.label}</span>
      </a>`;
  }).join('');

  mount.innerHTML = `
    <aside class="sb-sidebar" id="sbSidebar">
      <div class="sb-sidebar-brand">
        <div class="mark">SB</div>
        <div>
          <div class="name">SmartBill Pro</div>
          <div class="tag">BILLING &amp; GST</div>
        </div>
      </div>
      <nav class="sb-nav">${navHtml}</nav>
      <div class="sb-sidebar-footer">
        <div class="d-flex align-items-center gap-2">
          <div style="width:36px;height:36px;border-radius:50%;background:var(--sb-primary);color:white;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px;flex-shrink:0;">${initials}</div>
          <div class="flex-grow-1 min-width-0">
            <div style="color:white;font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${user.fullName || 'User'}</div>
            <div style="color:#94A3B8;font-size:11px;">${roleLabel}</div>
          </div>
          <button class="btn btn-sm" style="color:#94A3B8;" onclick="logout()" title="Log out">
            <i class="bi bi-box-arrow-right"></i>
          </button>
        </div>
      </div>
    </aside>
    <div class="sb-sidebar-backdrop" id="sbSidebarBackdrop" style="display:none;position:fixed;inset:0;background:rgba(15,23,42,0.4);z-index:1030;"></div>
  `;

  const backdrop = document.getElementById('sbSidebarBackdrop');
  const sidebar = document.getElementById('sbSidebar');
  backdrop.addEventListener('click', () => {
    sidebar.classList.remove('open');
    backdrop.style.display = 'none';
  });
}

function toggleSidebar() {
  const sidebar = document.getElementById('sbSidebar');
  const backdrop = document.getElementById('sbSidebarBackdrop');
  const isOpen = sidebar.classList.toggle('open');
  backdrop.style.display = isOpen ? 'block' : 'none';
}

function renderTopbar(title, subtitle) {
  const mount = document.getElementById('sb-topbar-mount');
  if (!mount) return;
  mount.innerHTML = `
    <div class="d-flex align-items-center gap-3">
      <button class="sb-sidebar-toggle" onclick="toggleSidebar()"><i class="bi bi-list"></i></button>
      <div>
        <div class="sb-page-title">${title}</div>
        ${subtitle ? `<div class="sb-page-subtitle">${subtitle}</div>` : ''}
      </div>
    </div>
    <div class="d-flex align-items-center gap-2">
      <button class="btn btn-light border d-none d-md-inline-flex align-items-center gap-2" onclick="window.location.href='new-bill.html'">
        <i class="bi bi-plus-lg"></i> New Bill
      </button>
    </div>
  `;
}
