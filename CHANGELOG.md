# Changelog

## 1.1.3

### Fixes
- **Crash without TX/RX Redstone mod:** Radio Locomotive and Radio Steam Locomotive advancement files now include `fabric:load_conditions` (`fabric:all_mods_loaded`), matching their corresponding recipes. Previously the advancements loaded unconditionally and crashed the game during registry loading because `peterwolfs_txrx_redstone:receiver` was not in the item registry.

## 1.1.2

### Fixes & Improvements
- **Radio Controlled Steam Locomotive:**
  - In layout mode, receiving a 50% signal (redstone power 7 or 8 / potentiometer 12:00 neutral) now activates full train brake (`throttle = -1.0F` and `applyTrainBrake`) instead of coasting in idle (`0.0F`).
  - Matches model railway behavior: the locomotive halts and locks firmly on stop without drifting, rolling downhill, or requiring manual braking.
  - Automatically brakes when disarmed, when waiting for a reverse stop, when running out of fuel/water, or on 0% signal in classic mode.
  - Driver inputs (W/S/R) in the cab no longer fight or override radio control on radio locomotives.
  - In-cab HUD clearly displays radio control status (`[RADIO]`, `Brake: 100% [STOP]`).

## 1.1.1

### Features
- **Steam Locomotive:**
  - Added new 2-block Steam Locomotive (`minecart_chain:steam_locomotive`) featuring a steel boiler, smoke box, chimney, detailed cab interior with boiler backplate gauges, seating bench, and 8 drive wheels with animated side rods.
  - Interactive player cab driving with **W / S** throttle and brake control.
  - **Emergency Brake:** double-tapping **S** within 300 ms immediately applies 100% full brake (`-1.0` throttle).
  - **Reverser key (R):** seamlessly reverses driving thrust without flipping the locomotive yaw or model orientation (boiler and chimney always point forward along the track).
  - **Remote Coupler key (C):** couples or uncouples wagons dynamically from the cab. Extended coupling range detects the nearest wagon up to **4 blocks** behind the consist tail (or tender).
  - Custom cab HUD displaying live speed (km/h), throttle percentage, reverser status, and coupler state.
  - **Extended Lever Reach:** while seated in the locomotive cab, the player's interaction reach for trackside levers (`LeverBlock`) is doubled (up to 9+ blocks).

- **Steam Locomotive with Tender (Consist Pair):**
  - Added combined item (`minecart_chain:steam_locomotive_with_tender`) that spawns the steam engine coupled directly to its coal tender on rails.
  - Kinematic hinged articulation: the tender follows the authoritative 3D breadcrumb track trail of the locomotive, matching velocity without lagging behind.
  - Consist buffer beams: wagons and tender cushion against each other during braking and reverse moves, preventing cars from telescoping or clipping through the locomotive.
  - The cab coupler key (**C**) automatically manages wagons behind the tender.

- **Long Minecart (Double Length):**
  - Added new 2-block minecart platform (`minecart_chain:long_minecart`) with 8 animated wheels.
  - Crafting recipe using iron ingots and a standard minecart.

- **Long Minecart with Double Chest:**
  - Added new 2-block cargo wagon (`minecart_chain:long_chest_minecart`) with a built-in double chest offering **54 inventory slots**.
  - Full player access: right-click to open the double chest inventory menu; items persist and drop upon destruction.
  - Detailed model with wooden chest body, dark iron trims, lid seam, and dual gold latches.
  - Shapeless crafting recipe: Long Minecart + 2 Chests.
  - Added to *Redstone Blocks* and *Tools & Utilities* creative tabs.

### Physics & Cornering Improvements
- **Tight Curve Navigation:**
  - Reworked multi-axle wagon guidance through tight bends: body yaw and pitch are sampled between bogie axle positions along the recorded rail path, preventing corner-cutting and derailment.
  - Reduced curve rolling resistance, restoring full tractive power so locomotives can pull heavy consists uphill.
  - **Direction Lock:** wagon drag on curves and deceleration cannot rotate, bounce, or invert the locomotive's established travel direction.

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
