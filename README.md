# Minecart Chain — NeoForge

NeoForge port for Minecraft 26.1.2. The gameplay implementation and mixins are shared from the sibling `minecart-chain` source tree; loader registration and interaction events are implemented with NeoForge APIs.

## Build

```sh
../minecart-chain/gradlew build
```

Output: `build/libs/minecart_chain-neoforge-1.1.0.jar`

## Smoke test

```sh
../minecart-chain/gradlew runServer
```
