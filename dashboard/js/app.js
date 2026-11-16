import { getHealth } from "./api.js";
import * as overview from "./overview.js";
import * as applications from "./applications.js";
import * as appointments from "./appointments.js";
import * as documents from "./documents.js";
import * as centres from "./centres.js";
import * as billing from "./billing.js";
import * as reports from "./reports.js";
import * as priorities from "./priorities.js";
import * as settings from "./settings.js";
import { escapeHtml, icon, loadingState, toast } from "./dashboard.js";

const navigation = [
  { section: "Workspace", items: [
    { id: "overview", label: "Overview", icon: "grid" },
    { id: "applications", label: "Applications", icon: "file" },
    { id: "appointments", label: "Appointments", icon: "calendar" },
    { id: "documents", label: "Document verification", icon: "check" },
    { id: "centres", label: "Appointment centres", icon: "building" },
  ] },
  { section: "Management", items: [
    { id: "priorities", label: "Priority management", icon: "layers" },
    { id: "billing", label: "Fee & refunds", icon: "wallet" },
    { id: "reports", label: "Reports", icon: "chart" },
  ] },
  { section: "System", items: [
    { id: "settings", label: "Settings", icon: "settings" },
  ] },
];

const pages = { overview, applications, appointments, documents, centres, priorities, billing, reports, settings };
const titles = Object.fromEntries(navigation.flatMap((group) => group.items.map((item) => [item.id, item.label])));
const app = document.querySelector("#app");
let currentRoute = "";

function renderShell() {
  app.innerHTML = `<div class="app-shell">
    <aside class="sidebar" id="sidebar" aria-label="Primary navigation">
      <a class="brand" href="#overview"><img src="assets/logo.svg" alt=""><span><span class="brand-name">PS-117</span><span class="brand-subtitle">Service operations</span></span></a>
      <nav>${navigation.map((group) => `<div class="nav-section-label">${escapeHtml(group.section)}</div><div class="nav-list">${group.items.map((item) => `<a class="nav-link" data-route="${item.id}" href="#${item.id}">${icon(item.icon)}<span>${escapeHtml(item.label)}</span></a>`).join("")}</div>`).join("")}</nav>
      <div class="sidebar-bottom">
        <div class="sidebar-note"><strong>Passport Seva · PS-117</strong><span>Local allocation and verification operations workspace</span></div>
        <div class="profile"><div class="avatar">PS</div><div><div class="profile-name">Service operator</div><div class="profile-role">Local administration</div></div></div>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <button class="icon-button mobile-menu" type="button" aria-label="Open navigation" data-action="menu">${icon("menu")}</button>
        <div class="breadcrumb">PS-117 <span aria-hidden="true">/</span> <strong id="breadcrumb-title">Overview</strong></div>
        <div class="topbar-right">
          <label class="search-box">${icon("search")}<input type="search" id="global-search" placeholder="Search applications…" aria-label="Search applications"></label>
          <button class="icon-button" type="button" aria-label="Refresh current view" data-action="refresh">${icon("refresh")}</button>
          <span class="badge pending" id="connection-status">Connecting</span>
        </div>
      </header>
      <main class="main-content" id="page-view" tabindex="-1">${loadingState("Starting dashboard…")}</main>
    </div>
    <div id="mobile-scrim"></div>
  </div>`;
  app.addEventListener("click", handleShellClick);
  app.querySelector("#global-search").addEventListener("keydown", (event) => {
    if (event.key === "Enter" && event.currentTarget.value.trim()) {
      window.pendingApplicationSearch = event.currentTarget.value.trim();
      navigate("applications");
    }
  });
  checkHealth();
  renderRoute();
}

function handleShellClick(event) {
  const routeLink = event.target.closest("[data-route]");
  if (routeLink) {
    closeMobileNavigation();
    return;
  }
  if (event.target.closest('[data-action="refresh"]')) {
    if (currentRoute) renderRoute(true);
    return;
  }
  if (event.target.closest('[data-action="menu"]')) {
    const sidebar = app.querySelector("#sidebar");
    const open = sidebar.classList.toggle("open");
    const scrim = app.querySelector("#mobile-scrim");
    scrim.className = open ? "sidebar-scrim" : "";
    scrim.onclick = open ? closeMobileNavigation : null;
  }
}

function closeMobileNavigation() {
  app.querySelector("#sidebar")?.classList.remove("open");
  const scrim = app.querySelector("#mobile-scrim");
  if (scrim) {
    scrim.className = "";
    scrim.onclick = null;
  }
}

function navigate(route) {
  const target = `#${route}`;
  if (window.location.hash === target) renderRoute(true);
  else window.location.hash = route;
}

async function renderRoute(force = false) {
  const route = window.location.hash.replace(/^#/, "") || "overview";
  if (!pages[route]) {
    window.history.replaceState(null, "", "#overview");
    currentRoute = "";
    return renderRoute(true);
  }
  if (!force && route === currentRoute) return;
  currentRoute = route;
  app.querySelector("#breadcrumb-title").textContent = titles[route];
  app.querySelectorAll("[data-route]").forEach((link) => {
    const active = link.dataset.route === route;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });
  const view = app.querySelector("#page-view");
  view.innerHTML = loadingState();
  await pages[route].renderPage(view);
  if (route === "applications" && window.pendingApplicationSearch) {
    const search = view.querySelector("#application-search");
    if (search) {
      search.value = window.pendingApplicationSearch;
      search.dispatchEvent(new Event("input", { bubbles: true }));
    }
    window.pendingApplicationSearch = "";
  }
  view.focus({ preventScroll: true });
}

async function checkHealth() {
  const indicator = app.querySelector("#connection-status");
  try {
    await getHealth();
    indicator.className = "badge success";
    indicator.textContent = "API online";
    indicator.title = "Connected to the local Java API";
  } catch (error) {
    indicator.className = "badge danger";
    indicator.textContent = "API offline";
    indicator.title = error.message;
    toast(error.message, true);
  }
}

window.addEventListener("hashchange", () => renderRoute());
window.addEventListener("unhandledrejection", (event) => {
  toast(event.reason?.message || "Unexpected dashboard error.", true);
});
renderShell();
