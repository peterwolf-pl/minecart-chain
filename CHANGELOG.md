# Changelog

## 1.1.0

### Features
- **Radio Locomotive Integration:**
  - Added new Radio Locomotive (`minecart_chain:radio_locomotive`), wirelessly controlled via PeterWolf's TX/RX Redstone mod (`peterwolfs_txrx_redstone`).
  - Added shapeless crafting recipe: Furnace Minecart + PeterWolf's Receiver.
  - Added direct crafting recipe from basic materials: Minecart + Furnace + PeterWolf's Receiver -> Radio Locomotive.
  - Added recipe advancements with automatic recipe book unlocking upon acquiring ingredients in survival mode.
  - Added in-world conversion: right-clicking a regular furnace minecart while holding a PeterWolf Receiver installs the wireless module.
  - Creative tab integration in *Redstone* and *Tools & Utilities* when TX/RX mod is loaded.
  - Custom visual model with front chimney, rear receiver module, antenna rod, and no manual levers.
- **Model Railway Layout Mode (Tryb makietowy - Default):**
  - Added new Model Railway Layout Mode, enabled by default (toggleable via `/minecartchain layoutMode <true|false>` or `/minecartchain layout` / `/minecartchain makieta`).
  - **Potentiometer Controls (Channel 1):**
    - 50% (Godzina 12:00, Signal 8 / Level 5): Stop / Brake (`sygnał do zatrzymania`). Default state when placing/building the potentiometer.
    - > 50% to 100% (Godzina 5:30, Signal 9–15): Forward movement (`ruch do przodu`), speed proportional to knob rotation (100% at 5:30).
    - < 50% down to 0% (Godzina 7:30, Signal 0–6): Reverse movement (`ruch do tyłu`), drives in reverse without flipping locomotive body.
    - Updated 3D potentiometer needle sweep: 0% at 7:30 (-135°), 50% at 12:00 (0° straight up), 100% at 5:30 (+135°).
    - Disabled pointer red glowing backlight: clean unlit mechanical metallic needle for model railway controllers.
  - **Automated Remote Coupler (Channel 2):**
    - High signal (> 0): Automatically couples to nearest unlinked wagon within 2 blocks (`max 2 bloki od lokomotywy`), plays chain coupling sound.
    - Low signal (== 0): Uncouples wagons from the locomotive without dropping item entities, plays chain release sound.
  - UI displays dedicated layout readouts: `12: STOP`, `10: FORWARD`, `0: REVERSE` and live coupler status (`● COUPLED` / `○ UNCOUPLED`).
- **Interactive Jumper Interface (GUI):**
  - Right-clicking the Radio Locomotive opens an electronics console styled exactly like the PeterWolf TX/RX receiver block (white wool carpet border, dark green PCB, copper traces, and gold contact pads).
  - 8 Throttle/Speed jumpers (bits 0–7) in 2 rows of 4 for receiving potentiometer/transmitter speed signals (channel 0–255), with real-time 15-segment LED signal meter and speed percentage readout.
  - 8 Reverse jumpers (bits 0–7) in 2 rows of 4 for reverse signals (channel 0–255), with real-time directional status indicator.
  - Visual jumper caps matching block textures: glowing radiant red caps with filament core (`#FFBE8C`) and aura when ON; dark unlit graphite caps with exposed gold pins when OFF.
  - Channel badge with binary dot readout (`CH: X [ ● ● ○ ○ ○ ○ ○ ○ ]`).
  - Idempotent networking via `RadioLocomotiveJumperPayload`: sends explicit target states to eliminate double-toggle bounce and latency glitches.
- **Reverse & Direction Logic:**
  - Reverse can only be switched when the locomotive and connected consist are completely stopped (`isTrainStopped`).
  - If a reverse signal arrives while the train is in motion, the system arms a standby state (`REVERSE PENDING - STOP TRAIN`) and cleanly engages reverse the moment the train comes to a halt.
  - Reversing inverts driving thrust without turning the locomotive body or visual yaw around (chimney stays forward, receiver stays rear).
  - Fixed directional flip when throttle drops to zero: locked yaw while stopped and preserved established heading against braking deceleration bounces and buffer collisions.
  - Fixed chain rendering alignment: eliminated unwanted Z-axis roll that offset alternating chain links sideways; chain now connects dead-center between wagon and locomotive.
  - Added Safety Arming Procedure for newly placed radio locomotives: newly placed locomotives are locked/disarmed upon placement so they do not drive away, allowing the player to safely configure jumpers. To arm the locomotive for the first time, throttle must first be brought to 50% (neutral stop / 12:00); a chime confirms arming, and the locomotive is then ready to drive forward or reverse.
  - Added track alignment filter to remote auto-coupling: prevents coupling to wagons on adjacent parallel tracks.
- **Configurable Refueling System & Commands:**
  - Locomotive fuel (coal) and water requirement is now configurable and **disabled by default**.
  - Added `/minecartchain refueling <true|false>` (alias `/locomotive refueling <true|false>`) to toggle refueling requirements.
  - Added `/minecartchain fuel <true|false>` and `/minecartchain water <true|false>` for granular control.
  - Added `/minecartchain` status overview command.
  - Settings are saved to `config/minecart_chain.json` and persist across restarts.
  - Refueling GUI slots and gauges remain functional for players who choose to load coal and water buckets.
- **Minecraft 26.3 Port:**
  - Updated to Minecraft 26.3 (Wilderness Bound), Loom 1.17, Fabric API 0.160.7+26.3, and Java 25.

## 1.0.34

- Port to Minecraft 26.3.

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
