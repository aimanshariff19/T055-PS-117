import { getDocuments } from "./api.js";
import {
  emptyState, errorState, escapeHtml, formatDate, loadingState, note, pageHeader, statusBadge,
} from "./dashboard.js";

export async function renderPage(root) {
  root.innerHTML = loadingState("Reading source document records…");
  let payload;
  try {
    payload = await getDocuments();
  } catch (error) {
    root.innerHTML = pageHeader("Verification desk", "Document verification", "Review document records and Java validation feedback.") + errorState(error);
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => renderPage(root));
    return;
  }
  const rows = payload.documents || [];
  root.innerHTML = `${pageHeader("Verification desk", "Document verification", "Verification status comes from documents.csv; validation messages are exposed only after Java processing.")}
    ${note("The CSV schema contains no verification timestamp. “Not recorded” is shown rather than an inferred date.", "warning")}
    <section class="panel table-panel"><div class="panel-header"><div><h2 class="panel-title">Document register</h2><div class="panel-subtitle">${rows.length} source document records</div></div></div>
      <div class="table-wrap">${rows.length ? `<table><thead><tr><th>Document ID</th><th>Application</th><th>Applicant</th><th>Document type</th><th>Verification</th><th>Expiry date</th><th>Processed validation</th><th>Validation feedback</th></tr></thead><tbody>${rows.map((row) => `<tr>
        <td class="primary-cell mono">${escapeHtml(row.documentId)}</td><td class="mono">${escapeHtml(row.applicationId)}</td>
        <td>${escapeHtml(row.applicantName || "Not recorded")}</td><td>${escapeHtml(row.documentType)}</td>
        <td>${statusBadge(row.verificationStatus)}</td><td>${formatDate(row.expiryDate)}</td>
        <td>${statusBadge(row.validationStatus)}</td><td>${escapeHtml(row.validationErrors?.join("; ") || "No validation message returned")}</td>
      </tr>`).join("")}</tbody></table>` : emptyState("No documents found", "The configured documents.csv has no document rows.", "check")}</div>
    </section>`;
}
