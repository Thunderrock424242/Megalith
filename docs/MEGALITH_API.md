# Megalith API — Developer Documentation

> **Mod ID:** `megalith`  
> **Minecraft:** 1.21.1 · NeoForge  
> **Package root:** `com.thunder.megalith`

---

## What is Megalith?

Megalith is a NeoForge library mod that helps modders safely generate very large
structures in Minecraft worlds. It supplements (not replaces) vanilla datapack-
driven structure generation with:

- **Datapack JSON definitions** — describe a structure without writing Java.
- **Once-per-world guarantees** — a structure is placed at most once per save.
- **Phased / chunk-safe placement** — large structures are placed across many
  server ticks so the server never freezes.
- **Terrain adaptation** — level pads, smart foundations, buried placement, and
  cave carving before block placement begins.
- **Persistent tracking** — `SavedData` records where and whether each structure
  was generated, surviving server restarts.
- **Debug commands** — locate, force-place, reset, list, and validate structures
  at runtime.

---

## How it differs from vanilla structures

| Feature | Vanilla Structures | Megalith |
|---|---|---|
| Size | Small–medium | Huge (hundreds of blocks) |
| Placement timing | Chunk generation | Any time (phased) |
| Once per world | No built-in | Yes (`oncePerWorld: true`) |
| Terrain prep | None | FLAT_PAD / SMART_FOUNDATION / etc. |
| Config per modpack | Limited | Full JSON datapack |
| Debug tooling | None | `/megalith` commands |

---

## Adding a Megalith structure (JSON)

Create a JSON file at:

```
data/<your_modid>/megalith_structure/<structure_name>.json
```

### Minimal example

```json
{
  "id": "mymod:my_tower",
  "template": "mymod:my_tower/main"
}
```

### Full example

```json
{
  "id": "examplemod:particle_accelerator",
  "template": "examplemod:particle_accelerator/main",
  "pieces": [
    "examplemod:particle_accelerator/core",
    "examplemod:particle_accelerator/ring_north",
    "examplemod:particle_accelerator/ring_south",
    "examplemod:particle_accelerator/control_room"
  ],
  "oncePerWorld": true,
  "dimension": "minecraft:overworld",
  "minDistanceFromSpawn": 4000,
  "maxDistanceFromSpawn": 12000,
  "biomeTag": "minecraft:is_overworld",
  "avoidWater": true,
  "avoidVillages": true,
  "terrainMode": "SMART_FOUNDATION",
  "scanRadius": 96,
  "foundationDepth": 12,
  "chunkSafe": true,
  "placeInPhases": true,
  "maxBlocksPerTick": 5000
}
```

### Field reference

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | ResourceLocation | **required** | Unique identifier |
| `template` | ResourceLocation | **required** | Main `.nbt` template |
| `pieces` | ResourceLocation[] | `[]` | Extra pieces placed after main |
| `oncePerWorld` | boolean | `true` | Prevent duplicate generation |
| `dimension` | ResourceLocation | `minecraft:overworld` | Target dimension |
| `minDistanceFromSpawn` | int | `0` | Min placement distance |
| `maxDistanceFromSpawn` | int | `10000` | Max placement distance |
| `biomeTag` | string | none | Optional biome tag filter |
| `avoidWater` | boolean | `false` | Skip water surface positions |
| `avoidVillages` | boolean | `false` | Avoid village proximity |
| `terrainMode` | TerrainMode | `NONE` | Terrain adaptation strategy |
| `scanRadius` | int | `32` | Radius for terrain scan |
| `foundationDepth` | int | `4` | Depth for foundation/carving |
| `chunkSafe` | boolean | `true` | Only place in loaded chunks |
| `placeInPhases` | boolean | `false` | Spread across ticks |
| `maxBlocksPerTick` | int | `2000` | Block budget per tick |

---

## Once-per-world generation

When `oncePerWorld` is `true` Megalith writes a record to `MegalithSavedData`
(stored in the overworld's `data/` folder) when placement begins. If the world
is reloaded mid-placement the queue restores itself on next server start
(future enhancement — currently incomplete placements are re-queued).

Calling `MegalithStructures.queuePlacement()` with `oncePerWorld: true` returns
`false` silently if the structure already generated.

---

## Phased / chunk-safe placement

With `placeInPhases: true` and `chunkSafe: true`:

1. The structure is split into **pieces** (one `.nbt` per entry in `pieces[]`).
2. Each server tick the `MegalithPlacementManager` places **one piece**.
3. `maxBlocksPerTick` is a hint for future per-block decomposition (not yet
   enforced at the sub-piece level — see Known Limitations).

With `placeInPhases: false` all pieces are placed in the tick that triggered
placement. Use only for small structures.

---

## Terrain modes

| Mode | Effect |
|---|---|
| `NONE` | No terrain modification |
| `FLAT_PAD` | Clears above and fills below to a flat target Y |
| `SMART_FOUNDATION` | Places stone under any air gaps in the footprint |
| `BURIED` | Offsets placement Y downward (position handled by finder) |
| `CAVE_CARVE` | Carves a hollow underground box before placement |

Terrain adaptation runs **before** the first piece is queued so it takes effect
even on the same tick that placement is triggered.

---

## Debug commands

All commands require **op level 2**.

```
/megalith locate <id>
```
Shows the saved position of a generated structure, or reports it hasn't generated.

```
/megalith forceplace <id>
```
Finds a valid position and queues placement immediately (ignores `oncePerWorld`
tracking only if you reset first — still respects terrain checks).

```
/megalith reset <id>
```
Clears the saved generation record so the structure can be generated again.
Does **not** remove already-placed blocks from the world.

```
/megalith list
```
Lists all loaded structure definitions with their generation status.

```
/megalith validate <id>
```
Shows definition details and warns about missing templates.

---

## Java API usage

Add Megalith as a dependency in your `build.gradle`:

```groovy
dependencies {
    // local jar or Maven coordinate when published
    implementation fg.deobf("com.thunder:megalith:<version>")
}
```

Declare the dependency in your `mods.toml`:

```toml
[[dependencies.yourmodid]]
    modId = "megalith"
    mandatory = true
    versionRange = "[1,)"
    ordering = "AFTER"
    side = "SERVER"
```

### Check if a structure has generated

```java
import com.thunder.megalith.megastructure.api.MegalithStructures;

ResourceLocation id = ResourceLocation.parse("mymod:my_structure");
if (MegalithStructures.hasGenerated(serverLevel, id)) {
    // already in the world
}
```

### Queue automatic placement

```java
boolean queued = MegalithStructures.queuePlacement(serverLevel, id);
if (!queued) {
    LOGGER.warn("Could not queue placement — check that the definition is loaded.");
}
```

### Queue placement at a specific position

```java
BlockPos myPos = new BlockPos(1200, 64, -800);
MegalithStructures.queuePlacement(serverLevel, id, myPos);
```

### Get the generated position

```java
MegalithStructures.getGeneratedPos(serverLevel, id).ifPresent(pos ->
    LOGGER.info("Structure is at {}", pos)
);
```

### Reset and re-generate

```java
MegalithStructures.resetGenerated(serverLevel, id);
MegalithStructures.queuePlacement(serverLevel, id);
```

### Listen for completion (custom hook)

There is no event yet for "structure fully placed." To react to completion,
poll `MegalithSavedData.get(level).getAllGenerated()` for entries where
`isComplete() == true`, or add a NeoForge custom event in a future Megalith
version (see Future Expansion).

---

## Splitting huge builds into pieces

Minecraft's `.nbt` structure files have a practical size limit around
48×48×48 before they become slow to load and paste. For massive structures:

1. Build in a creative world.
2. Use a tool like **Structure Block** or **Litematica** to save each logical
   section as its own `.nbt` file.
3. List each `.nbt` as an entry in the `pieces[]` array.
4. Enable `placeInPhases: true` so each section is placed in a separate tick.

Pieces are placed at the **same origin** by default. Use relative offsets
(a future Megalith feature) to stitch sections together correctly.

---

## Performance notes

- `FLAT_PAD` and `CAVE_CARVE` iterate every block in the footprint
  synchronously. Keep `scanRadius` reasonable (≤128) or call from a phased
  task rather than a command.
- With `placeInPhases: true` and many structures queued simultaneously the
  server will serialize them one piece per tick. For large modpacks, stagger
  generation triggers.
- `maxBlocksPerTick` is **not yet enforced** at the sub-piece level — it is a
  hint for the future per-block decomposition path (see Known Limitations).

---

## Known limitations

1. **Per-block decomposition not yet implemented.** `maxBlocksPerTick` is
   recorded in the definition but `MegalithTemplatePlacer` calls
   `StructureTemplate.placeInWorld()` in one call per piece. Future work:
   decompose the template's block list and spread it across ticks.
2. **Biome tag filtering is a stub.** The `biomeTag` field is parsed but the
   actual tag lookup is a TODO in `MegalithPlacementFinder`.
3. **Village avoidance is a stub.** Needs a POI / structure scan via
   `ServerLevel.structureManager()`.
4. **Piece offsets are not implemented.** All pieces share the same origin.
   Add an offset field to the definition for multi-piece assembly.
5. **No generation event.** No NeoForge event fires when a Megalith structure
   finishes placing. Workaround: poll `MegalithSavedData`.
6. **Queue does not survive server restart.** In-progress placements are lost
   on restart. Future: serialise the queue to `SavedData`.

---

## Future expansion ideas

- Per-block tick decomposition respecting `maxBlocksPerTick`.
- Piece-level relative offsets and rotation/mirror support.
- Biome tag and village proximity checks.
- NeoForge events: `MegalithStructurePlacedEvent`, `MegalithStructureStartEvent`.
- Structure loot table injection post-placement.
- Integration with the vanilla `/locate` command.
- Multi-phase terrain preparation (async-like via queue).
- Web-based structure definition validator.