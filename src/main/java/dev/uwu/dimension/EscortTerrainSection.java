package dev.uwu.dimension;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.LevelChunk;

/** Links a certified section to its owning chunk while that certificate is valid. */
public interface EscortTerrainSection {
    void uwu$trackEscortTerrain(@Nullable LevelChunk owner,int baseY);
}
