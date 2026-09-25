# Minecart Chain Train 
Turn ordinary Minecraft minecarts into fully controllable steam trains.
Minecart Chain lets you connect carts into real train formations, convert furnace minecarts into controllable steam locomotives, and manage your train with simple lever-based controls.
Build freight trains, passenger trains, industrial railways, station yards, and steam-powered transport systems using familiar Minecraft mechanics expanded with more realistic train behavior.


## Features
Connect Minecarts with Iron Chains
Use Iron Chains to link minecarts together.
Connected minecarts move as one train, allowing you to create longer and more useful rail formations.
Steam Locomotive Conversion
Place a furnace minecart at the front of your connected train to turn it into a controllable steam locomotive.
The locomotive can pull connected wagons and reacts to throttle, brake, direction, fuel, water, and signal controls.


## Fuel and Water System
**Fuel and water consumption is optional and configurable (disabled by default).**
You can enable or disable refueling requirements anytime using commands:
- `/minecartchain refueling <true|false>` (or `/locomotive refueling <true|false>`)
- `/minecartchain fuel <true|false>`
- `/minecartchain water <true|false>`

When enabled, supported fuel includes all vanilla furnace fuels (coal, charcoal, logs, lava buckets, etc.), and water can be added with water buckets. Fuel and water are consumed only while moving and scale with train length.

## Radio Locomotive (with PeterWolf's TX/RX Redstone)
When playing with **PeterWolf's TX/RX Redstone** (`peterwolfs_txrx_redstone`), you can craft or upgrade to a wireless **Radio Locomotive**:
- **Wireless Throttle:** Set 8 jumpers for the throttle channel (0–255) to drive the locomotive with a remote Potentiometer / Transmitter.
- **Wireless Reverse:** Set 8 jumpers for the reverse channel (0–255). Reverse can only engage when the train is stopped and inverts drive direction without turning the locomotive body around.
- **Electronics Console UI:** Right-click the locomotive to open the jumper configuration panel matching the TX/RX block aesthetics with 2 rows of 4 jumpers, glowing red active states, and channel readouts.
Locomotive Screen
Furnace minecart locomotives now have a locomotive screen inspired by the vanilla furnace minecart.
The screen shows:
Current fuel reserves
Current water reserves
This makes it easier to manage long-distance railway travel.

## Train Controls
- Red Lever - Brake
Control the train’s braking force.
Use it to slow down, stop safely at stations, or manage movement near junctions.
- Navy Lever - Direction
Choose the direction of travel.
Available modes:
Forward
Reverse
This is useful for switching yards, backing trains into position, and managing compact railway systems.
- Black Lever - Throttle
Control locomotive power.
Available modes:
1/3 Throttle
Full Throttle
Use lower throttle for slow movement and precise control.
Use full throttle when you need more speed or pulling power.

## Signal Rail
Signal Rail is a powered-rail-looking track that sends a redstone-driven control signal to a connected train.
Right-clicking Signal Rail cycles its mode between:
Throttle
Brake
Reverse

When powered by redstone, Signal Rail toggles the selected locomotive lever once when any cart in the connected train passes over it.
Signal Rail works through the whole chained train.
This means a passenger cart or cargo cart can trigger the locomotive controls, not only the locomotive itself.


### Signal Rail can be used for:
- Automatic braking zones
- Direction switching
- Station control systems
- Yard automation
- Redstone-based railway logic

### Signal Rail Recipe
Signal Rail uses a shapeless recipe:
Powered Rail
Redstone Torch
Lever


## Locomotive Interaction
- **Ride** a furnace minecart with an empty hand (vanilla click).
- **Mount controls** by right-clicking a furnace minecart with a Lever (or by chaining it into a train).
- **Toggle controls** by clicking the red / navy / black lever panels on the locomotive.
- **Open fuel/water screen**: sneak + empty hand on a locomotive with mounted controls.
- **Fuel / water**: right-click with furnace fuels or a water bucket.
- **Unlink**: sneak + Iron Chain on a linked cart (returns chains).

## Lean Out of the Wagon
Improve visibility while riding inside a minecart.
Controls:
Press A to lean left
Press D to lean right
Use this to look around corners, watch your train, check nearby wagons, and enjoy a more immersive railway experience.


### What You Can Build
With Minecart Chain, you can create:
Steam-powered freight trains
Passenger services
Industrial minecart systems
Automated redstone railways
Switching yards
Station control systems
Long-distance railway routes
More realistic Minecraft train networks


## Compatibility

| Component | Version |
|-----------|---------|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.160.7+26.3 |
| Java | 25+ |
| Mod | 1.0.34 |

Optional: [Splinecart](https://modrinth.com/mod/splinecart) for mounted-track trains.

