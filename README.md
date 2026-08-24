# Villager Pickup

A server-side Fabric mod that lets you pick up villagers without boats or minecarts.
Crouch and right-click a villager with an empty hand to turn it into a villager spawn
egg that carries the villager's data. Use the egg on the ground to place it back.

Only the server needs the mod installed. Vanilla clients can join.

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

## Building

JDK 21 and a Fabric 1.21.10 development environment.

```bash
./gradlew build
```

The jar is in `build/libs/`.

## License

GPL-3.0-or-later
