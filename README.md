# SimpleTPA Plugin

**SimpleTPA** is a Minecraft Paper plugin that implements teleport request functionality, including a `/tpback` command with configurable warmup and cooldown.

> **Forked from [Jelly-Pudding/simpletpa](https://github.com/Jelly-Pudding/simpletpa)** — extended with `/tpback` warmup, damage cancellation, location expiry, and additional configuration options.

## Features
- Send teleport requests to other players with `/tpa <player>`
- Accept specific player teleport requests with `/tpaccept <player>`
- Deny teleport requests with `/tpdeny <player>`
- Cancel your own requests with `/tpacancel <player>` or `/tpacancel all`
- Return to your pre-teleport location with `/tpback`
- Configurable warmup on `/tpback` — cancelled if you take damage
- Requests expire automatically after a configurable timeout
- Configurable cooldown between sending requests to prevent spam
- Configurable cross-world teleportation support

## Installation
1. Download the latest release from the [Releases](https://github.com/asperavl/simpletpa/releases/latest) page.
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Configuration
In `config.yml`, you can configure:
```yaml
# Time in seconds before a teleport request expires
request-timeout: 120

# Cooldown in seconds between sending teleport requests
request-cooldown: 10

# Whether to allow cross-world teleportation
allow-cross-world: false

# Cooldown in seconds between /tpback uses
tpback-cooldown: 30

# Seconds before a saved tpback location expires (0 = never expires)
tpback-expiry: 300

# Whether to clear the saved tpback location when the player dies
tpback-clear-on-death: true

# Seconds to wait before /tpback teleports you (0 = instant). Taking damage cancels it.
tpback-warmup: 3
```

## Commands
| Command | Description |
|---|---|
| `/tpa <player>` | Send a teleport request to another player |
| `/tpaccept <player>` | Accept a pending teleport request |
| `/tpdeny <player>` | Deny a pending teleport request |
| `/tpacancel <player>` | Cancel your outgoing request to a player |
| `/tpacancel all` | Cancel all your outgoing teleport requests |
| `/tpback` | Teleport back to your location before your last TPA |

## Permissions
| Permission | Description | Default |
|---|---|---|
| `simpletpa.tpa` | Use `/tpa` | true |
| `simpletpa.tpaccept` | Use `/tpaccept` | true |
| `simpletpa.tpdeny` | Use `/tpdeny` | true |
| `simpletpa.tpacancel` | Use `/tpacancel` | true |
| `simpletpa.tpback` | Use `/tpback` | true |

## Example Usage
1. Player A sends a request: `/tpa PlayerB`
2. Player B receives a notification and accepts: `/tpaccept PlayerA`
3. Player A is teleported to Player B — their previous location is saved
4. Player A can return with `/tpback` (after the warmup, if configured)
5. If Player A takes damage during the warmup, the teleport is cancelled
