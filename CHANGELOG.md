# Changelog

## 1.0.33

### Fixes
- Reverted the 1.0.32 slope-follow change. Projecting engine speed onto the incline was slowing the locomotive downhill.

## 1.0.32

### Fixes
- A wagon following a locomotive downhill no longer derails. Chain spacing no longer teleports the cart horizontally off a slope; movement stays along the rail, including the vertical of ascending tracks.

## 1.0.31

### Fixes
- Releasing a Signal Rail brake no longer sends the wagon the opposite way from the locomotive. The consist is started together in the engine's direction, and chain spacing no longer launches a following cart backward.

## 1.0.30

### Fixes
- Signal Rail **Brake** no longer releases a stopped train after five seconds. The brake stays on until the rail is unpowered or broken; the signal state is what lets the train go.

## 1.0.29

### Fixes
- A player riding a wagon behind a locomotive now turns their look with the cart. After a north-to-east curve the camera faces east instead of staying north.

## 1.0.28

### Fixes
- Chain tension no longer pulls, stops, or reverses a lever locomotive. Only the wagon is moved to keep spacing.

## 1.0.27

### Fixes
- A wagon is only taken off vanilla rails while it is itself on a curve or switch. Skipping physics for the whole consist while the locomotive was on a 3x3 made the wagon freeze and fall off between a switch and the curve.

## 1.0.26

### Fixes
- A wagon chained to a locomotive on a curve or switch no longer keeps vanilla minecart physics. That was driving the wagon into the engine and starting the bounce.

## 1.0.25

### Fixes
- Chain tension no longer pulls a following wagon onto a curve or switch while the locomotive is still being guided there.

## 1.0.24

### Fixes
- Fixed a crash on startup: `NewMinecartBehaviorMixin` now reaches the minecart through the parent `MinecartBehavior` class.

## 1.0.23

### Fixes
- Vanilla minecart gravity and off-rail physics are skipped while a cart is on an external track controller.

## 1.0.22

### Fixes
- Coupling impulses and follower-guide pulls are skipped while both (or the follower) carts are on an external track controller, so curve and switch mods can place the consist kinematically.
- Vanilla minecart gravity and off-rail physics are skipped for those carts so a curve controller can keep them on the path.

## 1.0.21

### Fixes
- **Crash on bootstrap** — fixed invalid mixin inject into `Entity.setRemoved` (method is not on `AbstractMinecart`). Link cleanup now soft-implements `onRemoval` instead.

## 1.0.20

### Fixes
- **Signal Rail reverse cooldown** — reverse mode no longer arms the 5s lockout when the train is still moving; cooldown is only applied after a successful toggle. Cooldown is shared by the whole train consist.
- **Furnace minecart riding** — empty-hand clicks no longer always open the locomotive screen. Players can ride furnace minecarts again.
  - Controls: click lever panels on a locomotive
  - Menu: sneak + empty hand on a locomotive with mounted controls
  - Mount controls: right-click with a lever
- **Stale chain links** — destroying a linked cart clears partner links (chunk unload still keeps NBT links). Loaded non-reciprocal links are pruned during tick.
- **Iron chain refunds** — manual unlink returns chains to the player; distance snaps and cart destruction drop chains with a break sound.

## 1.0.19

- Updated official mod icon (`icon.png`).
