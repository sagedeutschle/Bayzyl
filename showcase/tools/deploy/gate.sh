#!/usr/bin/env bash
# gate.sh <base-url> [local|live]: the routes that must hold before and after every deploy. Exit 1 on any failure.
#   local: a server started from showcase/server with site/ = dist; /api/wordle needs network (GATE_WORDLE=require to insist)
#   live:  https://prismet.xyz after `fly deploy`; /api/wordle must answer with today's word
set -uo pipefail
B=${1:?base url}; MODE=${2:-local}; fail=0
ok()   { printf 'ok   %s\n' "$1"; }
bad()  { printf 'FAIL %s\n' "$1"; fail=1; }
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$B$1"; }
chk()  { [ "$(code "$1")" = "$2" ] && ok "$1 = $2" || bad "$1 = $(code "$1") (want $2)"; }
chk / 200; chk /index.html 200; chk /work/bayzyl.html 200; chk /colophon.html 200; chk /site.css 200; chk /site.js 200
chk /assets/fonts/fonts.css 200; chk /assets/og.jpg 200; chk /favicon.ico 200; chk /healthz 200
chk /steam 200; chk /steam.html 200; chk /debt 200; chk /debt.html 200; chk /style.css 200; chk /steam.js 200; chk /debt.js 200
chk /icon.svg 200; chk /manifest.webmanifest 200
chk /arcade 200; chk /tools 200; chk /arcade/game.html 200; chk /arcade/arcade.css 200
for game in 2048 minesweeper lights-out wordle rubiks-cube snake sudoku sliding-15 nonogram chess reversi connect-four checkers gomoku sea-battle catan solitaire spider crazy-8 brick-bench; do
  chk "/arcade/$game" 200
done
for module in boot app-shell catalog extended browser-saves controller-policy import-backup engines saves arcade cloud room room-protocol; do
  chk "/arcade/$module.js" 200
done
for module in wordle rubiks-cube snake sudoku sliding-15 nonogram chess reversi connect-four checkers gomoku sea-battle catan solitaire spider crazy-8 brick-bench puzzle-common puzzle-data catan-board; do
  chk "/arcade/games/$module.js" 200
done
chk /product-shell.css 200; chk /api/arcade/config 200; chk /debt.css 200; chk /debt-metrics.js 200; chk /.well-known/apple-app-site-association 200
# Upstream outages are valid data states. Gate the contract, never fabricate freshness.
if debt=$(curl -fsS --max-time 95 --max-filesize 8388608 "$B/api/debt"); then
  if printf '%s' "$debt" | python3 -c 'import datetime,json,math,re,sys
try:
  d=json.load(sys.stdin)
  def date(v):
    return isinstance(v,str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}",v) is not None and datetime.date.fromisoformat(v).isoformat()==v
  def number(v):
    return type(v) in (int,float) and math.isfinite(v)
  assert d["version"]==1 and d["status"] in ("ok","partial","unavailable")
  assert isinstance(d["fetchedAt"],str) and date(d["fetchedAt"][:10])
  metrics=d["metrics"]; groups=d["groups"]
  assert isinstance(metrics,list) and len(metrics)==43 and len({m["id"] for m in metrics})==43
  assert isinstance(groups,list) and {g["id"] for g in groups}=={"national","ratios","treasury","revenue","spending","household","benefits","economy","people"} and len(groups)==9
  assigned=[i for g in groups for i in g["metricIds"]]
  assert len(assigned)==43 and set(assigned)=={m["id"] for m in metrics}
  assert isinstance(d["sources"],list)
  for m in metrics:
    assert isinstance(m["id"],str) and isinstance(m["label"],str) and isinstance(m["unit"],str)
    assert m["status"] in ("ok","stale","missing")
    assert any(g["id"]==m["group"] and m["id"] in g["metricIds"] for g in groups)
    h=m["history"]; assert isinstance(h,list) and len(h)<=10000
    assert all(date(p["date"]) and p["date"]<=d["fetchedAt"][:10] and number(p["value"]) for p in h)
    assert all(h[i-1]["date"]<h[i]["date"] for i in range(1,len(h)))
    if m["status"]=="missing": assert m["value"] is None and m["observedAt"] is None and not h
    else: assert number(m["value"]) and date(m["observedAt"]) and h and h[-1]["date"]==m["observedAt"] and h[-1]["value"]==m["value"]
  print("debt schema: 43 metrics / 9 groups / " + d["status"])
except (AssertionError,KeyError,TypeError,ValueError,OverflowError):
  sys.exit(1)
'; then ok "/api/debt normalized 43-metric contract"; else bad "/api/debt invalid metric/history contract"; fi
else
  bad "/api/debt request failed or exceeded bounded response size/time"
fi
chk /scam 200; chk /scam.js 200; chk /scam-calc.js 200; chk /scam.css 200; chk /scam-seal.svg 200; chk /scam-data/tax-2026.json 200; chk /scam-data/mts-snapshot.json 200
chk /edit 200; chk /edit.js 200; chk /edit/store.js 200; chk /lib/render.js 200; chk /edit/assets.json 200; chk /robots.txt 200
st=$(curl -s --max-time 20 "$B/api/edit/status"); printf '%s' "$st" | grep -q '"ok":true' && ok "/api/edit/status answers ($(printf '%s' "$st" | grep -o '"configured":[a-z]*'))" || bad "/api/edit/status = $(printf '%s' "$st" | head -c 80)"
chk /shots/helm-1-full.webp 404; chk /shots/ 404; chk /nope 404; chk /api/nope 404; chk /../etc/passwd 404
[ "$(curl -s --max-time 20 "$B/steam" "$B/steam.js" | grep -cE '7656[0-9]{13}')" = 0 ] && ok "/steam carries no SteamID64" || bad "/steam carries a SteamID64"
cc=$(curl -sI --max-time 20 "$B/site.css?v=0123abcd" | grep -i '^cache-control' | tr -d '\r' | cut -d' ' -f2-)
[ "$cc" = "public, max-age=31536000, immutable" ] && ok "versioned site file is immutable" || bad "versioned cache-control = '$cc'"
rtc=$(curl -s -o /dev/null -w '%{http_code}' --http1.1 -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' --max-time 10 "$B/rtc")
[ "$rtc" = 101 ] && ok "/rtc upgrades (101)" || bad "/rtc = $rtc (want 101)"
w=$(curl -s --max-time 20 "$B/api/wordle"); wc_=$(printf '%s' "$w" | python3 -c 'import json,sys
try:
  d=json.load(sys.stdin); print("ok" if sorted(d.keys())==["answer","date","sourceName"] and len(str(d["answer"]))==5 else "shape")
except Exception: print("bad")')
if [ "$wc_" = ok ]; then ok "/api/wordle answers {answer,date,sourceName}"; elif [ "$MODE" = live ] || [ "${GATE_WORDLE:-}" = require ]; then bad "/api/wordle = $(printf '%s' "$w" | head -c 80)"; else printf 'skip /api/wordle (no upstream here: %s)\n' "$(printf '%s' "$w" | head -c 60)"; fi
if [ "$MODE" = live ]; then
  r=$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 20 "http://${B#https://}/"); [ "$r" = "301 $B/" ] && ok "http redirects to https" || bad "http redirect = $r"
  curl -sI --max-time 20 "$B/" | grep -qi '^strict-transport-security' && ok "HSTS present" || bad "no HSTS"
fi
[ $fail = 0 ] && echo "GATE PASSED ($MODE, $B)" || { echo "GATE FAILED ($MODE, $B)"; exit 1; }
