# Combat HUD (Forge 1.20.1)

Runs on both client and server (install on both, same version). Replaces the survival HUD (Warzone-style) and the survival inventory screen (Division-style).
Creative / spectator keep the vanilla UI.

## Build
This folder has no Gradle wrapper. Copy `gradlew`, `gradlew.bat` and the `gradle/` folder from the
Forge 1.20.1 MDK (47.3.x), then:

    ./gradlew genEclipseRuns   # or genIntellijRuns
    ./gradlew runClient
    ./gradlew build            # jar in build/libs

## Phase 1 (this commit)
HUD: info bar, compass, squad list (online players), hotbar, weapon/ammo panel.
Inventory: dark backdrop, tab header, stat boxes, per-slot tiles, vitals + in-hand panels, player model.

## TACZ
No TACZ jar is needed to build.
- TACZ's ammo HUD is cancelled via `config/combathud-client.toml` -> `hiddenOverlays` (default `tacz:tac_gun_hud_overlay`,
  taken from TACZ's own ClientSetupEvent). Alternative without this mod: `GunHUDEnable = false` in `config/tacz-client.toml`.
- The weapon panel shows mag, chamber (+1), fire mode and reserve ammo by reflecting on TACZ's API; if that fails it logs once and shows nothing extra.
- TACZ's heat bar, kill counter, interact text and crosshair are left alone.

## Motion and feel
Smoothed bars with a white damage trail, red damage vignette (and low-health pulse), hit flash on your squad row,
ammo count pop on reload/shot, orange/red count at low/empty mag plus a pulsing LOW AMMO / RELOAD / NO AMMO alert (a chambered +1 round counts as ammo, except on open-bolt guns, same as TACZ),
weapon panel slides in on item change, hotbar selector slides, inventory fades/slides in.
All toggles in `config/combathud-client.toml`: `animations`, `damageVignette`, `lowAmmoAlert`.
Not done: hit markers (needs a client-side signal from TACZ), smoothing for teammates' bars.

## Region titles
"NOW ENTERING" + area name, typed in, with squares / flanking lines (Blacksite Zeta layout, Division 2 styling). Works on any server.
Triggers:
- Walk-in boxes: `config/combathud-regions.json` (created on first run; `enabled: false` example inside). `/combathud reload` re-reads it.
- From the server, command blocks or functions: `/tellraw @a {"text":"[combathud:title] Sector 2"}` or
  `... "[combathud:title] NOW LEAVING | Sector 2"`. Players with the mod get the banner (the line is hidden from chat);
  players without it see the raw text.
- Test locally: `/combathud title Sector 2`.
Sound toggle: `regionTitleSound` in `config/combathud-client.toml`.

## Contracts (server-driven)
Left side of the HUD, above your player block. Ops / command blocks / functions drive them:

    /contract add <targets> <id> <TYPE> <target> <reward> <seconds> <description...>
    /contract progress <targets> <id> <amount>     (completes at target)
    /contract set <targets> <id> <value>
    /contract complete|fail|remove <targets> <id>
    /contract clear <targets>
    /contract onfinish <targets> <id> <command...>   command to run when the contract completes ("-" clears it)
    /contract onfail   <targets> <id> <command...>   same, when it fails or times out

Example: `/contract add @a scav1 SCAVENGER 3 2600 90 Supply Boxes Opened` (seconds -1 = no timer).
Finish/fail commands run as the player with full permission, no chat output. Placeholders: `_lp` (or `{player}`) = the contract's owner, `{reward}` `{id}` `{type}` `{target}`.
Example: `/contract onfinish @a scav1 scoreboard players add _lp cash {reward}` (`_lp` = local player). Set them right after `/contract add` (re-adding an id resets them).
Accepted / complete / failed show as banners. Expired timers fail the contract. Contracts persist across relogs.
`config/combathud-common.toml` -> `rewardObjective`: scoreboard objective that gets the reward on completion (empty = off).
Colors: SCAVENGER yellow, PROTECT red, BOUNTY purple, other types get a palette color.
Wire game events to contracts with vanilla tools, e.g. `execute as @a[scores={...}] run contract progress @s scav1 1`.

Server-triggered titles: `/hudtitle <targets> <text>` ("Sector 2" or "NOW LEAVING | Sector 2"),
or with a color: `/hudtitle <targets> style <default|complete|fail|team> <text>`.
Title colors: orange = default (also contract accepted and region titles), green = contract complete, red = contract failed,
blue = class. Scoreboard teams are classes: `/team join` into a class shows a blue CLASS SELECTED banner to that player only, with the class display name exactly as set (`/team modify <team> displayName ...`).

## Squad
No squad on the HUD. The inventory has a SQUAD tab that currently lists every online player, up to 8 squad slots (two columns of four; `squadMax` in `config/combathud-client.toml`, 1-8) (scoreboard teams are classes in this pack, so they are not used for squads).

## TODO
- Minimap, contracts panel, cash/XP readout, pickup prompt ("F EQUIP")
- Free-layout inventory (Access Transformer on Slot.x/y)
- Config (toggle elements, positions, colors), Changed-mod transfur/latex bar
