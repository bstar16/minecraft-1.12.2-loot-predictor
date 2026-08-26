# Minecraft Loot Predictor

A standalone command-line predictor for naturally generated loot containers in **Minecraft Java Edition 1.12.2**.

It does not contain a hand-written approximation of the loot tables. It loads Mojang's original 1.12.2 server code in an isolated class loader and calls the original loot-table engine. That preserves Java `Random`, pool rolls, weights, stack splitting, chest-slot shuffling, item data values, random enchantments, level enchantments, treasure enchantments, and item NBT.

The program itself is original code and does **not** redistribute Minecraft. On first setup it downloads Mojang's official server jar and refuses to use it unless its SHA-1 is exactly `886945bfb2b978778c3a0288fd7fab09d315b25f`, the hash in Mojang's [official 1.12.2 version descriptor](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json).

## What is exact

- Given a nonzero `LootTableSeed`, `predict` returns the exact 1.12.2 items, counts, data values, enchantment NBT, and container slots.
- Given a saved, unopened container, `scan` reads its real `LootTable` and `LootTableSeed` from the Anvil/NBT save and predicts the exact result without opening or changing the world.
- Given only a world seed, `replay-spawn` uses Mojang's verified vanilla 1.12.2 server to generate and save the spawn chunks, then scans their unopened containers. This is exact because the complete terrain, biome, decorator, structure-piece, and population RNG chain is replayed by the original game.

Supported vanilla tables:

- simple dungeons
- abandoned mineshafts
- desert pyramids
- jungle temples and their dispensers
- stronghold corridors, crossings, and libraries
- village blacksmiths
- nether fortresses
- end-city treasure
- igloos, woodland mansions, and bonus chests

Forge's 1.12.x documentation confirms that vanilla naturally generated loot uses JSON loot tables such as `minecraft:chests/simple_dungeon`: [Forge 1.12 loot-table documentation](https://docs.minecraftforge.net/en/1.12.x/items/loot_tables/).

## Important predictability distinction

The world seed determines the whole generated world, but there is no general shortcut of the form `worldSeed + chest coordinates = loot` in 1.12.2.

During generation, Minecraft advances shared chunk/structure population RNG state and calls `nextLong()` when assigning a container's `LootTableSeed`. The exact state at that call depends on terrain and biome decisions, earlier decorators, structure pieces, neighboring structure intersections, and generation order. Consequently:

- **World seed alone, without replaying generation:** container contents are state-dependent and are not claimed as predictable by this tool.
- **World seed plus vanilla replay:** exact for the chunks actually generated and saved by the replay.
- **Saved unopened container with nonzero `LootTableSeed`:** exact, and no world seed is needed for the contents.
- **`LootTableSeed: 0`:** not predictable. Vanilla treats zero specially and constructs a new time-dependent `Random` when the container opens.
- **Already opened container:** its `LootTable` tag is removed; `scan` intentionally does not call its present contents a prediction.

The current world-seed replay covers the normal spawn chunks generated during first server startup. Arbitrary distant structure location and chunk pre-generation are not implemented in this release.

## Windows quick start

Requirements: 64-bit Java 8 or newer. Java 8 is closest to Minecraft 1.12.2; modern Java also works for prediction and scanning, although the vanilla replay command is most reliable on Java 8.

1. Open this folder in File Explorer.
2. Double-click `setup.bat`. This downloads and verifies the official 1.12.2 server jar into `runtime`.
3. Double-click `test.bat`. All four checks should print `PASS`.
4. Open Command Prompt or PowerShell in this folder for the commands below.

### Predict from a known LootTableSeed

```bat
loot-predictor.bat predict --table simple_dungeon --loot-seed 12345
```

For the jungle-temple dispenser, the program defaults to 9 slots. All listed chest tables default to 27 slots. Override with `--slots N` if testing a custom container.

### Scan an existing 1.12.2 save

Back up the save as normal. The scanner is read-only.

```bat
loot-predictor.bat scan --save "%APPDATA%\.minecraft\saves\My World"
```

For a container to be found, its chunk must have been generated and saved, the container must still have `LootTable` and `LootTableSeed`, and it must not have been opened. The scanner reads overworld `region`, Nether `DIM-1\region`, and End `DIM1\region` files. It supports tile containers and loot chest minecarts.

### Exact spawn-area replay from a world seed

Read the [Minecraft EULA](https://aka.ms/MinecraftEULA). The command only writes to the new/empty output folder you name.

```bat
loot-predictor.bat replay-spawn --world-seed -123456789 --output "%CD%\replay-123456789" --accept-eula
```

The tool starts the verified vanilla server with the requested seed, waits for spawn generation to finish, sends `stop`, then scans `replay-123456789\world`. Keep the generated folder if you want to inspect the result in Minecraft, or delete it yourself later.

### See the accuracy boundary

```bat
loot-predictor.bat explain --world-seed -123456789
```

### Check Java Random output

```bat
loot-predictor.bat java-random --seed 0 --count 5
```

The tool deliberately uses `java.util.Random`, the same 48-bit linear congruential generator used by Minecraft 1.12.2, rather than a language-specific substitute.

## Tests

Run:

```bat
test.bat
```

The self-test checks:

1. a fixed Java `Random.nextLong()` reference vector;
2. the Mojang server jar SHA-1;
3. repeatable `simple_dungeon` generation with exact container slot filling;
4. enchanted item NBT from `end_city_treasure`.

To rebuild from source, install a JDK and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

The source targets Java 8 bytecode and has no compile-time third-party dependencies.

## CLI reference

```text
setup [--server-jar PATH]
predict --table NAME --loot-seed N [--slots 27] [--server-jar PATH]
scan --save PATH [--server-jar PATH]
replay-spawn --world-seed N --output NEW_FOLDER --accept-eula [--server-jar PATH]
explain [--world-seed N]
java-random --seed N [--count 5]
self-test [--server-jar PATH]
```
