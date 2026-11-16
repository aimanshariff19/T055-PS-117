import { getApplications, getAppointments, getCentres, getSummary, processApplications } from "./api.js";
import {
  emptyState, errorState, escapeHtml, formatDate, formatNumber, icon,
  loadingState, note, pageHeader, progressBar, statusBadge, toast,
} from "./dashboard.js";

export async function renderPage(root) {
  root.innerHTML = loadingState("Loading live application and capacity data…");
  let summary;
  let applicationData;
  let appointmentData;
  let centreData;
  try {
    [summary, applicationData, appointmentData, centreData] = await Promise.all([
      getSummary(), getApplications(), getAppointments(), getCentres(),
    ]);
  } catch (error) {
    root.innerHTML = pageHeader("Operations overview", "Service overview", "Live view of the configured CSV dataset and the latest Java processing run.") + errorState(error);
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => renderPage(root));
    return;
  }

  const processed = summary.processingRunAvailable;
  const apps = applicationData.applications || [];
  const appointments = appointmentData.appointments || [];
  const centres = centreData.centres || [];
  const stats = [
    { label: "Total applications", value: summary.totalApplications, icon: "file", style: "" },
    { label: "Allocated appointments", value: summary.allocatedApplications, icon: "calendar", style: "green" },
    { label: "Document blocked", value: summary.docBlockedApplications, icon: "check", style: "amber" },
    { label: "Available slots", value: summary.availableSlots, icon: "grid", style: "purple" },
  ];
  const action = `<button class="button button-primary" type="button" data-action="process">${icon("refresh")}Run Java processing</button>`;

  root.innerHTML = `${pageHeader("Operations overview", "Service overview", "A live snapshot of applications, validations, allocations, and centre capacity.", action)}
    ${note(summary.statusNote, processed ? "success" : "warning")}
    <section class="stats-grid">${stats.map((stat) => `<article class="stat-card">
      <div class="stat-top"><span>${escapeHtml(stat.label)}</span><span class="stat-icon ${stat.style}">${icon(stat.icon)}</span></div>
      <div class="stat-value">${formatNumber(stat.value)}</div>
      <div class="stat-foot">${stat.value == null ? "Requires a Java processing run" : processed ? "From the latest engine run" : "From configured CSV inputs"}</div>
    </article>`).join("")}</section>
    <div class="content-grid">
      <section class="panel"><div class="panel-header"><div><h2 class="panel-title">Processing outcomes</h2><div class="panel-subtitle">Counts reported by the Java engine</div></div></div>
        <div class="panel-body">${processed ? renderOutcomeBars(summary) : `<div class="chart-empty">${icon("chart")}<strong>Processing has not run</strong><span>Outcome charts appear after you run the Java allocation and validation pipeline.</span></div>`}</div>
      </section>
      <section class="panel"><div class="panel-header"><div><h2 class="panel-title">Centre capacity</h2><div class="panel-subtitle">${processed ? "Engine slots for the next appointment date" : "Capacity values read from centres.csv"}</div></div></div>
        <div class="panel-body">${centres.length ? renderCentreBars(centres, processed) : emptyState("No centres found", "No centre records are available in the configured CSV.", "building")}</div>
      </section>
    </div>
    <div class="content-grid equal">
      <section class="panel table-panel"><div class="panel-header"><div><h2 class="panel-title">Recent applications</h2><div class="panel-subtitle">Most recent records in the configured CSV</div></div><a class="panel-action" href="#applications">View all ${icon("chevron")}</a></div>
        <div class="table-wrap">${apps.length ? `<table><thead><tr><th>Application</th><th>Applicant</th><th>Priority</th><th>Status</th></tr></thead><tbody>${apps.slice().sort((a,b) => String(b.applicationDate).localeCompare(String(a.applicationDate))).slice(0,5).map((app) => `<tr><td class="primary-cell mono">${escapeHtml(app.applicationId)}</td><td>${escapeHtml(app.applicantName)}</td><td>${app.priorityCategory ? statusBadge(app.priorityCategory) : `<span class="muted">Not calculated</span>`}</td><td>${statusBadge(app.applicationStatus)}</td></tr>`).join("")}</tbody></table>` : emptyState("No applications", "The source file contains no application rows.", "file")}</div>
      </section>
      <section class="panel table-panel"><div class="panel-header"><div><h2 class="panel-title">Upcoming appointments</h2><div class="panel-subtitle">Allocations from the latest Java run</div></div><a class="panel-action" href="#appointments">View all ${icon("chevron")}</a></div>
        <div class="table-wrap">${appointments.length ? `<table><thead><tr><th>Applicant</th><th>Centre</th><th>Date & time</th></tr></thead><tbody>${appointments.slice(0,5).map((item) => `<tr><td class="primary-cell">${escapeHtml(item.applicantName || item.applicationId)}<span class="secondary-cell">${escapeHtml(item.applicationId)}</span></td><td>${escapeHtml(item.centreName || item.centreId)}</td><td>${formatDate(item.appointmentDate)}<span class="secondary-cell">${escapeHtml(item.appointmentTime || "Not recorded")}</span></td></tr>`).join("")}</tbody></table>` : `<div class="table-empty">No appointment allocation results are available. Run the Java engine to populate this list.</div>`}</div>
      </section>
    </div>
    <div class="footer">PS-117 Passport and Visa Appointment Slot Allocation Engine · Live local data</div>`;

  root.querySelector('[data-action="process"]').addEventListener("click", async (event) => {
    const processButton = event.currentTarget;
    processButton.disabled = true;
    processButton.innerHTML = `${icon("refresh")}Processing…`;
    try {
      await processApplications();
      toast("Java processing completed. The dashboard now reflects its results.");
      await renderPage(root);
    } catch (error) {
      toast(error.message, true);
      processButton.disabled = false;
      processButton.innerHTML = `${icon("refresh")}Run Java processing`;
    }
  });
}

function renderOutcomeBars(summary) {
  const rows = [
    ["Valid documents", summary.validApplications, "green"],
    ["Document blocked", summary.docBlockedApplications, "red"],
    ["Invalid", summary.invalidApplications, "amber"],
    ["Allocated", summary.allocatedApplications, ""],
    ["Unallocated", summary.unallocatedApplications, "purple"],
  ].filter(([, value]) => value != null);
  const max = Math.max(1, ...rows.map(([, value]) => Number(value)));
  return `<div class="chart-legend"><span class="legend-item"><i class="legend-dot green"></i>Validated</span><span class="legend-item"><i class="legend-dot red"></i>Blocked</span><span class="legend-item"><i class="legend-dot"></i>Allocated</span></div>
    <div class="bar-chart">${rows.map(([label, value, style]) => `<div class="bar-row"><span>${escapeHtml(label)}</span><div class="bar-track"><div class="bar-fill ${style}" style="width:${Math.max(0, Number(value) / max * 100)}%"></div></div><strong class="bar-value">${formatNumber(value)}</strong></div>`).join("")}</div>`;
}

function renderCentreBars(centres, processed) {
  return `<div class="capacity-list">${centres.map((centre) => {
    const allocated = centre.allocatedSlots;
    const denominator = centre.dailyCapacity || 0;
    const percent = allocated == null || !denominator ? null : Math.round(allocated / denominator * 100);
    return `<div class="capacity-item"><div class="capacity-top"><span class="capacity-name">${escapeHtml(centre.name)}</span><span class="capacity-meta">${allocated == null ? `${formatNumber(denominator)} daily capacity` : `${formatNumber(allocated)} allocated · ${formatNumber(centre.availableSlots)} available`}</span></div>
      ${processed ? progressBar(percent) : `<div class="capacity-track"><div class="capacity-fill" style="width:0%"></div></div>`}
      <span class="capacity-meta">${escapeHtml(centre.city)} · ${escapeHtml(centre.centreId)}</span></div>`;
  }).join("")}</div>`;
}
