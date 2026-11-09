package com.superniuma.mechanism;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

/** 机关放下时覆盖掉的原方块，以及放下的方块数据（用于回合恢复）。 */
public record PlacedBlock(Location location, BlockData previous, BlockData placed) {
}
