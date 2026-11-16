import { getHealth, getSettings } from "./api.js";
import {
  button, errorState, escapeHtml, loadingState, note, pageHeader, statusBadge,
} from "./dashboard.js";

export async function renderPage(root) {
  root.innerHTML = loadingState("Checking server and configuration…");
  let health;
  let settings;
  try {
    [health, settings] = await Promise.all([getHealth(), getSettings()]);
  } catch (error) {
    root.innerHTML = pageHeader("System configuration", "Settings", "Inspect local server status and supported Java processing commands.") + errorState(error);
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => renderPage(root));
    return;
  }
  root.innerHTML = `${pageHeader("System configuration", "Settings", "Read-only details for the local dashboard server and Java command-line engine.",
    `<button class="button button-secondary" type="button" data-action="refresh">${'<svg aria-hidden="true"><use href="assets/icons/sprite.svg#refresh"></use></svg>'}Refresh status</button>`)}
    ${note("The API binds to loopback only. Data paths are fixed by application.properties; browser requests cannot specify files or commands.", "success")}
    <div class="content-grid equal">
      <section class="panel"><div class="panel-header"><div><h2 class="panel-title">Server status</h2><div class="panel-subtitle">Local Java HTTP service</div></div>${statusBadge(health.status)}</div>
        <div class="panel-body"><div class="settings-list">
          ${settingsRow("Application", health.application)}${settingsRow("Version", health.version)}
          ${settingsRow("Java runtime", health.runtime)}${settingsRow("Dashboard URL", `http://127.0.0.1:${settings.port}`)}
          ${settingsRow("Network exposure", "Loopback only")}${settingsRow("API state", "Healthy")}
        </div></div>
      </section>
      <section class="panel"><div class="panel-header"><div><h2 class="panel-title">Processing configuration</h2><div class="panel-subtitle">Existing commands and fixed inputs</div></div></div>
        <div class="panel-body"><div class="settings-list">
          ${settingsRow("Accepted CLI commands", settings.commands.join(", "))}
          ${settingsRow("Centres input", settings.inputFiles.centres)}
          ${settingsRow("Applications input", settings.inputFiles.applications)}
          ${settingsRow("Documents input", settings.inputFiles.documents)}
          ${settingsRow("Events input", settings.inputFiles.events)}
          ${settingsRow("Output directory", settings.outputDirectory)}
        </div></div>
      </section>
    </div>
    <section class="panel" style="margin-top:17px"><div class="panel-header"><div><h2 class="panel-title">Processing action</h2><div class="panel-subtitle">Run the same Java pipeline used by the CLI</div></div></div><div class="panel-body"><div class="process-banner">
      <div><h3>Process configured sample files</h3><p>This invokes Java validation and allocation, writes a new uniquely named report folder, and never accepts file paths from the browser.</p></div>
      ${button("Run processing", "process", "primary", "refresh")}
    </div></div></section>`;
  root.querySelector('[data-action="refresh"]').addEventListener("click", () => renderPage(root));
  root.querySelector('[data-action="process"]').addEventListener("click", async (event) => {
    const target = event.currentTarget;
    target.disabled = true;
    target.textContent = "Processing…";
    try {
      const { processApplications } = await import("./api.js");
      await processApplications();
      const { toast } = await import("./dashboard.js");
      toast("Java processing completed.");
      await renderPage(root);
    } catch (error) {
      const { toast } = await import("./dashboard.js");
      toast(error.message, true);
      target.disabled = false;
      target.textContent = "Run processing";
    }
  });
}

function settingsRow(label, value) {
  return `<div class="settings-row"><span class="settings-label">${escapeHtml(label)}</span><strong class="settings-value">${escapeHtml(value ?? "Not configured")}</strong></div>`;
}
