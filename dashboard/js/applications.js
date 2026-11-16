import { getApplications } from "./api.js";
import {
  emptyState, errorState, escapeHtml, formatDate, icon, loadingState,
  pageHeader, statusBadge,
} from "./dashboard.js";

export async function renderPage(root) {
  root.innerHTML = loadingState();
  let payload;
  try {
    payload = await getApplications();
  } catch (error) {
    root.innerHTML = pageHeader("Case management", "Applications", "Search, review, and inspect applications from the configured CSV dataset.") + errorState(error);
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => renderPage(root));
    return;
  }

  const records = payload.applications || [];
  root.innerHTML = `${pageHeader("Case management", "Applications", "Search and review application records. Processing status is populated only after the Java engine runs.",
    `<button class="button button-secondary" type="button" data-action="refresh">${icon("refresh")}Refresh</button>`)}
    ${payload.processed ? "" : `<div class="data-note warning">${icon("layers")}<span>Applications are loaded from CSV. Application decisions and priority categories are not shown until the Java engine processes this dataset.</span></div>`}
    <section class="panel table-panel">
      <div class="panel-header"><div><h2 class="panel-title">Application register</h2><div class="panel-subtitle">${records.length} source records</div></div></div>
      <div class="table-toolbar">
        <label class="table-search">${icon("search")}<input id="application-search" type="search" placeholder="Search ID, applicant or centre" aria-label="Search applications"></label>
        <select class="select-control" id="status-filter" aria-label="Filter by application status"><option value="">All statuses</option></select>
        <select class="select-control" id="priority-filter" aria-label="Filter by priority"><option value="">All priorities</option></select>
      </div>
      <div id="application-results"></div>
      <div id="application-pagination"></div>
    </section>
    <dialog class="modal" id="application-detail"><div class="modal-header"><div><div class="eyebrow">Record detail</div><h2>Application</h2></div><button class="icon-button" type="button" data-action="close-detail" aria-label="Close">${icon("chevron")}</button></div><div class="modal-body" id="application-detail-body"></div></dialog>`;

  const statuses = [...new Set(records.map((row) => row.applicationStatus).filter(Boolean))].sort();
  const priorities = [...new Set(records.flatMap((row) => [row.priorityCategory, ...(row.priorityFlags || [])]).filter(Boolean))].sort();
  root.querySelector("#status-filter").insertAdjacentHTML("beforeend", statuses.map((item) => `<option value="${escapeHtml(item)}">${escapeHtml(item.replaceAll("_", " "))}</option>`).join(""));
  root.querySelector("#priority-filter").insertAdjacentHTML("beforeend", priorities.map((item) => `<option value="${escapeHtml(item)}">${escapeHtml(item.replaceAll("_", " "))}</option>`).join(""));

  const state = { search: "", status: "", priority: "", page: 1, pageSize: 8, sortBy: "applicationDate", direction: "desc" };
  const results = root.querySelector("#application-results");
  const pagination = root.querySelector("#application-pagination");

  function draw() {
    const filtered = records.filter((row) => {
      const search = state.search.toLowerCase();
      const matchText = !search || [row.applicationId, row.applicantName, row.applicantId, row.preferredCentreId]
        .some((value) => String(value || "").toLowerCase().includes(search));
      const matchStatus = !state.status || row.applicationStatus === state.status;
      const categories = [row.priorityCategory, ...(row.priorityFlags || [])];
      const matchPriority = !state.priority || categories.includes(state.priority);
      return matchText && matchStatus && matchPriority;
    }).sort((left, right) => {
      const a = String(left[state.sortBy] ?? "").toLowerCase();
      const b = String(right[state.sortBy] ?? "").toLowerCase();
      return (a < b ? -1 : a > b ? 1 : 0) * state.direction;
    });
    const pages = Math.max(1, Math.ceil(filtered.length / state.pageSize));
    state.page = Math.min(state.page, pages);
    const visible = filtered.slice((state.page - 1) * state.pageSize, state.page * state.pageSize);
    if (!visible.length) {
      results.innerHTML = emptyState("No matching applications", records.length
        ? "Try changing the search text or filters."
        : "No application rows were found in the configured CSV file.", "file");
      pagination.innerHTML = "";
      return;
    }

    results.innerHTML = `<div class="table-wrap"><table><thead><tr>
      <th><button class="sortable" data-sort="applicationId">Application ID</button></th>
      <th><button class="sortable" data-sort="applicantName">Applicant</button></th>
      <th>Passport type</th><th><button class="sortable" data-sort="applicationDate">Application date</button></th>
      <th>Priority</th><th>Documents</th><th>Status</th><th>Actions</th>
      </tr></thead><tbody>${visible.map((row) => `<tr>
        <td class="primary-cell mono">${escapeHtml(row.applicationId)}</td>
        <td><span class="primary-cell">${escapeHtml(row.applicantName)}</span><span class="secondary-cell">${escapeHtml(row.applicantId)}</span></td>
        <td>${escapeHtml(row.passportType)}</td>
        <td>${formatDate(row.applicationDate)}</td>
        <td>${row.priorityCategory ? statusBadge(row.priorityCategory) : row.priorityFlags?.length ? row.priorityFlags.map(statusBadge).join(" ") : `<span class="muted">Not calculated</span>`}</td>
        <td>${statusBadge(row.documentStatus)}</td>
        <td>${statusBadge(row.applicationStatus)}</td>
        <td><button class="row-action" type="button" data-detail="${escapeHtml(row.applicationId)}">View ${icon("arrow")}</button></td>
      </tr>`).join("")}</tbody></table></div>`;
    const start = (state.page - 1) * state.pageSize + 1;
    const end = Math.min(state.page * state.pageSize, filtered.length);
    pagination.innerHTML = `<div class="table-footer"><span>Showing ${start}–${end} of ${filtered.length}</span>
      <div class="pagination"><button class="page-button" data-page="${state.page - 1}" ${state.page <= 1 ? "disabled" : ""} aria-label="Previous page">‹</button>
      <span class="page-button active" aria-current="page">${state.page}</span>
      <button class="page-button" data-page="${state.page + 1}" ${state.page >= pages ? "disabled" : ""} aria-label="Next page">›</button></div></div>`;
  }

  root.querySelector("#application-search").addEventListener("input", (event) => {
    state.search = event.target.value;
    state.page = 1;
    draw();
  });
  root.querySelector("#status-filter").addEventListener("change", (event) => {
    state.status = event.target.value;
    state.page = 1;
    draw();
  });
  root.querySelector("#priority-filter").addEventListener("change", (event) => {
    state.priority = event.target.value;
    state.page = 1;
    draw();
  });
  root.addEventListener("click", (event) => {
    const sortButton = event.target.closest("[data-sort]");
    if (sortButton) {
      const key = sortButton.dataset.sort;
      state.direction = state.sortBy === key ? state.direction * -1 : 1;
      state.sortBy = key;
      draw();
      return;
    }
    const pageButton = event.target.closest("[data-page]");
    if (pageButton && !pageButton.disabled) {
      state.page = Number(pageButton.dataset.page);
      draw();
      return;
    }
    const detailButton = event.target.closest("[data-detail]");
    if (detailButton) {
      const row = records.find((item) => item.applicationId === detailButton.dataset.detail);
      if (row) showDetail(row);
      return;
    }
    if (event.target.closest('[data-action="refresh"]')) renderPage(root);
    if (event.target.closest('[data-action="close-detail"]')) root.querySelector("#application-detail").close();
  });

  function showDetail(row) {
    const fields = [
      ["Application ID", row.applicationId], ["Applicant", row.applicantName],
      ["Applicant reference", row.applicantId], ["Date of birth", row.dateOfBirth],
      ["Application date", row.applicationDate], ["Passport type", row.passportType],
      ["Centre preference", row.preferredCentreId], ["Priority flags", row.priorityFlags?.join(", ") || "None recorded"],
      ["Resolved category", row.priorityCategory || "Not calculated"], ["Document status", row.documentStatus],
      ["Validation", row.validationStatus], ["Application status", row.applicationStatus],
      ["Validation notes", row.validationErrors?.join("; ") || "None returned"],
    ];
    root.querySelector("#application-detail-body").innerHTML = `<div class="detail-grid">${fields.map(([label, value]) => `<div class="detail-item"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value || "Not recorded")}</strong></div>`).join("")}</div>`;
    root.querySelector("#application-detail").showModal();
  }

  draw();
}
