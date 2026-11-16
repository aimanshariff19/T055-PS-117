import { getPriorities } from "./api.js";
import {
  emptyState, errorState, escapeHtml, loadingState, note, pageHeader, statusBadge,
} from "./dashboard.js";

export async function renderPage(root) {
  root.innerHTML = loadingState("Loading Java priority categories…");
  let payload;
  try {
    payload = await getPriorities();
  } catch (error) {
    root.innerHTML = pageHeader("Allocation rules", "Priority management", "Priority categories are defined by the Java application.") + errorState(error);
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => renderPage(root));
    return;
  }
  const categories = payload.categories || [];
  root.innerHTML = `${pageHeader("Allocation rules", "Priority management", "Read-only view of the priority categories and ordering exposed by the Java comparator.")}
    ${note(`Priority rank comes from ${payload.source}. The dashboard cannot change or override the allocation order.`)}
    <section class="panel table-panel"><div class="panel-header"><div><h2 class="panel-title">Supported priority categories</h2><div class="panel-subtitle">Lower rank is considered first by PriorityComparator</div></div></div>
      <div class="table-wrap">${categories.length ? `<table><thead><tr><th>Rank</th><th>Category</th><th>Policy label</th><th>Configured in</th></tr></thead><tbody>${categories.map((item) => `<tr>
        <td><span class="priority-rank">${escapeHtml(item.rank)}</span></td><td>${statusBadge(item.name)}</td>
        <td class="primary-cell">${escapeHtml(item.name.replaceAll("_", " "))}</td><td class="mono">PriorityComparator</td>
      </tr>`).join("")}</tbody></table>` : emptyState("No priority categories", "No Java priority enum values were returned.", "layers")}</div>
    </section>
    <section class="panel" style="margin-top:17px"><div class="panel-header"><div><h2 class="panel-title">Allocation safeguards</h2><div class="panel-subtitle">This dashboard has no allocation logic</div></div></div><div class="panel-body"><p class="page-description" style="margin-top:0">Allocation decisions, eligibility rules, booking windows, and slot selection remain in the Java engine. The UI displays returned decisions and cannot alter them.</p></div></section>`;
}
