import { getAllocationResults, getAppointments, processApplications } from "./api.js";
import {
  emptyState, errorState, escapeHtml, formatDate, icon, loadingState,
  note, pageHeader, statusBadge, toast,
} from "./dashboard.js";

export async function renderPage(root) {
  root.innerHTML = loadingState();
  let appointmentData;
  let allocationData;
  try {
    [appointmentData, allocationData] = await Promise.all([getAppointments(), getAllocationResults()]);
  } catch (error) {
    root.innerHTML = pageHeader("Allocation operations", "Appointments", "View appointment slots assigned by the Java allocation engine.") + errorState(error);
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => renderPage(root));
    return;
  }
  const appointments = appointmentData.appointments || [];
  const allocations = allocationData.results || [];
  const actions = `<button class="button button-primary" type="button" data-action="process">${icon("refresh")}Run Java allocation</button>`;
  root.innerHTML = `${pageHeader("Allocation operations", "Appointments", "Allocation decisions are made by Java. This dashboard does not assign or change appointment slots.", actions)}
    ${note(appointmentData.processed
      ? "Appointments and allocation outcomes reflect the latest processing run in this server session. The engine's quota manager does not currently populate per-centre quotas."
      : "No allocation run is available yet. Running the engine uses the configured CSV inputs and writes reports to a new run folder.", appointmentData.processed ? "success" : "warning")}
    <section class="panel table-panel"><div class="panel-header"><div><h2 class="panel-title">Allocated appointments</h2><div class="panel-subtitle">${appointments.length} appointments from Java engine state</div></div></div>
      <div class="table-wrap">${appointments.length ? `<table><thead><tr><th>Application</th><th>Applicant</th><th>Centre</th><th>Date</th><th>Time</th><th>Priority</th><th>Allocation</th></tr></thead><tbody>${appointments.map((item) => `<tr>
        <td class="primary-cell mono">${escapeHtml(item.applicationId)}</td><td>${escapeHtml(item.applicantName || "Not recorded")}</td>
        <td><span class="primary-cell">${escapeHtml(item.centreName || item.centreId)}</span><span class="secondary-cell">${escapeHtml(item.centreId)}</span></td>
        <td>${formatDate(item.appointmentDate)}</td><td>${escapeHtml(item.appointmentTime || "Not recorded")}</td>
        <td>${item.priorityCategory ? statusBadge(item.priorityCategory) : `<span class="muted">Not calculated</span>`}</td><td>${statusBadge(item.allocationStatus)}</td></tr>`).join("")}</tbody></table>` : emptyState("No appointments allocated", "Appointment records appear here only after a Java processing run returns allocations.", "calendar")}</div>
    </section>
    <section class="panel table-panel" style="margin-top:17px"><div class="panel-header"><div><h2 class="panel-title">Allocation decisions</h2><div class="panel-subtitle">Successful and unsuccessful decisions returned by AllocationEngine</div></div></div>
      <div class="table-wrap">${allocations.length ? `<table><thead><tr><th>Application</th><th>Decision</th><th>Centre</th><th>Slot</th><th>Priority</th><th>Reason</th></tr></thead><tbody>${allocations.map((item) => `<tr>
        <td class="primary-cell mono">${escapeHtml(item.applicationId)}</td><td>${statusBadge(item.allocated ? "ALLOCATED" : "NOT_ALLOCATED")}</td>
        <td>${escapeHtml(item.centreId || "—")}</td><td class="mono">${escapeHtml(item.slotId || "—")}</td>
        <td>${item.priorityCategory ? statusBadge(item.priorityCategory) : "—"}</td><td>${escapeHtml(item.reason || "Not recorded")}</td></tr>`).join("")}</tbody></table>` : `<div class="table-empty">No allocation decisions are available. The Java engine must run first.</div>`}</div>
    </section>`;

  root.querySelector('[data-action="process"]').addEventListener("click", async (event) => {
    const target = event.currentTarget;
    target.disabled = true;
    target.textContent = "Processing…";
    try {
      await processApplications();
      toast("Java allocation run completed.");
      await renderPage(root);
    } catch (error) {
      toast(error.message, true);
      target.disabled = false;
      target.textContent = "Run Java allocation";
    }
  });
}
