<div align="center">

# ThuzMenu

**A fully configurable SMP menu for Paper servers, forged by [GAMERTHUZ](https://github.com/NOTGAMERTHUZ).**

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b47a?style=flat-square)
![Paper](https://img.shields.io/badge/Paper-26.2-00e5ff?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-7c4dff?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

</div>

---

## What is ThuzMenu?

ThuzMenu gives your players one clean GUI for everything on your SMP: teleports, homes, wilderness,
online players, utilities, server status, stats and links. Think of it as a "mod menu" for survival.

**Everything is editable from the server's file manager.** Every menu is a plain `.yml` file, and every
button runs commands you choose. It works with your existing `tpa`, `rtp` and `sethome` plugins because
buttons simply run their commands.

## Features

- **Menu hub:** `/menu` or a hotbar item (right-click, or press Shift + F)
- **Live menus:** status, stats and the player list refresh while open
- **Player list menu:** heads of online players with page buttons, click to send a tpa request
- **Built-in reliability helpers:** `[spawn]` and `[back]` with warm-up, cooldown, cancel-on-move,
  and a safe-landing check so you do not return into lava or a wall after dying
- **Utilities:** portable crafting table, ender chest and trash bin
- **Per-button controls:** permissions, hidden buttons, cooldowns, separate left / right / shift click actions
- **Placeholders:** `{player}`, `{ping}`, `{tps}`, `{online}`, `{playtime}`, `{deaths}` and more, plus
  PlaceholderAPI (`%placeholder%`) if installed
- **MiniMessage and `&` color codes** everywhere
- **Hot reload:** `/thuzmenu reload`, no restart needed

## Requirements

- Paper 26.2 (or a fork on 26.2), Java 25
- PlaceholderAPI is optional

## Installation

1. Put `ThuzMenu.jar` in `plugins/`.
2. Start the server once. The folder `plugins/ThuzMenu/` is created with `config.yml` and `menus/`.
3. Edit the files, then run `/thuzmenu reload`.

## Commands and permissions

| Command | Permission | Description |
|---|---|---|
| `/menu [name]` (aliases `/smp`, `/smpmenu`) | `thuzmenu.use` (everyone) | Open a menu |
| `/thuzmenu reload` | `thuzmenu.admin` (OP) | Reload config and menus |
| `/thuzmenu list` | `thuzmenu.admin` | List loaded menus |
| `/thuzmenu open <menu> [player]` | `thuzmenu.admin` | Force-open a menu |
| `/thuzmenu give <player>` | `thuzmenu.admin` | Give the hotbar item |

Other permissions: `thuzmenu.spawn`, `thuzmenu.back`, `thuzmenu.bypass.cooldown`, `thuzmenu.bypass.warmup`.

## Connecting your own plugins

The default buttons run these commands: `tpa`, `tpahere`, `tpaccept`, `tpdeny`, `rtp`, `sethome`, `home`,
`homes`, `delhome`. If your plugins use other names, open the menu file and change the line, for example in
`menus/homes.yml`:

```yaml
home:
  actions:
    - "[player] home"      # change to e.g. "[player] myhome"
```

## Making a button

```yaml
buttons:
  my-button:
    slot: 13
    material: DIAMOND
    name: "<aqua><bold>Hello {player}"
    lore:
      - "<gray>Ping: <white>{ping}ms"
    cooldown: 5
    actions:
      - "[player] say hi"
      - "[sound] entity.player.levelup 1 1"
```

### Actions

| Action | What it does |
|---|---|
| `[player] cmd` | Run a command as the player (also the default with no tag) |
| `[console] cmd` | Run a command as the console |
| `[menu] name` | Open another menu |
| `[return]` | Go back to the previous menu |
| `[close]` | Close the menu |
| `[page] next` / `prev` | Change page (player list) |
| `[refresh]` | Redraw the menu |
| `[message]` / `[broadcast]` / `[actionbar]` | Send text |
| `[title] main;sub` | Show a title |
| `[sound] key vol pitch` | Play a sound |
| `[url] https://...` | Send a clickable link |
| `[delay] ticks` | Wait before the next action |
| `[spawn]` / `[back]` | Built-in safe teleports |
| `[workbench]` / `[enderchest]` / `[trash]` | Utilities |

### Button options

`slot` / `slots`, `material`, `name`, `lore`, `amount`, `glow`, `head`, `actions`,
`left-actions`, `right-actions`, `shift-actions`, `permission`, `view-permission`,
`cooldown`, `close-on-click`.

## Building

Requires JDK 25 and Gradle.

```bash
git clone https://github.com/NOTGAMERTHUZ/ThuzMenu.git
cd ThuzMenu
gradle build
```

The jar is in `build/libs/`. A GitHub Actions workflow also builds it on every push.

## License

[MIT](LICENSE). Copyright (c) 2026 GAMERTHUZ.
