# RaindanceMC/moderation

- **`moderation-module`** — Rain's Flexible Modules — Moderation: The moderator's half of the server: the commands, the screens, the reasons, the escalation ladder, the reports and the staff notes. The punishments themselves — who is banned, muted or frozen, and for how long — are RainsCore's, so a server that removes this module still enforces every ban it has already handed out.
- **`moderation-standalone`** — Rain's Moderation: Rain's Moderation, as a plugin of its own. Nothing here but a build file and a descriptor: the moderation module and the standard wrapper are shaded together, and that is the whole plugin. The name and the data folder are unchanged from the standalone plugin it replaces.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework). Split out of `rains-flexible-modules` on 16.10.2026, history kept.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `moderation-standalone/target/RainsModeration-3.0.0.jar` — also published as `de.raindancer.modules:moderation-standalone:3.0.0`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.

## Anti-cheat (3.16.0 / 3.17.0)

Grim detects; this module keeps the network's account of it. What was added for this server:

- **Excuses** (`rules/ExcuseRule`): a movement flag while riding one of Core's companions (or three seconds after), or with flight the server granted (a claim's perk, a game, `/fly`), is written down as excused and raises no level. A flag while the server lags (below `min-tps`) or the connection is slow (over 400 ms) counts half and never kicks or bans.
- **Alts** (`rules/AltRule`, `store/KnownAddresses`, `service/AltWatch`): a join from an address a banned account used within a month, or one four or more accounts share, is a line to staff. Addresses are kept only as the network's keyed hash (`core.secrets()` "address-hash"), a month, in `ac_address`. Nobody is refused by it.
- **X-ray suspicion to staff**: past 90 % the anti-cheat alert channel says so (`anticheat.xray-suspect`), at most every ten minutes, before the report at `xray.report-score` fires.
- **X-ray ledgers follow the player** (`ac_xray`): written with every flush, read on arrival when the network's copy is newer.

## Bot accounts and macros (3.30.0)

Settings `moderation-bots` (in game under Anti-cheat). Staff are told, a case is written (`ac_suspect`, network-wide, deleted after `cases.keep-days`, default 30). Nobody is punished unless `bots.action` / `macros.action` is set (kick, freeze, ban with or without a length).

- **Bots** (`service/BotGuard`, `rules/JoinWaveRule`, `rules/NewcomerRule`): only accounts new to the network. Signals: same /24 (/48) as others in the wave window, names that differ only in trailing digits, no movement or head turn in the first `bots.watch-seconds`, chat spam, no client brand or a bot library's brand. Two signals make a case. A wave (`bots.wave-joins` in `bots.wave-seconds`) with a shared network or names locks new accounts out of the network for `bots.lockdown-minutes` (`/anticheat lockdown`). Network hashes (keyed, `address-hash`) stay in memory only for the wave window.
- **Macros** (`service/MacroWatch`, `rules/MacroRule`, `model/ClickCadence`): gaps between left clicks (arm swings not explained by mining, dropping or a right click) and right clicks. Regular: median absolute deviation under `macros.max-variation` % for `macros.minutes`, gaps of 200 ms and more, a held right button (4 ticks) excluded. Fast: more than `macros.max-cps` left clicks a second for 3 s. Measured on the input, not on AFK status.
- `/anticheat bots` (cases screen, also from the suspects screen), help page `mod-bots`.
