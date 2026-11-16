import { getCentres } from "./api.js";
import {
  emptyState, errorState, escapeHtml, formatNumber, loadingState, note, pageHeader, progressBar,
} from "./dashboard.js";

export async function renderPage(root) {
  root.innerHTML = loadingState("Loading appointment centres…");
  let payload;
  try {
    payload = await getCentres();
  } catch (error) {
    root.innerHTML = pageHeader("Centre operations", "Appointment centres", "Centre configuration and capacity from centres.csv.") + errorState(error);
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => renderPage(root));
    return;
  }
  const centres = payload.centres || [];
  root.innerHTML = `${pageHeader("Centre operations", "Appointment centres", "Review configured centre capacity and slot availability returned by Java processing.")}
    ${note(payload.processed
      ? "Daily capacity is read from centres.csv. Available slot counts are summed from Java-generated slots for the next appointment date."
      : "Centre names and configured daily capacity are available from CSV. Allocation, availability, and utilization require a Java processing run.", payload.processed ? "" : "warning")}
    ${centres.length ? `<section class="stats-grid">${centres.map((centre) => `<article class="stat-card">
      <div class="stat-top"><span>${escapeHtml(centre.centreId)}</span><span class="stat-icon">${'<svg aria-hidden="true"><use href="assets/icons/sprite.svg#building"></use></svg>'}</span></div>
      <div class="stat-value">${formatNumber(centre.dailyCapacity)}</div><div class="stat-foot">Daily capacity · ${escapeHtml(centre.city)}</div></article>`).join("")}</section>
      <section class="panel table-panel"><div class="panel-header"><div><h2 class="panel-title">Centre capacity register</h2><div class="panel-subtitle">${centres.length} configured centres</div></div></div>
      <div class="table-wrap"><table><thead><tr><th>Centre ID</th><th>Centre name</th><th>Location</th><th>Daily capacity</th><th>Slot capacity</th><th>Allocated</th><th>Available slots</th><th>Utilization</th><th>Operating hours</th></tr></thead><tbody>${centres.map((centre) => `<tr>
        <td class="primary-cell mono">${escapeHtml(centre.centreId)}</td><td class="primary-cell">${escapeHtml(centre.name)}</td><td>${escapeHtml(centre.city)}</td>
        <td>${formatNumber(centre.dailyCapacity)}</td><td>${formatNumber(centre.slotCapacity)} / slot</td>
        <td>${formatNumber(centre.allocatedSlots)}</td><td>${formatNumber(centre.availableSlots)}</td><td>${progressBar(centre.utilizationPercent)}</td>
        <td>${escapeHtml(centre.openTime)}–${escapeHtml(centre.closeTime)}<span class="secondary-cell">${escapeHtml(centre.workingDays?.join(", ") || "Days not specified")}</span></td>
      </tr>`).join("")}</tbody></table></div></section>` : emptyState("No centres configured", "No centre rows were found in centres.csv.", "building")}`;
}
