package com.superniuma.mechanism;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

/** 机关放下时覆盖掉的原方块，用于回合结束后还原。 */
public record PlacedBlock(Location location, BlockData previous) {
}
