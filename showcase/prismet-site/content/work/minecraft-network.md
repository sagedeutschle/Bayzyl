<!-- Wording for the "Minecraft server network" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
Minecraft server network

## subtitle
A Velocity proxy, Dockerized Paper backends, and one admin command

## status
In production

## year
2026

## role
Design, setup, operations

## summary
A multi-server network: a Velocity proxy handles login, whitelisting, and network-wide bans, while containerized Paper backends run a multi-world survival server and a public hub. A single mc-admin command routes every task to the layer where it belongs, so an IP ban never lands on a backend and locks out the proxy.

## facts
- Proxy: Velocity + LibertyBans + whitelist gate
- Backends: 2 Paper servers in Docker
- Ops: one CLI for whitelist, bans, ops, gamerules, RCON

## highlights
- Whitelist by Mojang UUID, resolved automatically from a player name
- Per-world gamerules through Multiverse
- Escape hatches straight to the proxy console or backend RCON
