# Block Mimicry

A NeoForge mod that adds the **Mimic Block**: a block that copies the look and properties of any other block.

## Usage

- **Copy a block:** right-click a placed Mimic Block with a block. One of that block is used up.
- **Place it pre-copied:** place a Mimic Block while holding a block in your off-hand.
- **Take it back out:** sneak and right-click with an empty main hand. Breaking the Mimic Block also drops the copied block.

While empty, the Mimic Block is see-through.

## Recipe

4 of any dye around 1 of any wool makes 4 Mimic Blocks.

## What gets copied

- Look, shape and hitbox (including colored blocks like grass and leaves)
- Sounds, slipperiness, walking speed, and jump height
- Light and redstone power output
- Mining speed and explosion resistance
- Fences, panes, and walls connect to each other, and two stacked Mimic Blocks copying a door or tall flower form one whole block

## Limitations

- Blocks with their own storage or data (chests, furnaces, beds, signs) can't be copied.
- It's the look, not the function. Copied doors and trapdoors don't open, copied redstone dust doesn't carry power, and copied buttons and levers don't toggle.
- Stepping, landing, and walking-into effects aren't copied (magma damage, cobweb slowdown, hay bale landing).
- Real fences, panes, and walls don't connect to a Mimic Block copying one. Mimic Blocks connect to each other.

## Compatibility

- **Jade:** shows the copied block's name and mod.
- **JEI:** adds an info page for the Mimic Block.

## Requirements

- Minecraft 26.1.2
- NeoForge 26.1.2.122 or newer

## License

MIT. See [LICENSE](LICENSE).
