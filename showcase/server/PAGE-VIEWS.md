# Website page views

The website counts successful HTML page responses at the origin. Each count belongs to a published route, such as `/`, `/work/bayzyl.html` or `/arcade/chess`. The same person refreshing a page counts twice. A browser serving a cached page makes no request to count. These figures measure page requests, not unique people, sessions or conversions.

Only completed GET responses on `prismet.xyz` and `www.prismet.xyz` count. Known bot and command-line user agents, requests without a user agent, declared prefetch/prerender requests, and requests with Do Not Track or Global Privacy Control enabled are excluded. Automation that looks like a browser can still count. Named public pages and games use a fixed list, plus up to 128 public project page filenames from the deployed build. The private editor, game-shell asset, API calls, error pages and assets are excluded. Query strings, referrers, IP addresses, cookies, and user-agent values never enter the counters.

The app process initializes each `prismet_page_views_total{page="…"}` counter to zero at startup. `prismet_page_views_collection_start_seconds` gives the Unix startup time of that process. Fly scrapes a separate internal port, 9091, at `/metrics`. Only port 8080 is forwarded by the public HTTP service. Local runs start the metrics listener only when `PRISMET_METRICS_PORT` is set.

In Fly's authenticated Prometheus endpoint, filter by the app. Use per-instance counter increases before summing:
```promql
sum by (page) (increase(prismet_page_views_total{app="prismet-site-restless-horizon-217"}[24h]))
```
Scraped increases are estimates. Sleeping Machines produce gaps. Restarts reset the process counters, and requests served between a start/stop and its nearest scrape can be missed. With fewer than two samples, a period may have no usable increase. Data collection starts with the release containing this instrumentation. Earlier page history cannot be reconstructed from aggregate edge traffic.
