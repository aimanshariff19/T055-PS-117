import { getBilling } from "./api.js";
import {
  emptyState, errorState, escapeHtml, formatCurrency, formatNumber, loadingState,
  note, pageHeader, statusBadge,
} from "./dashboard.js";

export async function renderPage(root) {
  root.innerHTML = loadingState("Loading fee calculation data…");
  let payload;
  try {
    payload = await getBilling();
  } catch (error) {
    root.innerHTML = pageHeader("Finance operations", "Fee & refund management", "Fee estimates are sourced from the Java FeeCalculator; payment tracking is not available.") + errorState(error);
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => renderPage(root));
    return;
  }
  root.innerHTML = `${pageHeader("Finance operations", "Fee & refund management", "View Java-calculated fee estimates without implying payment collection or refund completion.")}
    ${note(payload.message, "warning")}
    <section class="stats-grid">
      <article class="stat-card"><div class="stat-top"><span>Estimated fees</span><span class="stat-icon">${'<svg aria-hidden="true"><use href="assets/icons/sprite.svg#wallet"></use></svg>'}</span></div><div class="stat-value">${formatCurrency(payload.totalEstimatedFees)}</div><div class="stat-foot">${payload.feeEstimatesAvailable ? "Java FeeCalculator total estimate" : "Available after processing"}</div></article>
      <article class="stat-card"><div class="stat-top"><span>Collected</span><span class="stat-icon green">${'<svg aria-hidden="true"><use href="assets/icons/sprite.svg#check"></use></svg>'}</span></div><div class="stat-value">${formatCurrency(payload.totalCollected)}</div><div class="stat-foot">No payment ledger in the backend</div></article>
      <article class="stat-card"><div class="stat-top"><span>Refund records</span><span class="stat-icon amber">${'<svg aria-hidden="true"><use href="assets/icons/sprite.svg#refresh"></use></svg>'}</span></div><div class="stat-value">${payload.refundStatusAvailable ? "Available" : "Not tracked"}</div><div class="stat-foot">No refund transactions are recorded</div></article>
      <article class="stat-card"><div class="stat-top"><span>Fee estimates</span><span class="stat-icon purple">${'<svg aria-hidden="true"><use href="assets/icons/sprite.svg#file"></use></svg>'}</span></div><div class="stat-value">${formatNumber(payload.transactions?.length || 0)}</div><div class="stat-foot">Rows from the Java fee calculator</div></article>
    </section>
    <section class="panel table-panel"><div class="panel-header"><div><h2 class="panel-title">Fee estimates</h2><div class="panel-subtitle">Amounts are estimates, not settled transactions</div></div></div>
      <div class="table-wrap">${payload.transactions?.length ? `<table><thead><tr><th>Application ID</th><th>Applicant</th><th>Fee category</th><th>Estimated amount</th><th>Payment status</th><th>Refund status</th></tr></thead><tbody>${payload.transactions.map((row) => `<tr>
        <td class="primary-cell mono">${escapeHtml(row.applicationId)}</td><td>${escapeHtml(row.applicantName || "Not recorded")}</td>
        <td>${escapeHtml(row.feeCategory || "Not recorded")}</td><td class="primary-cell">${formatCurrency(row.estimatedAmount)}</td>
        <td>${statusBadge(row.paymentStatus)}</td><td>${statusBadge(row.refundStatus)}</td>
      </tr>`).join("")}</tbody></table>` : emptyState("No fee estimates yet", "Run the Java engine to produce per-application estimates. Collected fees and refunds remain unavailable.", "wallet")}</div>
    </section>`;
}
