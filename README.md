# Villager Pickup

A server-side Fabric mod that lets you pick up villagers without boats or minecarts.
Crouch and right-click a villager with an empty hand to turn it into a villager spawn
egg that carries the villager's data. Use the egg on the ground to place it back.

Only the server needs the mod installed. Vanilla clients can join.

This is the Fabric port for Minecraft 26.1.2 (deobfuscated mappings, Java 25).
There is also a Fabric 1.21.10 version in this repository.

## How it works

- Crouch and right-click a villager with an empty main hand to capture it. The
  villager is removed and you get a villager spawn egg in return.
- The egg keeps the full villager data: profession, level, workstation and bed
  positions, and current trades. Librarian enchanted-book trades show the enchant
  and level.
- Baby villagers keep their age. The egg is named "Baby Villager Spawn Egg".
- The tooltip lists the villager's details, so you can check a trader before placing
  it.
- Use the egg on a block, or right-click another entity with it, to place the
  villager. In survival the egg is consumed.

## Requirements

- Fabric Loader 0.19.3 or newer for Minecraft 26.1.2.
- Fabric API for 26.1.2.
- Java 25.

## Building

JDK 25 and Gradle 9.7 or newer (Loom 1.17.19).

```bash
./gradlew build
```

The jar is in `build/libs/`.

## License

GPL-3.0-or-later
