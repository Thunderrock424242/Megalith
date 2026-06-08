package com.thunder.megalith.megastructure.worldgen;

import com.thunder.megalith.Megalith;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers all worldgen types needed by Megalith:
 * <ul>
 *   <li>{@link StructureType} — so the pipeline can deserialise {@link MegalithStructure}</li>
 *   <li>{@link StructurePieceType} — so chunks can deserialise {@link MegalithStructurePiece}</li>
 *   <li>{@link StructurePlacementType} — so structure sets can use {@link MegalithStructurePlacement}</li>
 * </ul>
 */
public class MegalithWorldgenRegistry {

    // ── Structure type ────────────────────────────────────────────────────────

    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Megalith.MOD_ID);

    /** megalith:megalith_structure */
    public static final DeferredHolder<StructureType<?>, StructureType<MegalithStructure>>
            MEGALITH_STRUCTURE_TYPE = STRUCTURE_TYPES.register(
            "megalith_structure", () -> () -> MegalithStructure.CODEC);

    // ── Structure piece type ──────────────────────────────────────────────────

    private static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Megalith.MOD_ID);

    /** megalith:megalith_piece */
    public static final DeferredHolder<StructurePieceType, StructurePieceType>
            MEGALITH_PIECE_TYPE = PIECE_TYPES.register(
            "megalith_piece",
            () -> {
                StructurePieceType type = MegalithStructurePiece::new;
                // Back-fill the static reference on the piece class so getType() works
                MegalithStructurePiece.TYPE = type;
                return type;
            });

    // ── Structure placement type ──────────────────────────────────────────────

    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT_TYPE, Megalith.MOD_ID);

    /** megalith:megalith_placement */
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<MegalithStructurePlacement>>
            MEGALITH_PLACEMENT_TYPE = PLACEMENT_TYPES.register(
            "megalith_placement",
            () -> () -> MegalithStructurePlacement.CODEC);

    // ── Boot ──────────────────────────────────────────────────────────────────

    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
        PLACEMENT_TYPES.register(modBus);
    }
}