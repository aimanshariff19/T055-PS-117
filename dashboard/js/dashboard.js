export function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  })[character]);
}

export function icon(name, className = "") {
  return `<svg class="${escapeHtml(className)}" aria-hidden="true"><use href="assets/icons/sprite.svg#${escapeHtml(name)}"></use></svg>`;
}

export function pageHeader(eyebrow, title, description, actions = "") {
  return `<section class="page-heading">
    <div><div class="eyebrow">${escapeHtml(eyebrow)}</div><h1>${escapeHtml(title)}</h1>
    <p class="page-description">${escapeHtml(description)}</p></div>
    ${actions ? `<div class="heading-actions">${actions}</div>` : ""}
  </section>`;
}

export function button(label, action, style = "secondary", iconName = "") {
  return `<button class="button button-${style}" type="button" data-action="${escapeHtml(action)}">${iconName ? icon(iconName) : ""}${escapeHtml(label)}</button>`;
}

export function note(message, type = "") {
  return `<div class="data-note ${type}">${icon("layers")}<span>${escapeHtml(message)}</span></div>`;
}

export function loadingState(message = "Loading data from the Java service…") {
  return `<div class="loading-state"><span class="spinner" aria-hidden="true"></span><span>${escapeHtml(message)}</span></div>`;
}

export function emptyState(title, message, iconName = "file") {
  return `<div class="empty-state"><div class="empty-icon">${icon(iconName)}</div><h3>${escapeHtml(title)}</h3><p>${escapeHtml(message)}</p></div>`;
}

export function errorState(error, retryAction = "retry") {
  return `<div class="error-state"><div class="empty-icon">${icon("bell")}</div><h3>Data could not be loaded</h3>
    <p>${escapeHtml(error?.message || "The API returned an unexpected response.")}</p>
    ${button("Try again", retryAction, "secondary", "refresh")}</div>`;
}

export function statusBadge(value) {
  const normalized = String(value || "UNKNOWN").toUpperCase();
  let style = "neutral";
  if (["ALLOCATED", "VALID", "VERIFIED", "PROCESSED", "BOOKED"].includes(normalized)) style = "success";
  else if (["DOC_BLOCK", "INVALID", "REJECTED", "FAILED", "CANCELLED"].includes(normalized)) style = "danger";
  else if (["PENDING", "NOT_PROCESSED", "UNVERIFIED", "INCOMPLETE", "NOT_TRACKED"].includes(normalized)) style = "pending";
  else if (["TATKAL", "SENIOR_CITIZEN"].includes(normalized)) style = "purple";
  else if (["NORMAL", "MINOR", "DIFFERENTLY_ABLED", "SELF_ATTESTED", "VALIDATED"].includes(normalized)) style = "info";
  return `<span class="badge ${style}">${escapeHtml(normalized.replaceAll("_", " "))}</span>`;
}

export function formatDate(value, options = { dateStyle: "medium" }) {
  if (!value) return "Not recorded";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? escapeHtml(value) : new Intl.DateTimeFormat(undefined, options).format(date);
}

export function formatNumber(value) {
  return value == null ? "—" : new Intl.NumberFormat().format(value);
}

export function formatCurrency(value) {
  if (value == null) return "Not available";
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(value));
}

export function progressBar(percent) {
  if (percent == null) return `<span class="muted">Not processed</span>`;
  const safePercent = Math.max(0, Math.min(100, Number(percent)));
  return `<div class="capacity-indicator"><div class="capacity-track"><div class="capacity-fill" style="width:${safePercent}%"></div></div><span class="capacity-percent">${safePercent}%</span></div>`;
}

export function toast(message, error = false) {
  let region = document.querySelector(".toast-region");
  if (!region) {
    region = document.createElement("div");
    region.className = "toast-region";
    region.setAttribute("aria-live", "polite");
    document.body.append(region);
  }
  const item = document.createElement("div");
  item.className = `toast${error ? " error" : ""}`;
  item.textContent = message;
  region.append(item);
  window.setTimeout(() => item.remove(), 4200);
}

export function formatError(error) {
  return error?.message || "An unexpected error occurred.";
}
