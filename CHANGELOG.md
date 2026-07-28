# Changelog

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
