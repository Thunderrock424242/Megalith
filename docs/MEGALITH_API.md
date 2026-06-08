# Megalith API — Developer Documentation

> **Mod ID:** `megalith`  
> **Minecraft:** 1.21.1 · NeoForge  
> **Package root:** `com.thunder.megalith`

---

## What is Megalith?

Megalith is a NeoForge library mod that helps modders safely generate very large
structures in Minecraft worlds. It hooks into the **vanilla worldgen pipeline**
so structures feel like they were always part of the world, while deferring the
actual block placement to a **server-tick queue** so the server never freezes
pasting thousands of blocks at once.

### Core guarantees

- Structures generate during **chunk decoration** — terrain is native, not overwritten
- **Once-per-world** enforcement via persistent `SavedData`
- **Phased block placement** spread across server ticks after the chunk is live
- **Terrain adaptation** applied during the decoration phase before blocks arrive
- Full **vanilla `/locate` support** — structures are real `Structure` subclasses
- **Datapack-driven** — no Java required to add a new structure

---

## How it differs from vanilla structures

| Feature | Vanilla Structures | Megalith |
|---|---|---|
| Generation timing | Chunk generation | Chunk decoration → tick queue |
| Size | Small–medium | Huge (hundreds of blocks) |
| Once per world | No built-in | Yes (`oncePerWorld: true`) |
| Terrain prep | None | FLAT_PAD / SMART_FOUNDATION / etc. |
| `/locate` support | Yes | Yes (native) |
| Phased placement | No | Yes (`placeInPhases: true`) |
| Config per modpack | Limited | Full JSON datapack |
| Debug tooling | None | `/megalith` commands |

---

## Adding a Megalith structure

Every Megalith structure requires **three JSON files** in your mod's datapack.
No Java is required.

---

### File 1 — Megalith definition

Controls structure behaviour, terrain adaptation, and placement settings.

```
data/<your_modid>/megalith_structure/<name>.json
```

#### Minimal example

```json
{
  "id": "mymod:my_tower",
  "template": "mymod:my_tower/main"
}
```

#### Full example

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

#### Field reference

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | ResourceLocation | **required** | Unique identifier for this structure |
| `template` | ResourceLocation | **required** | Main `.nbt` template file |
| `pieces` | ResourceLocation[] | `[]` | Additional pieces placed after the main template |
| `oncePerWorld` | boolean | `true` | Prevent this structure generating more than once per world save |
| `dimension` | ResourceLocation | `minecraft:overworld` | Target dimension |
| `minDistanceFromSpawn` | int | `0` | Minimum distance from world spawn in blocks |
| `maxDistanceFromSpawn` | int | `10000` | Maximum distance from world spawn in blocks |
| `biomeTag` | string | none | Optional biome tag filter (handled by vanilla via worldgen/structure JSON) |
| `avoidWater` | boolean | `false` | Reject positions over water during terrain scan |
| `avoidVillages` | boolean | `false` | Avoid village proximity *(stub — future implementation)* |
| `terrainMode` | TerrainMode | `NONE` | Terrain adaptation strategy (see Terrain Modes below) |
| `scanRadius` | int | `32` | Radius in blocks for terrain scanning and footprint |
| `foundationDepth` | int | `4` | Depth for foundation filling or cave carving |
| `chunkSafe` | boolean | `true` | Only place blocks in loaded chunks |
| `placeInPhases` | boolean | `false` | Spread piece placement across multiple server ticks |
| `maxBlocksPerTick` | int | `2000` | Block budget hint per tick *(piece-level granularity currently)* |

---

### File 2 — Worldgen structure entry

Registers the structure with vanilla's worldgen system and sets the biome filter.

```
data/<your_modid>/worldgen/structure/<name>.json
```

```json
{
  "type": "megalith:megalith_structure",
  "megalith_id": "mymod:particle_accelerator",
  "step": "surface_structures",
  "biomes": "#minecraft:is_overworld",
  "spawn_overrides": {}
}
```

| Field | Description |
|---|---|
| `type` | Must be `megalith:megalith_structure` |
| `megalith_id` | Must match the `id` field in your Megalith definition JSON |
| `step` | Vanilla decoration step. Use `surface_structures` for above-ground builds, `underground_structures` for buried ones |
| `biomes` | Vanilla biome tag or list. This is the canonical biome filter — prefer this over the `biomeTag` field in the Megalith definition |
| `spawn_overrides` | Vanilla mob spawn overrides during generation. Leave `{}` unless needed |

---

### File 3 — Structure set

Controls the placement grid: how frequently the structure appears and how far
from spawn it can generate.

```
data/<your_modid>/worldgen/structure_set/<name>.json
```

```json
{
  "structures": [
    { "structure": "mymod:particle_accelerator", "weight": 1 }
  ],
  "placement": {
    "type": "megalith:megalith_placement",
    "spacing": 80,
    "separation": 20,
    "salt": 198273645,
    "min_distance_from_spawn_chunks": 250,
    "max_distance_from_spawn_chunks": 750
  }
}
```

| Field | Description |
|---|---|
| `type` | Must be `megalith:megalith_placement` |
| `spacing` | Size of the placement grid in chunks. One candidate slot per grid cell |
| `separation` | Minimum chunk distance between two placements. Must be less than `spacing` |
| `salt` | Random seed salt. Use a unique number per structure to avoid grid alignment with other structures |
| `min_distance_from_spawn_chunks` | Minimum distance from spawn chunk (0,0) in chunks |
| `max_distance_from_spawn_chunks` | Maximum distance from spawn chunk in chunks |

> **Tip:** 250 chunks = 4000 blocks. 750 chunks = 12000 blocks. Divide your
> desired block distance by 16 to get chunk distance.

---

## How worldgen placement works

Understanding the two-phase model is important for debugging and for knowing
what each JSON field actually controls.

### Phase 1 — Chunk decoration (vanilla pipeline)

When the world generates a chunk that falls within the placement grid:

1. Vanilla calls `MegalithStructure#findGenerationPoint()`.
2. Megalith samples the terrain height variance across the footprint.
3. If the site is too steep, generation is rejected and the chunk is skipped.
4. Otherwise a `MegalithStructurePiece` is committed to the chunk's
   `StructureStart`. This is a lightweight marker — no blocks are placed yet.
5. During chunk decoration `MegalithStructurePiece#postProcess()` fires.
6. The once-per-world guard is checked here (this is the earliest point where
   a real `ServerLevel` reference is available).
7. Terrain adaptation runs (safe — the chunk is loaded but not yet sent to
   clients).
8. The structure is handed to `MegalithPlacementManager` and the position is
   recorded in `MegalithSavedData`.

### Phase 2 — Server tick queue

After Phase 1 commits the position:

1. `MegalithPlacementManager` holds a queue of `MegalithPlacementTask` objects,
   one per pending structure.
2. Each server tick the manager advances the front task by one piece.
3. A piece is one `.nbt` template file. The main template and each entry in
   `pieces[]` are separate steps.
4. When all pieces are placed the task is removed and `MegalithSavedData` marks
   the structure complete.

This means a structure with 5 pieces will finish across 5 server ticks minimum.
With `placeInPhases: false` all pieces are placed in the same tick as Phase 1 —
only use this for small structures.

---

## Once-per-world generation

When `oncePerWorld: true`:

- The check runs in `MegalithStructurePiece#postProcess()` the first time the
  chunk is decorated.
- The position is written to `MegalithSavedData` (stored in the overworld's
  saved data folder) before any blocks are placed.
- On subsequent world loads the `StructureStart` may re-fire `postProcess()`,
  but the saved data guard prevents double placement.
- Use `/megalith reset <id>` to clear the record and allow re-generation
  (does not remove already-placed blocks).

---

## Terrain modes

Terrain adaptation runs at the end of Phase 1 (chunk decoration), before any
blocks are queued. The chunk is loaded but not yet visible to clients, so
modifications are safe and clean.

| Mode | Effect |
|---|---|
| `NONE` | No terrain modification. Structure is placed as-is |
| `FLAT_PAD` | Clears everything above the target Y and fills gaps below it within the footprint |
| `SMART_FOUNDATION` | Places stone under any air gaps within the footprint down to `foundationDepth` |
| `BURIED` | Offsets the placement Y downward so the structure appears partially buried |
| `CAVE_CARVE` | Carves a hollow underground box before placement begins |

The footprint size is derived from `scanRadius`. Keep this value close to the
actual structure size — oversized scan radii make `FLAT_PAD` and `CAVE_CARVE`
expensive.

---

## Debug commands

All commands require **op level 2**.

```
/megalith locate <id>
```
Shows the saved position of a generated structure, or reports it has not
generated yet. Works alongside vanilla `/locate` since structures are real
`Structure` subclasses.

```
/megalith forceplace <id>
```
Bypasses the worldgen pipeline and queues placement immediately at a
runtime-found position. Useful for testing without waiting for natural
chunk generation. Respects `oncePerWorld` — use `/megalith reset` first if
the structure already generated.

```
/megalith reset <id>
```
Clears the saved generation record so the structure can generate again.
Does **not** remove already-placed blocks from the world.

```
/megalith list
```
Lists all loaded Megalith structure definitions and their current generation
status.

```
/megalith validate <id>
```
Prints definition details including template path, piece count, terrain mode,
and dimension. Useful for confirming a definition loaded correctly after
`/reload`.

---

## Java API

Add Megalith as a dependency in `build.gradle`:

```groovy
dependencies {
    implementation fg.deobf("com.thunder:megalith:<version>")
}
```

Declare the dependency in `mods.toml`:

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
import net.minecraft.resources.ResourceLocation;

ResourceLocation id = ResourceLocation.parse("mymod:particle_accelerator");

if (MegalithStructures.hasGenerated(serverLevel, id)) {
        // already in the world
        }
```

### Get the generated position

```java
MegalithStructures.getGeneratedPos(serverLevel, id).ifPresent(pos ->
        LOGGER.info("Structure is at {}", pos)
);
```

### Force-queue placement at an automatic position

Megalith will find a valid position respecting the definition's distance and
terrain rules. Respects `oncePerWorld`.

```java
boolean queued = MegalithStructures.queuePlacement(serverLevel, id);
if (!queued) {
        LOGGER.warn("Could not queue — definition missing, already generated, or no valid position found.");
}
```

### Force-queue placement at a specific position

```java
BlockPos myPos = new BlockPos(1200, 64, -800);
boolean queued = MegalithStructures.queuePlacement(serverLevel, id, myPos);
```

### Manually mark as generated

Useful if your own code places the structure and you want Megalith to track it.

```java
MegalithStructures.markGenerated(serverLevel, id, pos);
```

### Reset and re-generate

```java
MegalithStructures.resetGenerated(serverLevel, id);
MegalithStructures.queuePlacement(serverLevel, id); // or let worldgen trigger it
```

### Get a definition

```java
MegalithStructures.getDefinition(id).ifPresent(def -> {
    LOGGER.info("Terrain mode: {}", def.terrainMode());
    LOGGER.info("Pieces: {}", def.pieces().size());
});
```

---

## Splitting huge builds into pieces

Minecraft `.nbt` structure files become slow to load and paste beyond roughly
48×48×48 blocks. For massive structures:

1. Build in a creative world.
2. Use **Structure Blocks** or **Litematica** to save each logical section as
   its own `.nbt` file.
3. Name files consistently: `mymod:my_structure/core`,
   `mymod:my_structure/wing_east`, etc.
4. Set `template` to the central or first piece.
5. Add remaining pieces to the `pieces[]` array.
6. Enable `placeInPhases: true` — each piece is placed in a separate server
   tick, keeping the server responsive.

> **Note:** All pieces currently share the same origin point. Per-piece relative
> offsets are a planned feature. Until then, design pieces so they can be placed
> from the same bottom-northwest corner, or offset them manually in the `.nbt`
> file itself.

---

## Performance notes

- **`FLAT_PAD` and `CAVE_CARVE`** iterate every block in the footprint
  synchronously during chunk decoration. Keep `scanRadius` ≤ 64 for these
  modes or the decoration phase will stall.
- **`SMART_FOUNDATION`** is cheaper — it only fills air columns, not solid ones.
- **`placeInPhases: true`** is strongly recommended for any structure with more
  than one piece or more than ~2000 blocks total.
- **`maxBlocksPerTick`** currently operates at piece granularity, not per-block.
  A single large `.nbt` piece is still placed in one call. Sub-piece block
  batching is planned.
- Queued structures are processed one piece per tick in FIFO order. If many
  structures trigger simultaneously they will serialize naturally — no server
  impact beyond the per-tick piece placement cost.

---

## Known limitations

1. **Per-block decomposition not yet implemented.** `maxBlocksPerTick` is stored
   and respected at the piece level but `MegalithTemplatePlacer` still calls
   `StructureTemplate.placeInWorld()` in one shot per piece. Future work will
   decompose the block list across ticks.

2. **Piece-level offsets not implemented.** All pieces in `pieces[]` share the
   same origin. The fix is to add an offset field to each piece entry in the
   definition JSON.

3. **Village avoidance is a stub.** The `avoidVillages` field is parsed but the
   POI/structure proximity scan is not yet implemented.

4. **Queue does not survive server restart.** If the server stops mid-placement
   the queue is lost. The position is saved in `MegalithSavedData` so the
   structure won't duplicate, but the remaining pieces won't be placed. Fix:
   serialise the queue to `SavedData` on server stop.

5. **No completion event.** There is no NeoForge event for "structure fully
   placed." Workaround: poll `MegalithSavedData.get(level).getAllGenerated()`
   for entries where `isComplete() == true`.

6. **`biomeTag` in Megalith definition is advisory.** The authoritative biome
   filter is the `"biomes"` field in `worldgen/structure/<name>.json`, which
   vanilla enforces correctly. The `biomeTag` field in the Megalith definition
   JSON is retained for documentation and future runtime checks only.

---

## Future expansion ideas

- Sub-piece per-block tick decomposition respecting `maxBlocksPerTick`
- Per-piece relative offsets and rotation/mirror support in definition JSON
- Village and POI proximity avoidance
- NeoForge events: `MegalithStructurePlacedEvent`, `MegalithStructureStartEvent`
- Loot table injection into containers post-placement
- Queue serialisation for restart-safe phased placement
- Multi-structure placement priority/ordering system
- Structure preview command showing bounding box in-world