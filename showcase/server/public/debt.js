"use strict";
/* ----------------------------------------------------------------------------
   Accessible U.S. Debt Clock — single-file proof of concept.
   Units are kept small and named so this can later drop into another app
   (e.g. a WKWebView inside Kaleidoscope) without untangling globals.
---------------------------------------------------------------------------- */

const CONFIG = {
  // Treasury Fiscal Data API — free, no key, CORS-enabled, updated each business day.
  api: "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v2/accounting/od/debt_to_penny"
     + "?fields=tot_pub_debt_out_amt,record_date&sort=-record_date&page[size]=10",
  // Clearly-labelled estimates. Swap for a live Census/IRS source later.
  population: 342_000_000,   // ~U.S. resident population, mid-2026 est.
  taxpayers: 136_000_000,    // ~individual income-tax returns filed, est.
  // Fallback if the network is unavailable, so we never show a blank/wrong clock.
  fallback: { amount: 39_340_000_000_000, perSecond: 130_000, date: "recent estimate" },
  announceEverySec: 20,      // how often the live region speaks on its own
};

// --- Formatter: numbers -> display + plain-spoken strings ---------------------
const Formatter = {
  usd(n) {
    return n.toLocaleString("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });
  },
  // "approximately 36.2 trillion dollars" — friendlier than 14 digits read aloud.
  spoken(n) {
    const abs = Math.abs(n);
    const say = (v, unit) => `approximately ${(+v.toFixed(2)).toLocaleString("en-US")} ${unit}`;
    let body;
    if (abs >= 1e12) body = say(n / 1e12, "trillion dollars");
    else if (abs >= 1e9) body = say(n / 1e9, "billion dollars");
    else if (abs >= 1e6) body = say(n / 1e6, "million dollars");
    else body = `${Math.round(n).toLocaleString("en-US")} dollars`;
    return body;
  },
};

// --- DebtData: fetch latest records, derive current value + per-second rate ---
const DebtData = {
  anchorAmount: 0,      // most recent Treasury figure
  anchorTime: 0,        // when that figure is anchored (ms epoch)
  perSecond: 0,         // estimated growth per second
  todayBase: 0,         // figure at local midnight (for "increase today")
  asOf: "",
  live: false,

  async load() {
    try {
      const res = await fetch(CONFIG.api, { headers: { "Accept": "application/json" } });
      if (!res.ok) throw new Error("HTTP " + res.status);
      const json = await res.json();
      const rows = (json.data || [])
        .map(r => ({ amt: parseFloat(r.tot_pub_debt_out_amt), date: r.record_date }))
        .filter(r => isFinite(r.amt));
      if (rows.length < 2) throw new Error("insufficient data");

      const latest = rows[0];
      const older = rows[Math.min(rows.length - 1, 5)]; // ~a week of business days back
      const days = Math.max(1, daysBetween(older.date, latest.date));
      const perDay = (latest.amt - older.amt) / days;

      this.anchorAmount = latest.amt;
      this.anchorTime = endOfDay(latest.date);   // figure represents end of its record day
      this.perSecond = perDay / 86400;
      this.asOf = latest.date;
      this.live = true;
      this.todayBase = this.estimateAt(startOfLocalDay());
      return true;
    } catch (e) {
      // Graceful, honest fallback — labelled as an estimate.
      const f = CONFIG.fallback;
      this.anchorAmount = f.amount;
      this.anchorTime = Date.now();
      this.perSecond = f.perSecond;
      this.asOf = f.date;
      this.live = false;
      this.todayBase = this.estimateAt(startOfLocalDay());
      return false;
    }
  },

  estimateAt(ms) { return this.anchorAmount + this.perSecond * ((ms - this.anchorTime) / 1000); },
  current() { return this.estimateAt(Date.now()); },
  increaseToday() { return Math.max(0, this.current() - this.todayBase); },
};

function endOfDay(iso) { const d = new Date(iso + "T23:59:59"); return d.getTime(); }
function startOfLocalDay() { const d = new Date(); d.setHours(0,0,0,0); return d.getTime(); }
function daysBetween(a, b) { return Math.round((new Date(b) - new Date(a)) / 86400000); }

// --- Announcer: manages the single polite live region -----------------------
const Announcer = {
  el: document.getElementById("live"),
  last: 0,
  maybeSpeak(now) {
    if (now - this.last >= CONFIG.announceEverySec * 1000) { this.speakNow(); this.last = now; }
  },
  speakNow() {
    const d = DebtData;
    this.el.textContent =
      `National debt: ${Formatter.spoken(d.current())}. ` +
      `Debt per citizen: ${Formatter.spoken(d.current() / CONFIG.population)}. ` +
      `Growing about ${Formatter.spoken(d.perSecond)} per second.`;
  },
};

// --- UI: render the panels ---------------------------------------------------
const UI = {
  els: {
    debt: document.getElementById("debt"),
    debtSpoken: document.getElementById("debt-spoken"),
    perCitizen: document.getElementById("per-citizen"),
    perTaxpayer: document.getElementById("per-taxpayer"),
    today: document.getElementById("today"),
    rate: document.getElementById("rate"),
    status: document.getElementById("status"),
    asof: document.getElementById("asof"),
  },
  render() {
    const d = DebtData;
    const now = d.current();
    this.els.debt.textContent = Formatter.usd(now);
    this.els.debtSpoken.textContent = Formatter.spoken(now);
    this.els.perCitizen.textContent = Formatter.usd(now / CONFIG.population);
    this.els.perTaxpayer.textContent = Formatter.usd(now / CONFIG.taxpayers);
    this.els.today.textContent = Formatter.usd(d.increaseToday());
    this.els.rate.textContent = Formatter.usd(d.perSecond);
  },
  showStatus() {
    const d = DebtData;
    if (d.live) {
      this.els.status.textContent = `Live — latest U.S. Treasury figure dated ${d.asOf}, ticking by estimate between updates.`;
      this.els.status.className = "status";
    } else {
      this.els.status.textContent = "Couldn’t reach the U.S. Treasury just now — showing a clearly-labelled estimate. Reconnect to go live.";
      this.els.status.className = "status warn";
    }
    this.els.asof.textContent = d.live ? `Latest Treasury data: ${d.asOf}.` : "";
  },
};

// --- Ticker: the animation loop, honouring reduced-motion + manual pause -----
const Ticker = {
  paused: false,
  reduced: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  lastStep: 0,
  start() {
    const loop = (t) => {
      const now = Date.now();
      if (!this.paused) {
        if (this.reduced) {
          // No per-frame motion: update at a calm 2s step instead of easing digits.
          if (now - this.lastStep >= 2000) { UI.render(); this.lastStep = now; }
        } else {
          UI.render();
        }
        Announcer.maybeSpeak(now);
      }
      requestAnimationFrame(loop);
    };
    requestAnimationFrame(loop);
  },
};

// --- Wire up controls --------------------------------------------------------
document.getElementById("announce").addEventListener("click", () => Announcer.speakNow());
const motionBtn = document.getElementById("motion");
motionBtn.addEventListener("click", () => {
  Ticker.paused = !Ticker.paused;
  motionBtn.setAttribute("aria-pressed", String(Ticker.paused));
  motionBtn.textContent = Ticker.paused ? "▶ Resume motion" : "⏸ Pause motion";
  if (Ticker.paused) UI.render(); // freeze on a clean current value
});

// --- Boot --------------------------------------------------------------------
(async function init() {
  await DebtData.load();
  UI.render();
  UI.showStatus();
  Announcer.speakNow();          // announce once on load
  Ticker.start();
})();
