# Debt analytics contract and source acceptance

Local implementation, 2026-10-04. No deployment or installed/native acceptance is implied.

`createDebtService({fetchImpl,now,cacheMs,timeoutMs}).getSnapshot({force=false})` returns version 1, fetchedAt, aggregate status, 43 metrics, nine groups and individual source states. Default TTL is six hours, forced refresh floor one minute, at most four concurrent requests, ten-second source timeout, response limit 8 MB. Simultaneous callers share one refresh. Last-good observations are retained per metric in process memory; a restart begins without fabricated fallback data.

Metric fields: id, label, group, unit, value, observedAt, source, sourceUrl, derived, estimated, status (`ok`, `stale`, `missing`), history (`date`, `value`, optional `inputDates`), frequency, note, fetchedAt and optional derivation inputs. Missing values are null. Dollars and people are normalized before any arithmetic. All charts use returned observations. No modeled drift is implemented.

The frontend fetches only `/api/debt`, optionally `?refresh=1`. Backend modules must deploy alongside the public files: `debt-data.js`, `public/debt.html`, `debt.js`, `debt.css`, and `debt-metrics.js`. Font and brand assets are shared with the site. Parent owns route/rate-limit/overlay integration.

## Sources checked

- [Treasury Debt to the Penny](https://fiscaldata.treasury.gov/datasets/debt-to-the-penny/), [Daily Treasury Statement](https://fiscaldata.treasury.gov/datasets/daily-treasury-statement/), [Treasury gold reserve](https://fiscaldata.treasury.gov/datasets/status-report-government-gold-reserve/), [average interest rates](https://fiscaldata.treasury.gov/datasets/average-interest-rates-treasury-securities/). Direct API schemas and complete latest rows inspected. Daily Treasury Statement monetary fields are millions. The modern TGA closing-account row supplies its balance in `open_today_bal`; older `close_today_bal` is accepted. Limit arithmetic subtracts excluded debt and adds other subject debt, without adding the statutory ceiling. Gold requires eight complete distinct published detail lines; truncated groups are discarded.
- [FRED series definitions](https://fred.stlouisfed.org/): each metric links its exact series. Extra checks: [FDHBFIN](https://fred.stlouisfed.org/series/FDHBFIN) is billions; [WALCL](https://fred.stlouisfed.org/series/WALCL) is millions; [POPTHM](https://fred.stlouisfed.org/series/POPTHM) is thousands and includes armed forces overseas. [Social Security](https://fred.stlouisfed.org/series/W823RC1) and [Medicare](https://fred.stlouisfed.org/series/W824RC1) are monthly annual rates, not quarterly. [SLOAS](https://fred.stlouisfed.org/series/SLOAS) and [MVLOAS](https://fred.stlouisfed.org/series/MVLOAS) are explicitly discontinued. [FYFSD](https://fred.stlouisfed.org/series/FYFSD) and [MTSDS133FMS](https://fred.stlouisfed.org/series/MTSDS133FMS) use negative deficit, positive surplus. The derived per-citizen deficit explicitly uses the opposite convention (spending minus receipts).
- [BLS API signatures](https://www.bls.gov/developers/api_signature_v2.htm) and [limits](https://www.bls.gov/developers/api_faqs.htm). One six-series request, no key; annual M13 observations excluded. Counts convert thousands to persons. CPI stays an index, not an inflation percentage.

Six years of Treasury/FRED history are requested, one year for the multirow debt-limit table; BLS requests the current and preceding six calendar years. Missing points are not imputed. Ratio histories join backwards by observation period, with maximum input ages; they are current revised histories, not vintage data known on that date. Publication dates are not exposed by CSV. The dashboard's stale thresholds concern observation age/fetch failures, not proof that a provider missed its own release schedule.

## Verification

`node --test showcase/tools/tests/debt-check.mjs`: 18 passing tests. Coverage includes scales, invalid/missing values, BLS annual exclusion, complete gold groups, debt-limit exclusions, backwards derived joins, independently stale fallback, future response rejection, partial field failure, TTL/singleflight/forced floor, source timeouts/concurrency and chart/CSV contracts.

One bounded live upstream round on 2026-10-04 took 3.5 seconds: all 43 metrics populated, no source-request failures. 12,375 observations including derived histories. Four stale by cadence threshold: foreign-held federal debt and federal debt/GDP (2026-01-01 observations), student and auto loan series (2024-10-01; discontinued). Gross-debt history: 1,503 points, 2020-10-05 through 2026-10-01. Debt-limit history: 249 points, 2025-10-06 through 2026-10-01. Live snapshot retained only in the task's temporary evidence folder, not bundled into the app.

Parent owns browser visual, mobile, keyboard and shared-shell review. No browser operations were performed by the implementer.
