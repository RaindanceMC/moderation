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
