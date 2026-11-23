package dev.uwu.dimension;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.LevelChunk;

/** Tracks block replacements in newly generated sections, including direct writes by structure tools. */
public interface EscortTerrainSection {
    void uwu$trackEscortTerrain(@Nullable LevelChunk owner,int baseY);
}
