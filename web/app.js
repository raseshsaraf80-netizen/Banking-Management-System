// Author: Rasesh Saraf
// Single API base URL constant pointing to your deployed Render backend.
const API_BASE_URL = "https://banking-management-system-71ep.onrender.com";

let authToken = sessionStorage.getItem("bankToken") || "";
let currentUser = JSON.parse(sessionStorage.getItem("bankUser") || "null");
let customerAccounts = [];
let allCustomerTransactions = [];
let adminCustomers = [];
let adminAccounts = [];
let adminLogLines = [];
let activeTab = "";
const chatHistory = [];

const DEFAULT_PAYEES = [
  { name: "Meera Nair", acc: "SB100003" },
  { name: "Kabir Verma", acc: "CA100004" },
  { name: "Aarav Sharma", acc: "SB100001" }
];

const NAV_ICONS = {
  overview: `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>`,
  accounts: `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>`,
  transfers: `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 3l4 4-4 4"/><path d="M3 7h18"/><path d="M7 21l-4-4 4-4"/><path d="M21 17H3"/></svg>`,
  statements: `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="16" y2="17"/></svg>`,
  calculators: `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="2" width="16" height="20" rx="2"/><line x1="8" y1="6" x2="16" y2="6"/><line x1="8" y1="12" x2="8.01" y2="12"/><line x1="12" y1="12" x2="12.01" y2="12"/><line x1="16" y1="12" x2="16.01" y2="12"/><line x1="8" y1="16" x2="8.01" y2="16"/><line x1="12" y1="16" x2="12.01" y2="16"/><line x1="16" y1="16" x2="16.01" y2="16"/></svg>`,
  profile: `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  users: `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
  logs: `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>`
};

const CUSTOMER_TABS = [
  { id: "overview", icon: "overview", label: "Overview", subtitle: "Portfolio allocation, balance trend & recent ledger" },
  { id: "accounts", icon: "accounts", label: "My Accounts", subtitle: "Manage savings & current accounts or open new ones" },
  { id: "transfers", icon: "transfers", label: "Payments & Transfers", subtitle: "Deposits, withdrawals & atomic fund transfers" },
  { id: "statements", icon: "statements", label: "Statements & Export", subtitle: "Filter transactions, print official statements & export CSV" },
  { id: "calculators", icon: "calculators", label: "Financial Calculators", subtitle: "Loan EMI amortization, Fixed Deposit & Savings growth" },
  { id: "profile", icon: "profile", label: "Profile & Security", subtitle: "Update profile details & PBKDF2 password settings" }
];

const ADMIN_TABS = [
  { id: "admin-overview", icon: "overview", label: "Bank Command Center", subtitle: "Liquidity KPIs, interest engine & live feed" },
  { id: "admin-customers", icon: "users", label: "Customers Directory", subtitle: "All registered customer profiles & holdings" },
  { id: "admin-accounts", icon: "accounts", label: "Account Controls", subtitle: "Search, freeze, activate & close accounts" },
  { id: "statements", icon: "statements", label: "Ledger Inspector", subtitle: "Inspect any bank account statement & export CSV" },
  { id: "admin-logs", icon: "logs", label: "Audit Logs", subtitle: "System-wide plain stream audit log (logs/bank.log)" },
  { id: "calculators", icon: "calculators", label: "Financial Calculators", subtitle: "Loan EMI amortization, Fixed Deposit & Savings growth" },
  { id: "profile", icon: "profile", label: "Admin Profile", subtitle: "Administrator profile & password settings" }
];

const authScreen = document.getElementById("authScreen");
const appShell = document.getElementById("appShell");
const authAlert = document.getElementById("authAlert");
const appAlert = document.getElementById("appAlert");

function fmtMoney(val) {
  const num = Number(val || 0);
  return "₹" + num.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function escapeHtml(str) {
  return String(str ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function pushToast(message, isError = false) {
  const stack = document.getElementById("toastStack");
  const div = document.createElement("div");
  div.className = `toast ${isError ? "err" : "ok"}`;
  div.textContent = message;
  stack.appendChild(div);
  setTimeout(() => div.remove(), 4200);
}

function showBanner(el, message, isError = true) {
  el.textContent = message;
  el.className = `banner ${isError ? "error" : "success"}`;
  el.classList.remove("hidden");
  if (el === appAlert) {
    pushToast(message, isError);
  }
}

function hideBanners() {
  authAlert.classList.add("hidden");
  appAlert.classList.add("hidden");
}

async function checkApiHealth() {
  const pill = document.getElementById("apiHealthPill");
  const authStat = document.getElementById("authApiStatus");
  const slowTimer = setTimeout(() => {
    authStat.textContent = "Waking up server, please wait...";
    pill.innerHTML = '<span class="health-dot"></span>Waking Server...';
  }, 2000);

  try {
    const res = await fetch(`${API_BASE_URL}/health`);
    clearTimeout(slowTimer);
    if (res.ok) {
      pill.className = "health-pill online";
      pill.innerHTML = '<span class="health-dot"></span>API Connected';
      authStat.textContent = "System Status: Online";
      return;
    }
  } catch (_) {
    clearTimeout(slowTimer);
  }
  pill.className = "health-pill offline";
  pill.innerHTML = '<span class="health-dot"></span>API Offline';
  authStat.textContent = "System Status: Offline";
}

async function apiFetch(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (!(options.body instanceof FormData) && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  if (authToken) {
    headers["Authorization"] = `Bearer ${authToken}`;
  }
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  if (options.rawResponse) {
    if (!response.ok) {
      const errData = await response.json().catch(() => ({}));
      throw new Error(errData.error || `Request failed (${response.status})`);
    }
    return response;
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 && authToken) {
      logout();
    }
    throw new Error(data.error || `Request failed (${response.status})`);
  }
  return data;
}

function setSession(token, customer) {
  authToken = token;
  currentUser = customer;
  sessionStorage.setItem("bankToken", token);
  sessionStorage.setItem("bankUser", JSON.stringify(customer));
  activeTab = customer.role === "ADMIN" ? "admin-overview" : "overview";
  renderAppState();
}

function logout() {
  authToken = "";
  currentUser = null;
  customerAccounts = [];
  allCustomerTransactions = [];
  adminCustomers = [];
  adminAccounts = [];
  adminLogLines = [];
  sessionStorage.removeItem("bankToken");
  sessionStorage.removeItem("bankUser");
  document.body.classList.remove("admin-mode");
  hideBanners();
  renderAppState();
}

/* ================= AUTHENTICATION ================= */
document.querySelectorAll(".auth-tab").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".auth-tab").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const isLogin = btn.dataset.authTab === "login";
    document.getElementById("loginForm").classList.toggle("hidden", !isLogin);
    document.getElementById("registerForm").classList.toggle("hidden", isLogin);
    document.getElementById("authHeading").textContent = isLogin ? "Welcome back" : "Open your banking profile";
    document.getElementById("authSubheading").textContent = isLogin
      ? "Sign in to access your accounts or the Admin Command Center."
      : "Create a customer profile with salted PBKDF2 security.";
    hideBanners();
  });
});

document.getElementById("toggleLoginPwBtn").addEventListener("click", () => {
  const pwInput = document.getElementById("loginPassword");
  const btn = document.getElementById("toggleLoginPwBtn");
  const isHidden = pwInput.type === "password";
  pwInput.type = isHidden ? "text" : "password";
  btn.textContent = isHidden ? "Hide" : "Show";
});

document.getElementById("fillAaravBtn").addEventListener("click", () => {
  document.querySelector('[data-auth-tab="login"]').click();
  document.getElementById("loginEmail").value = "aarav@bank.local";
  document.getElementById("loginPassword").value = "Aarav@123";
});
document.getElementById("fillMeeraBtn").addEventListener("click", () => {
  document.querySelector('[data-auth-tab="login"]').click();
  document.getElementById("loginEmail").value = "meera@bank.local";
  document.getElementById("loginPassword").value = "Meera@123";
});
document.getElementById("fillAdminBtn").addEventListener("click", () => {
  document.querySelector('[data-auth-tab="login"]').click();
  document.getElementById("loginEmail").value = "admin@bank.local";
  document.getElementById("loginPassword").value = "Admin@123";
});

document.getElementById("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  hideBanners();
  const btn = e.submitter;
  const orig = btn.textContent;
  btn.disabled = true;
  btn.textContent = "Signing in...";
  try {
    const data = await apiFetch("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({
        email: document.getElementById("loginEmail").value.trim(),
        password: document.getElementById("loginPassword").value
      })
    });
    setSession(data.token, data.customer);
  } catch (err) {
    showBanner(authAlert, err.message, true);
  } finally {
    btn.disabled = false;
    btn.textContent = orig;
  }
});

document.getElementById("registerForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  hideBanners();
  const btn = e.submitter;
  const orig = btn.textContent;
  btn.disabled = true;
  btn.textContent = "Creating profile...";
  try {
    const data = await apiFetch("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        name: document.getElementById("regName").value.trim(),
        email: document.getElementById("regEmail").value.trim(),
        phone: document.getElementById("regPhone").value.trim(),
        address: document.getElementById("regAddress").value.trim(),
        password: document.getElementById("regPassword").value
      })
    });
    setSession(data.token, data.customer);
  } catch (err) {
    showBanner(authAlert, err.message, true);
  } finally {
    btn.disabled = false;
    btn.textContent = orig;
  }
});

/* ================= NAVIGATION & SHELL ================= */
function getTabsForCurrentUser() {
  return currentUser && currentUser.role === "ADMIN" ? ADMIN_TABS : CUSTOMER_TABS;
}

function buildSidebarNav() {
  const nav = document.getElementById("sidebarNav");
  const tabs = getTabsForCurrentUser();
  nav.innerHTML = "";
  tabs.forEach((tab) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `nav-item ${tab.id === activeTab ? "active" : ""}`;
    btn.innerHTML = `${NAV_ICONS[tab.icon] || ""}<span>${escapeHtml(tab.label)}</span>`;
    btn.addEventListener("click", () => switchTab(tab.id));
    nav.appendChild(btn);
  });
}

function switchTab(tabId) {
  activeTab = tabId;
  const tabs = getTabsForCurrentUser();
  const meta = tabs.find((t) => t.id === tabId) || tabs[0];
  activeTab = meta.id;

  document.getElementById("pageTitle").textContent = meta.label;
  document.getElementById("pageSubtitle").textContent = meta.subtitle;

  document.querySelectorAll(".tab-view").forEach((view) => view.classList.add("hidden"));
  const targetView = document.getElementById(`tab-${activeTab}`);
  if (targetView) targetView.classList.remove("hidden");

  buildSidebarNav();
  document.getElementById("sidebar").classList.remove("open");
}

document.querySelectorAll("[data-jump-tab]").forEach((btn) => {
  btn.addEventListener("click", () => switchTab(btn.dataset.jumpTab));
});

document.getElementById("mobileMenuBtn").addEventListener("click", () => {
  document.getElementById("sidebar").classList.toggle("open");
});

document.getElementById("logoutBtn").addEventListener("click", logout);
document.getElementById("globalRefreshBtn").addEventListener("click", refreshPortalData);

async function renderAppState() {
  checkApiHealth();
  if (!authToken || !currentUser) {
    authScreen.classList.remove("hidden");
    appShell.classList.add("hidden");
    return;
  }

  const isAdmin = currentUser.role === "ADMIN";
  document.body.classList.toggle("admin-mode", isAdmin);
  document.getElementById("portalModeBadge").textContent = isAdmin ? "ADMIN COMMAND" : "CUSTOMER PORTAL";
  document.getElementById("roleTag").textContent = currentUser.role;
  document.getElementById("userNameLabel").textContent = currentUser.name;
  document.getElementById("userEmailLabel").textContent = currentUser.email;
  document.getElementById("userAvatar").textContent = (currentUser.name || "U").charAt(0).toUpperCase();

  authScreen.classList.add("hidden");
  appShell.classList.remove("hidden");

  if (!activeTab) {
    activeTab = isAdmin ? "admin-overview" : "overview";
  }
  switchTab(activeTab);
  renderProfileTab();
  renderBeneficiaries();
  runAllCalculators();
  await refreshPortalData();
}

async function refreshPortalData() {
  if (!currentUser) return;
  try {
    checkApiHealth();
    if (currentUser.role === "ADMIN") {
      await loadAdminPortal();
    } else {
      await loadCustomerPortal();
    }
  } catch (err) {
    showBanner(appAlert, err.message, true);
  }
}

/* ================= SVG CHARTS ================= */
function renderDonutSvg(segments, centerLabel, centerValue) {
  const total = segments.reduce((sum, s) => sum + Math.max(0, s.value), 0);
  const size = 148;
  const radius = 53;
  const cx = 74;
  const cy = 74;
  const circumference = 2 * Math.PI * radius;

  if (total <= 0) {
    return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
      <circle cx="${cx}" cy="${cy}" r="${radius}" fill="none" stroke="#e2e8f0" stroke-width="17"></circle>
      <text x="${cx}" y="${cy + 4}" text-anchor="middle" font-size="12" fill="#64748b">No data</text>
    </svg>`;
  }

  let offset = 0;
  const circles = segments.map((seg) => {
    const val = Math.max(0, seg.value);
    const arc = (val / total) * circumference;
    const html = `<circle cx="${cx}" cy="${cy}" r="${radius}" fill="none" stroke="${seg.color}" stroke-width="17"
      stroke-dasharray="${arc.toFixed(2)} ${(circumference - arc).toFixed(2)}"
      stroke-dashoffset="${(-offset).toFixed(2)}" transform="rotate(-90 ${cx} ${cy})"></circle>`;
    offset += arc;
    return html;
  }).join("");

  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <circle cx="${cx}" cy="${cy}" r="${radius}" fill="none" stroke="#f1f5f9" stroke-width="17"></circle>
    ${circles}
    <text x="${cx}" y="${cy - 4}" text-anchor="middle" font-size="10" fill="#64748b">${escapeHtml(centerLabel)}</text>
    <text x="${cx}" y="${cy + 14}" text-anchor="middle" font-size="12" font-weight="700" fill="#0f172a">${escapeHtml(centerValue)}</text>
  </svg>`;
}

function renderCashflowBarSvg(credits, debits) {
  const maxVal = Math.max(credits, debits, 1);
  const cWidth = Math.max(8, (credits / maxVal) * 190);
  const dWidth = Math.max(8, (debits / maxVal) * 190);
  return `<svg width="100%" height="120" viewBox="0 0 320 120">
    <text x="0" y="24" font-size="12" font-weight="600" fill="#334155">Credits (Inflow)</text>
    <rect x="0" y="32" width="190" height="16" rx="8" fill="#f1f5f9"></rect>
    <rect x="0" y="32" width="${cWidth.toFixed(1)}" height="16" rx="8" fill="#0d9488"></rect>
    <text x="200" y="45" font-size="11.5" font-weight="700" fill="#0f766e">${fmtMoney(credits)}</text>

    <text x="0" y="76" font-size="12" font-weight="600" fill="#334155">Debits (Outflow)</text>
    <rect x="0" y="84" width="190" height="16" rx="8" fill="#f1f5f9"></rect>
    <rect x="0" y="84" width="${dWidth.toFixed(1)}" height="16" rx="8" fill="#1d4ed8"></rect>
    <text x="200" y="97" font-size="11.5" font-weight="700" fill="#1d4ed8">${fmtMoney(debits)}</text>
  </svg>`;
}

function renderBalanceTrendSvg(txns) {
  if (!txns || txns.length === 0) {
    return '<div class="muted-text">Execute transactions to view balance trajectory.</div>';
  }
  const ordered = [...txns].slice(0, 10).reverse();
  const points = ordered.map((t) => Number(t.balanceAfter));
  if (points.length === 1) points.push(points[0]);

  const w = 310;
  const h = 130;
  const pad = 16;
  const min = Math.min(...points);
  const max = Math.max(...points);
  const span = Math.max(1, max - min);

  const coords = points.map((val, idx) => {
    const x = pad + (idx / (points.length - 1)) * (w - pad * 2);
    const y = h - pad - ((val - min) / span) * (h - pad * 2);
    return { x, y };
  });

  const polyline = coords.map((c) => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");
  const area = `${pad},${h - pad} ${polyline} ${w - pad},${h - pad}`;
  const dots = coords.map((c) => `<circle cx="${c.x.toFixed(1)}" cy="${c.y.toFixed(1)}" r="3.5" fill="#0d9488" stroke="#fff" stroke-width="1.5"/>`).join("");

  return `<svg width="100%" height="${h}" viewBox="0 0 ${w} ${h}">
    <defs>
      <linearGradient id="trendGrad" x1="0" x2="0" y1="0" y2="1">
        <stop offset="0%" stop-color="#0d9488" stop-opacity="0.28"/>
        <stop offset="100%" stop-color="#0d9488" stop-opacity="0.02"/>
      </linearGradient>
    </defs>
    <polygon points="${area}" fill="url(#trendGrad)"></polygon>
    <polyline points="${polyline}" fill="none" stroke="#0d9488" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"></polyline>
    ${dots}
    <text x="${pad}" y="12" font-size="10.5" fill="#64748b">High: ${fmtMoney(max)}</text>
    <text x="${w - pad}" y="12" text-anchor="end" font-size="10.5" fill="#64748b">Low: ${fmtMoney(min)}</text>
  </svg>`;
}

function renderTopAccountsBarSvg(accounts) {
  const sorted = [...accounts].sort((a, b) => Number(b.balance) - Number(a.balance)).slice(0, 4);
  if (!sorted.length) return '<div class="muted-text">No accounts found.</div>';
  const maxBal = Math.max(...sorted.map((a) => Math.max(0, Number(a.balance))), 1);
  const rows = sorted.map((acc, i) => {
    const y = 16 + i * 32;
    const bal = Math.max(0, Number(acc.balance));
    const width = Math.max(8, (bal / maxBal) * 150);
    return `
      <text x="0" y="${y + 11}" font-size="11" font-weight="700" fill="#334155">${escapeHtml(acc.accountNumber)}</text>
      <rect x="74" y="${y}" width="150" height="14" rx="7" fill="#f1f5f9"></rect>
      <rect x="74" y="${y}" width="${width.toFixed(1)}" height="14" rx="7" fill="${acc.accountType === "SAVINGS" ? "#0d9488" : "#d97706"}"></rect>
      <text x="232" y="${y + 11}" font-size="11" font-weight="600" fill="#0f172a">${fmtMoney(acc.balance)}</text>
    `;
  }).join("");
  return `<svg width="100%" height="145" viewBox="0 0 330 145">${rows}</svg>`;
}

/* ================= MODAL (RECEIPT & CUSTOMER DETAILS) ================= */
function openInfoModal(badgeText, title, details, showPrint = true) {
  document.getElementById("modalBadge").textContent = badgeText;
  document.getElementById("modalTitle").textContent = title;
  document.getElementById("printReceiptBtn").classList.toggle("hidden", !showPrint);
  const body = document.getElementById("receiptBody");
  body.innerHTML = details.map(([k, v]) =>
    `<div class="receipt-row"><span>${escapeHtml(k)}</span><strong>${escapeHtml(v)}</strong></div>`
  ).join("");
  document.getElementById("receiptModal").classList.remove("hidden");
}

document.getElementById("closeReceiptBtn").addEventListener("click", () =>
  document.getElementById("receiptModal").classList.add("hidden"));
document.getElementById("doneReceiptBtn").addEventListener("click", () =>
  document.getElementById("receiptModal").classList.add("hidden"));
document.getElementById("printReceiptBtn").addEventListener("click", () => window.print());

/* ================= SAVED BENEFICIARIES ================= */
function getPayeeStorageKey() {
  return `bankPayees_${currentUser ? currentUser.customerId : "guest"}`;
}

function getBeneficiaries() {
  const raw = localStorage.getItem(getPayeeStorageKey());
  if (!raw) return DEFAULT_PAYEES;
  try {
    return JSON.parse(raw);
  } catch {
    return DEFAULT_PAYEES;
  }
}

function renderBeneficiaries() {
  const wrap = document.getElementById("beneficiaryChips");
  wrap.innerHTML = "";
  getBeneficiaries().forEach((p) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "chip-recipient";
    btn.innerHTML = `${escapeHtml(p.name)} (${escapeHtml(p.acc)}) <span class="chip-del" title="Remove">&times;</span>`;
    btn.addEventListener("click", (ev) => {
      if (ev.target.classList.contains("chip-del")) {
        const updated = getBeneficiaries().filter((item) => item.acc !== p.acc);
        localStorage.setItem(getPayeeStorageKey(), JSON.stringify(updated));
        renderBeneficiaries();
        return;
      }
      document.getElementById("toAccount").value = p.acc;
      updateTransferAfterPreview();
    });
    wrap.appendChild(btn);
  });
}

document.getElementById("addPayeeBtn").addEventListener("click", () => {
  const name = document.getElementById("newPayeeName").value.trim();
  const acc = document.getElementById("newPayeeAcc").value.trim().toUpperCase();
  if (!name || !acc) {
    showBanner(appAlert, "Enter both payee name and account number.", true);
    return;
  }
  const list = getBeneficiaries().filter((p) => p.acc !== acc);
  list.unshift({ name, acc });
  localStorage.setItem(getPayeeStorageKey(), JSON.stringify(list.slice(0, 8)));
  document.getElementById("newPayeeName").value = "";
  document.getElementById("newPayeeAcc").value = "";
  renderBeneficiaries();
  pushToast(`Saved beneficiary ${name} (${acc})`);
});

/* ================= CUSTOMER PORTAL ================= */
async function loadCustomerPortal() {
  customerAccounts = await apiFetch("/api/accounts");
  populateAccountDropdowns(customerAccounts);

  let total = 0;
  let activeCount = 0;
  let savingsCount = 0;
  let currentCount = 0;
  let savingsSum = 0;
  let currentSum = 0;

  customerAccounts.forEach((acc) => {
    const bal = Number(acc.balance);
    total += bal;
    if (acc.status === "ACTIVE") activeCount++;
    if (acc.accountType === "SAVINGS") {
      savingsCount++;
      savingsSum += Math.max(0, bal);
    } else {
      currentCount++;
      currentSum += Math.max(0, bal);
    }
  });

  document.getElementById("kpiTotalBalance").textContent = fmtMoney(total);
  document.getElementById("kpiActiveCount").textContent = String(activeCount);
  document.getElementById("kpiAccountMix").textContent = `${savingsCount} Savings • ${currentCount} Current`;
  document.getElementById("kpiEstInterest").textContent = fmtMoney(savingsSum * (0.035 / 12));

  document.getElementById("portfolioChartWrap").innerHTML = renderDonutSvg(
    [
      { label: "Savings", value: savingsSum, color: "#0d9488" },
      { label: "Current", value: currentSum, color: "#1d4ed8" }
    ],
    "Portfolio",
    fmtMoney(total)
  ) + `<div class="chart-legend">
    <div class="legend-row"><span class="dot" style="background:#0d9488"></span>Savings: <strong>${fmtMoney(savingsSum)}</strong></div>
    <div class="legend-row"><span class="dot" style="background:#1d4ed8"></span>Current: <strong>${fmtMoney(currentSum)}</strong></div>
  </div>`;

  const ovList = document.getElementById("overviewAccountsList");
  ovList.innerHTML = customerAccounts.length ? "" : '<div class="muted-text">No accounts yet. Open one from My Accounts.</div>';
  customerAccounts.forEach((acc) => {
    const row = document.createElement("div");
    row.className = "list-row";
    row.innerHTML = `<div><strong>${escapeHtml(acc.accountNumber)}</strong> <span class="badge badge-info">${acc.accountType}</span>
      <div class="muted-text">Status: ${acc.status}</div></div>
      <strong>${fmtMoney(acc.balance)}</strong>`;
    ovList.appendChild(row);
  });

  renderCustomerAccountCards();

  if (customerAccounts.length > 0) {
    // Fetch transactions across ALL owned accounts so Overview reflects full portfolio activity
    const perAccountLists = await Promise.all(
      customerAccounts.map((acc) =>
        apiFetch(`/api/accounts/${encodeURIComponent(acc.accountNumber)}/transactions?limit=25`)
      )
    );
    allCustomerTransactions = perAccountLists
      .flat()
      .sort((a, b) => String(b.timestamp).localeCompare(String(a.timestamp)));

    document.getElementById("kpiTxnCount").textContent = String(allCustomerTransactions.length);

    const trendSel = document.getElementById("trendAccountSel");
    const prevTrend = trendSel.value;
    trendSel.innerHTML = "";
    customerAccounts.forEach((acc) => {
      const opt = document.createElement("option");
      opt.value = acc.accountNumber;
      opt.textContent = acc.accountNumber;
      trendSel.appendChild(opt);
    });
    if (prevTrend && customerAccounts.some((a) => a.accountNumber === prevTrend)) {
      trendSel.value = prevTrend;
    }
    updateBalanceTrendChart();

    let credits = 0;
    let debits = 0;
    allCustomerTransactions.forEach((t) => {
      const a = Number(t.amount);
      if (t.type === "DEPOSIT" || t.type === "TRANSFER_IN" || t.type === "INTEREST") credits += a;
      else debits += a;
    });
    document.getElementById("cashflowChartWrap").innerHTML = renderCashflowBarSvg(credits, debits);

    const recentEl = document.getElementById("overviewRecentTxns");
    recentEl.innerHTML = "";
    allCustomerTransactions.slice(0, 5).forEach((t) => {
      const isCredit = t.type === "DEPOSIT" || t.type === "TRANSFER_IN" || t.type === "INTEREST";
      const row = document.createElement("div");
      row.className = "list-row";
      row.innerHTML = `<div><strong>${escapeHtml(t.accountNumber)} &bull; ${escapeHtml(t.type)}</strong>
        <div class="muted-text">${escapeHtml(t.description || "")}</div></div>
        <strong style="color:${isCredit ? "#0f766e" : "#dc2626"}">${isCredit ? "+" : "-"}${fmtMoney(t.amount)}</strong>`;
      recentEl.appendChild(row);
    });

    await loadStatementTable();
  } else {
    allCustomerTransactions = [];
    document.getElementById("kpiTxnCount").textContent = "0";
    document.getElementById("balanceTrendWrap").innerHTML = '<div class="muted-text">Open an account to see balance trajectory.</div>';
    document.getElementById("cashflowChartWrap").innerHTML = '<div class="muted-text">Open an account to see cashflow analytics.</div>';
    document.getElementById("overviewRecentTxns").innerHTML = "";
    document.getElementById("txnBody").innerHTML = "";
  }
}

function updateBalanceTrendChart() {
  const accNo = document.getElementById("trendAccountSel").value;
  const filtered = allCustomerTransactions.filter((t) => t.accountNumber === accNo);
  document.getElementById("balanceTrendWrap").innerHTML = renderBalanceTrendSvg(filtered);
}

document.getElementById("trendAccountSel").addEventListener("change", updateBalanceTrendChart);

function renderCustomerAccountCards() {
  const grid = document.getElementById("accountsGrid");
  grid.innerHTML = "";
  customerAccounts.forEach((acc) => {
    const isSavings = acc.accountType === "SAVINGS";
    const card = document.createElement("div");
    card.className = `bank-card ${isSavings ? "" : "current-card"} ${acc.status !== "ACTIVE" ? "frozen-card" : ""}`;
    const canClose = acc.status === "ACTIVE" && Number(acc.balance) === 0;
    card.innerHTML = `
      <div>
        <div class="bank-card-top">
          <span>${isSavings ? "SAVINGS • 3.5% p.a." : "CURRENT • ₹10k OD"}</span>
          <span class="badge ${acc.status === "ACTIVE" ? "badge-ok" : "badge-warn"}">${acc.status}</span>
        </div>
        <div class="card-mid">
          <div class="bank-card-no">${escapeHtml(acc.accountNumber)}</div>
          <button type="button" class="copy-btn" data-copy="${escapeHtml(acc.accountNumber)}">Copy #</button>
        </div>
        <div class="bank-card-bal">${fmtMoney(acc.balance)}</div>
      </div>
      <div class="bank-card-foot">
        <span>Opened ${escapeHtml(acc.openedOn)}</span>
        <div class="inline-controls">
          <button type="button" class="btn-xs btn-xs-light card-pay-btn" data-acc="${escapeHtml(acc.accountNumber)}">Transact</button>
          <button type="button" class="btn-xs btn-xs-light card-stmt-btn" data-acc="${escapeHtml(acc.accountNumber)}">Statement</button>
          ${canClose ? `<button type="button" class="btn-xs btn-xs-danger close-acc-btn" data-acc="${escapeHtml(acc.accountNumber)}">Close</button>` : ""}
        </div>
      </div>
    `;
    grid.appendChild(card);
  });

  grid.querySelectorAll(".copy-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      navigator.clipboard?.writeText(btn.dataset.copy);
      btn.textContent = "Copied!";
      pushToast(`Copied ${btn.dataset.copy} to clipboard`);
      setTimeout(() => (btn.textContent = "Copy #"), 1200);
    });
  });

  grid.querySelectorAll(".card-pay-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.getElementById("cashAccount").value = btn.dataset.acc;
      document.getElementById("fromAccount").value = btn.dataset.acc;
      updateLiveBalances(customerAccounts);
      switchTab("transfers");
    });
  });

  grid.querySelectorAll(".card-stmt-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      document.getElementById("stmtAccount").value = btn.dataset.acc;
      switchTab("statements");
      await loadStatementTable();
    });
  });

  grid.querySelectorAll(".close-acc-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      try {
        const res = await apiFetch(`/api/accounts/${encodeURIComponent(btn.dataset.acc)}/close`, { method: "POST" });
        showBanner(appAlert, res.message, false);
        await refreshPortalData();
      } catch (err) {
        showBanner(appAlert, err.message, true);
      }
    });
  });
}

function populateAccountDropdowns(accounts) {
  ["cashAccount", "fromAccount", "stmtAccount"].forEach((id) => {
    const sel = document.getElementById(id);
    const prev = sel.value;
    sel.innerHTML = "";
    accounts.forEach((acc) => {
      const opt = document.createElement("option");
      opt.value = acc.accountNumber;
      opt.textContent = `${acc.accountNumber} (${acc.accountType} — ${fmtMoney(acc.balance)})`;
      sel.appendChild(opt);
    });
    if (prev && accounts.some((a) => a.accountNumber === prev)) {
      sel.value = prev;
    }
  });
  updateLiveBalances(accounts);
}

function updateLiveBalances(accounts) {
  const cashNo = document.getElementById("cashAccount").value;
  const fromNo = document.getElementById("fromAccount").value;
  const cashAcc = accounts.find((a) => a.accountNumber === cashNo);
  const fromAcc = accounts.find((a) => a.accountNumber === fromNo);
  document.getElementById("cashLiveBalance").textContent = cashAcc ? fmtMoney(cashAcc.balance) : "₹0.00";
  document.getElementById("transferLiveBalance").textContent = fromAcc ? fmtMoney(fromAcc.balance) : "₹0.00";
  updateTransferAfterPreview();
}

function updateTransferAfterPreview() {
  const accounts = currentUser?.role === "ADMIN" ? adminAccounts : customerAccounts;
  const fromNo = document.getElementById("fromAccount").value;
  const fromAcc = accounts.find((a) => a.accountNumber === fromNo);
  const amt = Number(document.getElementById("transferAmount").value || 0);
  const preview = document.getElementById("transferAfterPreview");
  if (!fromAcc || amt <= 0) {
    preview.textContent = "";
    return;
  }
  preview.textContent = `After transfer: ${fmtMoney(Number(fromAcc.balance) - amt)}`;
}

document.getElementById("cashAccount").addEventListener("change", () =>
  updateLiveBalances(currentUser?.role === "ADMIN" ? adminAccounts : customerAccounts));
document.getElementById("fromAccount").addEventListener("change", () =>
  updateLiveBalances(currentUser?.role === "ADMIN" ? adminAccounts : customerAccounts));
document.getElementById("transferAmount").addEventListener("input", updateTransferAfterPreview);

document.querySelectorAll(".chip-amt").forEach((chip) => {
  chip.addEventListener("click", () => {
    const input = document.getElementById(chip.dataset.target);
    input.value = (Number(input.value || 0) + Number(chip.dataset.amt)).toFixed(2);
  });
});

document.getElementById("openAccountForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  hideBanners();
  const btn = e.submitter;
  btn.disabled = true;
  try {
    const acc = await apiFetch("/api/accounts", {
      method: "POST",
      body: JSON.stringify({
        accountType: document.getElementById("openType").value,
        initialDeposit: Number(document.getElementById("openDeposit").value)
      })
    });
    showBanner(appAlert, `Opened ${acc.accountType} account ${acc.accountNumber}.`, false);
    await refreshPortalData();
  } catch (err) {
    showBanner(appAlert, err.message, true);
  } finally {
    btn.disabled = false;
  }
});

document.getElementById("cashForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  hideBanners();
  const btn = e.submitter;
  btn.disabled = true;
  const accNo = document.getElementById("cashAccount").value;
  const op = document.getElementById("cashOp").value;
  try {
    const txn = await apiFetch(`/api/accounts/${encodeURIComponent(accNo)}/${op}`, {
      method: "POST",
      body: JSON.stringify({
        amount: Number(document.getElementById("cashAmount").value),
        description: document.getElementById("cashDesc").value.trim()
      })
    });
    document.getElementById("cashAmount").value = "";
    document.getElementById("cashDesc").value = "";
    await refreshPortalData();
    openInfoModal("TRANSACTION COMPLETED", "SRM Trust Bank Receipt", [
      ["Reference ID", `#${txn.txnId}`],
      ["Account", accNo],
      ["Operation", txn.type],
      ["Amount", fmtMoney(txn.amount)],
      ["Balance After", fmtMoney(txn.balanceAfter)],
      ["Timestamp", String(txn.timestamp).replace("T", " ").slice(0, 19)]
    ]);
  } catch (err) {
    showBanner(appAlert, err.message, true);
  } finally {
    btn.disabled = false;
  }
});

document.getElementById("transferForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  hideBanners();
  const from = document.getElementById("fromAccount").value;
  const to = document.getElementById("toAccount").value.trim().toUpperCase();
  if (from === to) {
    showBanner(appAlert, "Source and destination accounts cannot be the same.", true);
    return;
  }
  const btn = e.submitter;
  btn.disabled = true;
  const amt = Number(document.getElementById("transferAmount").value);
  const desc = document.getElementById("transferDesc").value.trim();
  try {
    await apiFetch("/api/transfer", {
      method: "POST",
      body: JSON.stringify({ fromAccount: from, toAccount: to, amount: amt, description: desc })
    });
    document.getElementById("transferAmount").value = "";
    document.getElementById("transferDesc").value = "";
    await refreshPortalData();
    openInfoModal("TRANSACTION COMPLETED", "Atomic Fund Transfer Receipt", [
      ["From Account", from],
      ["To Account", to],
      ["Amount Transferred", fmtMoney(amt)],
      ["Remarks", desc || "Fund transfer"],
      ["Timestamp", new Date().toLocaleString()],
      ["Status", "COMMITTED (ACID)"]
    ]);
  } catch (err) {
    showBanner(appAlert, err.message, true);
  } finally {
    btn.disabled = false;
  }
});

/* ================= STATEMENTS & PRINT / CSV ================= */
async function loadStatementTable() {
  const accNo = document.getElementById("stmtAccount").value;
  const body = document.getElementById("txnBody");
  const strip = document.getElementById("stmtSummaryStrip");
  if (!accNo) {
    body.innerHTML = "";
    strip.innerHTML = "";
    return;
  }

  document.getElementById("printMeta").textContent =
    `Account: ${accNo} | Holder: ${currentUser?.name || ""} | Printed: ${new Date().toLocaleString()}`;

  const from = document.getElementById("stmtFrom").value;
  const to = document.getElementById("stmtTo").value;
  const typeFilter = document.getElementById("stmtType").value;
  const keyword = (document.getElementById("stmtKeyword").value || "").toLowerCase().trim();

  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const query = params.toString() ? `?${params.toString()}` : "";

  try {
    let txns = await apiFetch(`/api/accounts/${encodeURIComponent(accNo)}/transactions${query}`);
    if (typeFilter !== "ALL") {
      txns = txns.filter((t) => t.type === typeFilter);
    }
    if (keyword) {
      txns = txns.filter((t) => (t.description || "").toLowerCase().includes(keyword) || String(t.txnId).includes(keyword));
    }

    let credits = 0;
    let debits = 0;
    txns.forEach((t) => {
      const a = Number(t.amount);
      if (t.type === "DEPOSIT" || t.type === "TRANSFER_IN" || t.type === "INTEREST") credits += a;
      else debits += a;
    });
    strip.innerHTML = `
      <span>Account: <strong>${escapeHtml(accNo)}</strong></span>
      <span>Entries: <strong>${txns.length}</strong></span>
      <span>Credits: <strong style="color:#0f766e">${fmtMoney(credits)}</strong></span>
      <span>Debits: <strong style="color:#dc2626">${fmtMoney(debits)}</strong></span>
    `;

    body.innerHTML = "";
    if (!txns.length) {
      body.innerHTML = '<tr><td colspan="7" class="muted-text">No transactions match the selected filters.</td></tr>';
      return;
    }
    txns.forEach((t) => {
      const isCredit = t.type === "DEPOSIT" || t.type === "TRANSFER_IN" || t.type === "INTEREST";
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>#${t.txnId}</td>
        <td>${escapeHtml(String(t.timestamp).replace("T", " ").slice(0, 19))}</td>
        <td><span class="badge ${isCredit ? "badge-ok" : "badge-info"}">${escapeHtml(t.type)}</span></td>
        <td style="color:${isCredit ? "#0f766e" : "#dc2626"};font-weight:700">${isCredit ? "+" : "-"}${fmtMoney(t.amount)}</td>
        <td><strong>${fmtMoney(t.balanceAfter)}</strong></td>
        <td>${escapeHtml(t.description || "")}</td>
        <td class="no-print"><button type="button" class="btn-xs btn-xs-dark view-rcpt-btn">Receipt</button></td>
      `;
      tr.querySelector(".view-rcpt-btn").addEventListener("click", () => {
        openInfoModal("TRANSACTION RECORD", "SRM Trust Bank Receipt", [
          ["Reference ID", `#${t.txnId}`],
          ["Account", t.accountNumber],
          ["Type", t.type],
          ["Amount", fmtMoney(t.amount)],
          ["Balance After", fmtMoney(t.balanceAfter)],
          ["Description", t.description || "-"],
          ["Timestamp", String(t.timestamp).replace("T", " ").slice(0, 19)]
        ]);
      });
      body.appendChild(tr);
    });
  } catch (err) {
    showBanner(appAlert, err.message, true);
  }
}

document.getElementById("stmtAccount").addEventListener("change", loadStatementTable);
document.getElementById("stmtType").addEventListener("change", loadStatementTable);
document.getElementById("stmtKeyword").addEventListener("input", loadStatementTable);
document.getElementById("loadStmtBtn").addEventListener("click", loadStatementTable);
document.getElementById("printStmtBtn").addEventListener("click", () => window.print());
document.getElementById("resetStmtBtn").addEventListener("click", () => {
  document.getElementById("stmtFrom").value = "";
  document.getElementById("stmtTo").value = "";
  document.getElementById("stmtType").value = "ALL";
  document.getElementById("stmtKeyword").value = "";
  loadStatementTable();
});

document.getElementById("downloadCsvBtn").addEventListener("click", async () => {
  const accNo = document.getElementById("stmtAccount").value;
  if (!accNo) {
    showBanner(appAlert, "Select an account first.", true);
    return;
  }
  try {
    const params = new URLSearchParams();
    const from = document.getElementById("stmtFrom").value;
    const to = document.getElementById("stmtTo").value;
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const q = params.toString() ? `?${params.toString()}` : "";

    const res = await apiFetch(`/api/accounts/${encodeURIComponent(accNo)}/statement/csv${q}`, { rawResponse: true });
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${accNo}_statement.csv`;
    a.click();
    URL.revokeObjectURL(url);
    pushToast(`Downloaded ${accNo}_statement.csv`);
  } catch (err) {
    showBanner(appAlert, err.message, true);
  }
});

/* ================= ADMIN COMMAND CENTER ================= */
async function loadAdminPortal() {
  const [overview, customers, accounts, logsData] = await Promise.all([
    apiFetch("/api/admin/overview"),
    apiFetch("/api/admin/customers"),
    apiFetch("/api/admin/accounts"),
    apiFetch("/api/admin/logs")
  ]);
  adminCustomers = customers;
  adminAccounts = accounts;
  adminLogLines = logsData.lines || [];

  document.getElementById("admTotalDeposits").textContent = fmtMoney(overview.totalDeposits);
  document.getElementById("admTotalCustomers").textContent = String(overview.totalCustomers);
  document.getElementById("admTotalAccounts").textContent = String(overview.totalAccounts);
  document.getElementById("admStatusCounts").textContent =
    `${overview.activeAccounts} Active • ${overview.frozenAccounts} Frozen • ${overview.closedAccounts} Closed`;

  document.getElementById("admLiquidityChartWrap").innerHTML = renderDonutSvg(
    [
      { label: "Savings", value: Number(overview.savingsDeposits), color: "#0d9488" },
      { label: "Current", value: Number(overview.currentDeposits), color: "#d97706" }
    ],
    "Bank Pool",
    fmtMoney(overview.totalDeposits)
  ) + `<div class="chart-legend">
    <div class="legend-row"><span class="dot" style="background:#0d9488"></span>Savings: <strong>${fmtMoney(overview.savingsDeposits)}</strong></div>
    <div class="legend-row"><span class="dot" style="background:#d97706"></span>Current: <strong>${fmtMoney(overview.currentDeposits)}</strong></div>
  </div>`;

  document.getElementById("admStatusChartWrap").innerHTML = renderDonutSvg(
    [
      { label: "Active", value: overview.activeAccounts, color: "#16a34a" },
      { label: "Frozen", value: overview.frozenAccounts, color: "#f59e0b" },
      { label: "Closed", value: overview.closedAccounts, color: "#dc2626" }
    ],
    "Accounts",
    String(overview.totalAccounts)
  ) + `<div class="chart-legend">
    <div class="legend-row"><span class="dot" style="background:#16a34a"></span>Active: <strong>${overview.activeAccounts}</strong></div>
    <div class="legend-row"><span class="dot" style="background:#f59e0b"></span>Frozen: <strong>${overview.frozenAccounts}</strong></div>
    <div class="legend-row"><span class="dot" style="background:#dc2626"></span>Closed: <strong>${overview.closedAccounts}</strong></div>
  </div>`;

  document.getElementById("admTopAccountsChartWrap").innerHTML = renderTopAccountsBarSvg(adminAccounts);

  const feed = document.getElementById("admRecentFeed");
  const recent = overview.recentActivity || [];
  feed.innerHTML = recent.length ? "" : '<div class="muted-text">No transactions recorded in this server session yet.</div>';
  recent.forEach((t) => {
    const row = document.createElement("div");
    row.className = "list-row";
    row.innerHTML = `<div><strong>${escapeHtml(t.accountNumber)}</strong> <span class="badge badge-info">${escapeHtml(t.type)}</span>
      <div class="muted-text">${escapeHtml(t.description || "")}</div></div>
      <strong>${fmtMoney(t.amount)} (Bal: ${fmtMoney(t.balanceAfter)})</strong>`;
    feed.appendChild(row);
  });

  renderAdminCustomersTable();
  renderAdminAccountsTable();
  renderAdminLogs();

  const stmtSel = document.getElementById("stmtAccount");
  const prev = stmtSel.value;
  stmtSel.innerHTML = "";
  adminAccounts.forEach((acc) => {
    const opt = document.createElement("option");
    opt.value = acc.accountNumber;
    opt.textContent = `${acc.accountNumber} (${acc.accountType} — ${fmtMoney(acc.balance)})`;
    stmtSel.appendChild(opt);
  });
  if (prev && adminAccounts.some((a) => a.accountNumber === prev)) stmtSel.value = prev;
  await loadStatementTable();
}

function renderAdminCustomersTable() {
  const q = (document.getElementById("admCustSearch").value || "").toLowerCase().trim();
  const body = document.getElementById("admCustBody");
  body.innerHTML = "";

  adminCustomers
    .filter((c) => !q || c.name.toLowerCase().includes(q) || c.email.toLowerCase().includes(q) || c.phone.includes(q))
    .forEach((c) => {
      const ownedAccs = adminAccounts.filter((a) => a.customerId === c.customerId);
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>#${c.customerId}</td>
        <td><strong>${escapeHtml(c.name)}</strong></td>
        <td>${escapeHtml(c.email)}</td>
        <td>${escapeHtml(c.phone)}</td>
        <td>${escapeHtml(c.address || "-")}</td>
        <td><span class="badge ${c.role === "ADMIN" ? "badge-warn" : "badge-info"}">${c.role}</span></td>
        <td>${ownedAccs.length}</td>
        <td><button type="button" class="btn-xs btn-xs-dark adm-cust-view">View</button></td>
      `;
      tr.querySelector(".adm-cust-view").addEventListener("click", () => {
        const total = ownedAccs.reduce((s, a) => s + Number(a.balance), 0);
        const rows = [
          ["Customer ID", `#${c.customerId}`],
          ["Name", c.name],
          ["Email", c.email],
          ["Phone", c.phone],
          ["Address", c.address || "-"],
          ["Total Balance", fmtMoney(total)],
          ...ownedAccs.map((a) => [`Account ${a.accountNumber}`, `${a.accountType} • ${a.status} • ${fmtMoney(a.balance)}`])
        ];
        openInfoModal("CUSTOMER PROFILE", c.name, rows, false);
      });
      body.appendChild(tr);
    });
}

function renderAdminAccountsTable() {
  const q = (document.getElementById("admAccSearch").value || "").toLowerCase().trim();
  const statusFilter = document.getElementById("admAccStatusFilter").value;
  const custById = Object.fromEntries(adminCustomers.map((c) => [c.customerId, c]));
  const body = document.getElementById("admAccBody");
  body.innerHTML = "";

  adminAccounts
    .filter((acc) => statusFilter === "ALL" || acc.status === statusFilter)
    .filter((acc) => {
      if (!q) return true;
      const owner = custById[acc.customerId];
      return acc.accountNumber.toLowerCase().includes(q)
        || (owner && (owner.name.toLowerCase().includes(q) || owner.email.toLowerCase().includes(q)));
    })
    .forEach((acc) => {
      const owner = custById[acc.customerId];
      const badgeClass = acc.status === "ACTIVE" ? "badge-ok" : (acc.status === "FROZEN" ? "badge-warn" : "badge-danger");
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td><strong>${escapeHtml(acc.accountNumber)}</strong></td>
        <td>${owner ? `${escapeHtml(owner.name)} <span class="muted-text">(${escapeHtml(owner.email)})</span>` : `#${acc.customerId}`}</td>
        <td>${acc.accountType}</td>
        <td><strong>${fmtMoney(acc.balance)}</strong></td>
        <td><span class="badge ${badgeClass}">${acc.status}</span></td>
        <td>${escapeHtml(acc.openedOn)}</td>
        <td class="inline-controls">
          <button type="button" class="btn-xs btn-xs-warn adm-status-btn" data-acc="${escapeHtml(acc.accountNumber)}" data-status="FROZEN">Freeze</button>
          <button type="button" class="btn-xs btn-xs-ok adm-status-btn" data-acc="${escapeHtml(acc.accountNumber)}" data-status="ACTIVE">Activate</button>
          <button type="button" class="btn-xs btn-xs-danger adm-status-btn" data-acc="${escapeHtml(acc.accountNumber)}" data-status="CLOSED">Close</button>
          <button type="button" class="btn-xs btn-xs-dark adm-inspect-btn" data-acc="${escapeHtml(acc.accountNumber)}">Ledger</button>
        </td>
      `;
      body.appendChild(tr);
    });

  body.querySelectorAll(".adm-status-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      try {
        const res = await apiFetch(`/api/admin/accounts/${encodeURIComponent(btn.dataset.acc)}/status`, {
          method: "POST",
          body: JSON.stringify({ status: btn.dataset.status })
        });
        showBanner(appAlert, res.message, false);
        await loadAdminPortal();
      } catch (err) {
        showBanner(appAlert, err.message, true);
      }
    });
  });

  body.querySelectorAll(".adm-inspect-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      document.getElementById("stmtAccount").value = btn.dataset.acc;
      switchTab("statements");
      await loadStatementTable();
    });
  });
}

document.getElementById("admExportAccountsBtn").addEventListener("click", () => {
  const custById = Object.fromEntries(adminCustomers.map((c) => [c.customerId, c]));
  const lines = ["account_number,customer_name,email,type,balance,status,opened_on"];
  adminAccounts.forEach((a) => {
    const owner = custById[a.customerId];
    lines.push(`${a.accountNumber},"${owner?.name || ""}","${owner?.email || ""}",${a.accountType},${a.balance},${a.status},${a.openedOn}`);
  });
  const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "bank_accounts_summary.csv";
  a.click();
  URL.revokeObjectURL(url);
  pushToast("Exported bank_accounts_summary.csv");
});

function renderAdminLogs() {
  const q = (document.getElementById("admLogSearch").value || "").toLowerCase().trim();
  const container = document.getElementById("admLogList");
  const filtered = adminLogLines.filter((l) => !q || l.toLowerCase().includes(q));
  container.innerHTML = filtered.length
    ? filtered.map((l) => `<div class="log-line">${escapeHtml(l)}</div>`).join("")
    : '<div class="muted-text">No audit log lines recorded yet.</div>';
}

document.getElementById("admCustSearch").addEventListener("input", renderAdminCustomersTable);
document.getElementById("admAccSearch").addEventListener("input", renderAdminAccountsTable);
document.getElementById("admAccStatusFilter").addEventListener("change", renderAdminAccountsTable);
document.getElementById("admLogSearch").addEventListener("input", renderAdminLogs);
document.getElementById("refreshLogsBtn").addEventListener("click", loadAdminPortal);

document.getElementById("runInterestBtn").addEventListener("click", async () => {
  hideBanners();
  try {
    const res = await apiFetch("/api/admin/interest/run", { method: "POST", body: "{}" });
    showBanner(appAlert, res.message, false);
    await loadAdminPortal();
  } catch (err) {
    showBanner(appAlert, err.message, true);
  }
});

/* ================= FINANCIAL CALCULATORS ================= */
document.querySelectorAll(".calc-tab").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".calc-tab").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    document.querySelectorAll(".calc-panel").forEach((p) => p.classList.add("hidden"));
    document.getElementById(`calc-${btn.dataset.calc}`).classList.remove("hidden");
  });
});

function syncRangePair(inputId, rangeId, fn) {
  const numEl = document.getElementById(inputId);
  const rngEl = document.getElementById(rangeId);
  numEl.addEventListener("input", () => {
    rngEl.value = numEl.value;
    fn();
  });
  rngEl.addEventListener("input", () => {
    numEl.value = rngEl.value;
    fn();
  });
}

function updateEmiCalc() {
  const p = Math.max(0, Number(document.getElementById("emiPrincipal").value));
  const annual = Math.max(0, Number(document.getElementById("emiRate").value));
  const n = Math.max(1, Math.min(360, Number(document.getElementById("emiMonths").value)));
  const r = annual / 12 / 100;
  const emi = r === 0 ? p / n : (p * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
  const total = emi * n;
  const interest = Math.max(0, total - p);

  document.getElementById("emiMonthlyOut").textContent = fmtMoney(emi);
  document.getElementById("emiPrincipalOut").textContent = fmtMoney(p);
  document.getElementById("emiInterestOut").textContent = fmtMoney(interest);
  document.getElementById("emiTotalOut").textContent = fmtMoney(total);
  document.getElementById("emiChartWrap").innerHTML = renderDonutSvg(
    [
      { label: "Principal", value: p, color: "#0d9488" },
      { label: "Interest", value: interest, color: "#d97706" }
    ],
    "Total Pay",
    fmtMoney(total)
  );

  const schedBody = document.getElementById("emiScheduleBody");
  schedBody.innerHTML = "";
  let bal = p;
  let yrPrincipal = 0;
  let yrInterest = 0;
  for (let m = 1; m <= n; m++) {
    const mInt = bal * r;
    const mPrin = Math.min(bal, emi - mInt);
    bal = Math.max(0, bal - mPrin);
    yrPrincipal += mPrin;
    yrInterest += mInt;
    if (m % 12 === 0 || m === n) {
      const yr = Math.ceil(m / 12);
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>Year ${yr}</td><td>${fmtMoney(yrPrincipal)}</td><td>${fmtMoney(yrInterest)}</td><td><strong>${fmtMoney(bal)}</strong></td>`;
      schedBody.appendChild(tr);
      yrPrincipal = 0;
      yrInterest = 0;
    }
  }
}

function updateFdCalc() {
  const p = Math.max(0, Number(document.getElementById("fdPrincipal").value));
  const r = Math.max(0, Number(document.getElementById("fdRate").value)) / 100;
  const t = Math.max(0, Math.min(25, Number(document.getElementById("fdYears").value)));
  const freq = Math.max(1, Number(document.getElementById("fdFreq").value));
  const maturity = p * Math.pow(1 + r / freq, freq * t);
  const interest = Math.max(0, maturity - p);
  const gainPct = p > 0 ? (interest / p) * 100 : 0;

  document.getElementById("fdMaturityOut").textContent = fmtMoney(maturity);
  document.getElementById("fdPrincipalOut").textContent = fmtMoney(p);
  document.getElementById("fdInterestOut").textContent = fmtMoney(interest);
  document.getElementById("fdGainOut").textContent = `${gainPct.toFixed(1)}%`;
  document.getElementById("fdChartWrap").innerHTML = renderDonutSvg(
    [
      { label: "Deposit", value: p, color: "#1d4ed8" },
      { label: "Interest", value: interest, color: "#0d9488" }
    ],
    "Maturity",
    fmtMoney(maturity)
  );

  const schedBody = document.getElementById("fdScheduleBody");
  schedBody.innerHTML = "";
  for (let yr = 1; yr <= Math.ceil(t); yr++) {
    const val = p * Math.pow(1 + r / freq, freq * yr);
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>Year ${yr}</td><td>${fmtMoney(p)}</td><td>${fmtMoney(val - p)}</td><td><strong>${fmtMoney(val)}</strong></td>`;
    schedBody.appendChild(tr);
  }
}

function updateSavingsCalc() {
  const start = Math.max(0, Number(document.getElementById("savStart").value));
  const monthly = Math.max(0, Number(document.getElementById("savMonthly").value));
  const years = Math.max(1, Math.min(30, Number(document.getElementById("savYears").value)));
  const mRate = 0.035 / 12;
  let bal = start;

  const schedBody = document.getElementById("savScheduleBody");
  schedBody.innerHTML = "";
  for (let yr = 1; yr <= years; yr++) {
    for (let m = 0; m < 12; m++) {
      bal = (bal + monthly) * (1 + mRate);
    }
    const yrContrib = start + monthly * yr * 12;
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>Year ${yr}</td><td>${fmtMoney(yrContrib)}</td><td>${fmtMoney(Math.max(0, bal - yrContrib))}</td><td><strong>${fmtMoney(bal)}</strong></td>`;
    schedBody.appendChild(tr);
  }

  const contrib = start + monthly * years * 12;
  const interest = Math.max(0, bal - contrib);
  document.getElementById("savTotalOut").textContent = fmtMoney(bal);
  document.getElementById("savContribOut").textContent = fmtMoney(contrib);
  document.getElementById("savInterestOut").textContent = fmtMoney(interest);
  document.getElementById("savMonthlyIntOut").textContent = fmtMoney(bal * mRate);
  document.getElementById("savChartWrap").innerHTML = renderDonutSvg(
    [
      { label: "Contributed", value: contrib, color: "#0b2545" },
      { label: "Interest", value: interest, color: "#0d9488" }
    ],
    "Future Value",
    fmtMoney(bal)
  );
}

function runAllCalculators() {
  updateEmiCalc();
  updateFdCalc();
  updateSavingsCalc();
}

syncRangePair("emiPrincipal", "emiPrincipalRange", updateEmiCalc);
syncRangePair("emiRate", "emiRateRange", updateEmiCalc);
syncRangePair("emiMonths", "emiMonthsRange", updateEmiCalc);
["fdPrincipal", "fdRate", "fdYears", "fdFreq"].forEach((id) =>
  document.getElementById(id).addEventListener("input", updateFdCalc));
["savStart", "savMonthly", "savYears"].forEach((id) =>
  document.getElementById(id).addEventListener("input", updateSavingsCalc));

/* ================= PROFILE & PASSWORD ================= */
function renderProfileTab() {
  if (!currentUser) return;
  document.getElementById("profName").value = currentUser.name || "";
  document.getElementById("profEmail").value = currentUser.email || "";
  document.getElementById("profPhone").value = currentUser.phone || "";
  document.getElementById("profAddress").value = currentUser.address || "";
  document.getElementById("profileMetaBox").innerHTML = `
    <div class="profile-row"><span>Customer ID</span><strong>#${currentUser.customerId}</strong></div>
    <div class="profile-row"><span>Access Role</span><strong>${escapeHtml(currentUser.role)}</strong></div>
  `;
}

document.getElementById("editProfileForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  hideBanners();
  try {
    const updated = await apiFetch("/api/auth/profile", {
      method: "PUT",
      body: JSON.stringify({
        name: document.getElementById("profName").value.trim(),
        phone: document.getElementById("profPhone").value.trim(),
        address: document.getElementById("profAddress").value.trim()
      })
    });
    currentUser = updated;
    sessionStorage.setItem("bankUser", JSON.stringify(updated));
    document.getElementById("userNameLabel").textContent = updated.name;
    document.getElementById("userAvatar").textContent = (updated.name || "U").charAt(0).toUpperCase();
    showBanner(appAlert, "Profile updated successfully.", false);
  } catch (err) {
    showBanner(appAlert, err.message, true);
  }
});

document.getElementById("changePasswordForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  hideBanners();
  try {
    const res = await apiFetch("/api/auth/change-password", {
      method: "POST",
      body: JSON.stringify({
        oldPassword: document.getElementById("cpOld").value,
        newPassword: document.getElementById("cpNew").value
      })
    });
    document.getElementById("cpOld").value = "";
    document.getElementById("cpNew").value = "";
    showBanner(appAlert, res.message, false);
  } catch (err) {
    showBanner(appAlert, err.message, true);
  }
});

/* ================= FLOATING CHATBOT WIDGET ================= */
const chatPanel = document.getElementById("chatPanel");
const chatMessages = document.getElementById("chatMessages");

document.getElementById("chatToggleBtn").addEventListener("click", () => chatPanel.classList.toggle("hidden"));
document.getElementById("chatCloseBtn").addEventListener("click", () => chatPanel.classList.add("hidden"));

function appendChatBubble(text, who) {
  const div = document.createElement("div");
  div.className = `msg ${who}`;
  div.textContent = text;
  chatMessages.appendChild(div);
  chatMessages.scrollTop = chatMessages.scrollHeight;
}

function localPreLoginFaq(q) {
  const lower = q.toLowerCase();
  if (lower.includes("minimum") || lower.includes("savings")) {
    return "Savings accounts require a minimum balance of ₹1,000, allow 5 withdrawals/month, and earn 3.5% p.a. Sign in to use the full server assistant.";
  }
  if (lower.includes("overdraft") || lower.includes("current")) {
    return "Current accounts include a ₹10,000 overdraft limit and earn 0% interest. Sign in for full assistant help.";
  }
  if (lower.includes("export") || lower.includes("csv")) {
    return "Sign in, open Statements & Export, and click Download CSV or Print / PDF.";
  }
  return "Please sign in to ask the live banking assistant (or click a demo login button to explore).";
}

async function sendChatPrompt(msg) {
  appendChatBubble(msg, "user");
  if (!authToken) {
    appendChatBubble(localPreLoginFaq(msg), "bot");
    return;
  }
  try {
    const data = await apiFetch("/api/chat", {
      method: "POST",
      body: JSON.stringify({ message: msg, history: chatHistory.slice(-6) })
    });
    chatHistory.push({ role: "user", content: msg });
    chatHistory.push({ role: "assistant", content: data.reply });
    appendChatBubble(data.reply, "bot");
  } catch (err) {
    appendChatBubble(`Error: ${err.message}`, "bot");
  }
}

document.querySelectorAll(".chat-chip").forEach((chip) => {
  chip.addEventListener("click", () => sendChatPrompt(chip.dataset.q));
});

document.getElementById("chatForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const input = document.getElementById("chatInput");
  const msg = input.value.trim();
  if (!msg) return;
  input.value = "";
  await sendChatPrompt(msg);
});

renderAppState();
