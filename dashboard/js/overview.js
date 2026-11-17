import { escapeHtml, icon, pageHeader, statusBadge, formatNumber } from "./dashboard.js";

const demo = {
  stats: [
    { label: "Applications received", value: 1284, delta: "+12.8%", note: "vs. previous 30 days", icon: "file", tone: "blue" },
    { label: "Appointments allocated", value: 936, delta: "+8.4%", note: "73% of applications", icon: "calendar", tone: "mint" },
    { label: "Awaiting verification", value: 214, delta: "16.7%", note: "Requires document review", icon: "check", tone: "amber" },
    { label: "Available slots", value: 148, delta: "Today", note: "Across 8 service centres", icon: "grid", tone: "violet" },
  ],
  trend: [
    { day: "Mon", received: 112, allocated: 84 }, { day: "Tue", received: 146, allocated: 106 },
    { day: "Wed", received: 128, allocated: 98 }, { day: "Thu", received: 172, allocated: 126 },
    { day: "Fri", received: 154, allocated: 119 }, { day: "Sat", received: 96, allocated: 73 },
    { day: "Sun", received: 118, allocated: 88 },
  ],
  centres: [
    { name: "Bengaluru Central", city: "Karnataka", used: 82, total: 100, code: "BLR-01" },
    { name: "Hyderabad Regional", city: "Telangana", used: 68, total: 80, code: "HYD-02" },
    { name: "Chennai Passport Seva", city: "Tamil Nadu", used: 74, total: 90, code: "CHE-03" },
    { name: "Mysuru Service Centre", city: "Karnataka", used: 42, total: 70, code: "MYS-04" },
  ],
  activity: [
    { id: "PS117-2048", name: "Aarav Mehta", type: "Normal passport", status: "ALLOCATED", time: "09:42 AM", initials: "AM", tone: "blue" },
    { id: "PS117-2047", name: "Diya Nair", type: "Tatkal passport", status: "PENDING", time: "09:28 AM", initials: "DN", tone: "rose" },
    { id: "PS117-2046", name: "Kabir Rao", type: "Re-issue", status: "VERIFIED", time: "09:11 AM", initials: "KR", tone: "mint" },
    { id: "PS117-2045", name: "Ananya Iyer", type: "Police clearance", status: "PENDING", time: "08:56 AM", initials: "AI", tone: "violet" },
  ],
};

function trendChart(period = "7 days") {
  const rows = period === "30 days"
    ? [{ day:"W1",received:520,allocated:390 },{ day:"W2",received:610,allocated:452 },{ day:"W3",received:575,allocated:438 },{ day:"W4",received:690,allocated:510 }]
    : demo.trend;
  const max = Math.max(...rows.map((r) => r.received)) * 1.12;
  const bars = rows.map((r, i) => {
    const x = 48 + i * (period === "30 days" ? 118 : 65);
    const h1 = r.received / max * 145, h2 = r.allocated / max * 145;
    return `<g><rect x="${x}" y="${178-h1}" width="17" height="${h1}" rx="5" fill="#b9c9f7"/><rect x="${x+20}" y="${178-h2}" width="17" height="${h2}" rx="5" fill="#4569d4"/><text x="${x+18}" y="202" text-anchor="middle" fill="#8994a8" font-size="10">${r.day}</text></g>`;
  }).join("");
  return `<svg class="overview-chart" viewBox="0 0 540 218" role="img" aria-label="Applications received and appointments allocated chart">
    <line x1="32" y1="178" x2="525" y2="178" stroke="#e9edf4"/><line x1="32" y1="130" x2="525" y2="130" stroke="#eef1f6" stroke-dasharray="3 5"/><line x1="32" y1="82" x2="525" y2="82" stroke="#eef1f6" stroke-dasharray="3 5"/><line x1="32" y1="34" x2="525" y2="34" stroke="#eef1f6" stroke-dasharray="3 5"/>
    <text x="25" y="181" text-anchor="end" fill="#9aa4b5" font-size="9">0</text><text x="25" y="133" text-anchor="end" fill="#9aa4b5" font-size="9">50</text><text x="25" y="85" text-anchor="end" fill="#9aa4b5" font-size="9">100</text><text x="25" y="37" text-anchor="end" fill="#9aa4b5" font-size="9">150</text>${bars}</svg>`;
}

export async function renderPage(root) {
  root.innerHTML = `
    <div class="overview-page">
      <section class="overview-welcome">
        <div class="welcome-copy">
          <div class="welcome-kicker"><span class="welcome-kicker-dot"></span> SERVICE OPERATIONS / OVERVIEW</div>
          <h1>Good morning, Operator<span>.</span></h1>
          <p>Your service command centre. Here's what's happening across passport operations today.</p>
          <div class="welcome-meta"><span>${icon("calendar")} Tuesday, 17 November 2026</span><span class="welcome-divider"></span><span>${icon("map-pin")} India operations</span></div>
        </div>
        <div class="welcome-art" aria-hidden="true"><div class="art-orbit orbit-one"></div><div class="art-orbit orbit-two"></div><div class="art-core"><span>PS</span><i>117</i></div><div class="art-spark spark-one"></div><div class="art-spark spark-two"></div></div>
        <div class="welcome-bottom"><span>DAILY OPERATIONS SNAPSHOT</span><span>DEMO ENVIRONMENT <b>●</b></span></div>
      </section>

      <div class="overview-section-heading"><div><span class="section-eyebrow">AT A GLANCE</span><h2>Operations summary</h2></div><span class="snapshot-label"><span></span> Illustrative demo data</span></div>
      <section class="overview-stats">
        ${demo.stats.map((s, i) => `<article class="overview-stat stat-${s.tone}"><div class="overview-stat-top"><span>${s.label}</span><span class="overview-stat-icon">${icon(s.icon)}</span></div><div class="overview-stat-value">${formatNumber(s.value)}</div><div class="overview-stat-bottom"><strong>${s.delta}</strong><span>${s.note}</span></div><div class="stat-index">0${i+1}</div></article>`).join("")}
      </section>

      <section class="overview-main-grid">
        <article class="overview-card trend-card">
          <div class="overview-card-head"><div><span class="section-eyebrow">PERFORMANCE</span><h2>Application activity</h2><p>Applications received against appointments allocated</p></div><select id="trend-period" class="overview-select" aria-label="Chart period"><option>7 days</option><option>30 days</option></select></div>
          <div class="trend-legend"><span><i class="legend-received"></i>Received</span><span><i class="legend-allocated"></i>Allocated</span></div>
          <div id="trend-chart">${trendChart()}</div>
          <div class="chart-foot"><span>Activity volume · sample dataset</span><span>Updated for preview</span></div>
        </article>
        <article class="overview-card priority-card">
          <div class="overview-card-head"><div><span class="section-eyebrow">QUEUE HEALTH</span><h2>Application priorities</h2><p>Illustrative distribution by category</p></div><span class="card-menu-mark">•••</span></div>
          <div class="priority-total"><strong>1,284</strong><span>Total applications</span></div>
          <div class="priority-stack"><span style="width:48%;background:#496bd4"></span><span style="width:22%;background:#9b83dd"></span><span style="width:17%;background:#e4ad61"></span><span style="width:13%;background:#66b69b"></span></div>
          <div class="priority-legend">
            <div><i style="background:#496bd4"></i><span>Normal</span><b>48%</b></div><div><i style="background:#9b83dd"></i><span>Tatkal</span><b>22%</b></div><div><i style="background:#e4ad61"></i><span>Senior / Minor</span><b>17%</b></div><div><i style="background:#66b69b"></i><span>Other priority</span><b>13%</b></div>
          </div>
          <div class="priority-foot"><span>${icon("info")} Demo distribution only</span></div>
        </article>
      </section>

      <section class="overview-lower-grid">
        <article class="overview-card activity-card">
          <div class="overview-card-head"><div><span class="section-eyebrow">LATEST UPDATES</span><h2>Recent applications</h2><p>Latest activity in the sample workspace</p></div><a class="overview-link" href="#applications">View all ${icon("arrow")}</a></div>
          <div class="activity-list">${demo.activity.map((a) => `<div class="activity-row"><div class="activity-avatar avatar-${a.tone}">${a.initials}</div><div class="activity-person"><strong>${a.name}</strong><span>${a.id} · ${a.type}</span></div><div class="activity-status">${statusBadge(a.status)}</div><time>${a.time}</time></div>`).join("")}</div>
          <div class="activity-footer"><span>Showing 4 of 1,284 illustrative records</span><a href="#applications">Open application register ${icon("arrow")}</a></div>
        </article>
        <article class="overview-card centres-card">
          <div class="overview-card-head"><div><span class="section-eyebrow">CAPACITY MONITOR</span><h2>Centre utilisation</h2><p>Sample daily slot allocation</p></div><a class="overview-link" href="#centres">All centres ${icon("arrow")}</a></div>
          <div class="centre-list">${demo.centres.map((c) => { const pct=Math.round(c.used/c.total*100); return `<div class="centre-row"><div class="centre-row-top"><div><strong>${c.name}</strong><span>${c.code} · ${c.city}</span></div><b>${pct}%</b></div><div class="centre-track"><span style="width:${pct}%"></span></div><div class="centre-row-foot"><span>${c.used} allocated</span><span>${c.total-c.used} slots available</span></div></div>`; }).join("")}</div>
        </article>
      </section>
      <div class="overview-disclaimer">${icon("info")} <span>This is a UI preview using illustrative demo data. No live passport applications, bookings, payments or government records are accessed or changed. Java API integration will be added in the next phase.</span></div>
      <footer class="overview-footer"><span>PS-117 · SERVICE OPERATIONS</span><span>DESIGNED FOR CLARITY. BUILT FOR CONTROL.</span></footer>
    </div>`;

  root.querySelector("#trend-period")?.addEventListener("change", (event) => {
    root.querySelector("#trend-chart").innerHTML = trendChart(event.target.value);
  });
}
