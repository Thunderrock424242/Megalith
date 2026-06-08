package com.thunder.megalith.megastructure.terrain;

import com.mojang.serialization.Codec;

/**
 * Controls how Megalith adapts the terrain before placing a structure.
 */
public enum TerrainMode {

    /** Place the structure exactly as-is; no terrain modification. */
    NONE,

    /** Clear and level a flat rectangular pad at the target Y. */
    FLAT_PAD,

    /** Place foundation blocks under any air gaps in the footprint. */
    SMART_FOUNDATION,

    /** Lower the placement Y so the structure appears partially buried. */
    BURIED,

    /** Carve out a rectangular underground volume before placement. */
    CAVE_CARVE;

    public static final Codec<TerrainMode> CODEC = Codec.STRING.xmap(
            s -> TerrainMode.valueOf(s.toUpperCase()),
            TerrainMode::name
    );
}