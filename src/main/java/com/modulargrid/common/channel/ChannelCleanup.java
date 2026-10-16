package com.modulargrid.common.channel;

import com.modulargrid.ModularGrid;
import com.modulargrid.common.tile.TileAccessPlug;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleans up references to channels that are gone.
 */
public final class ChannelCleanup {

    private ChannelCleanup() {
    }

    /**
     * Removes a channel address from every loaded access plug, in every loaded dimension.
     *
     * @return the amount of plugs that had a stale entry
     */
    public static int purgeFromAccessPlugs(String address) {
        if (address == null || address.isEmpty()) {
            return 0;
        }
        int purged = 0;
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (world == null) {
                continue;
            }
            // copy first: forgetChannel only marks the tile dirty, but do not walk a live list while doing so
            List<TileEntity> tiles = new ArrayList<>(world.loadedTileEntityList);
            for (TileEntity tile : tiles) {
                if (tile instanceof TileAccessPlug plug && !tile.isInvalid() && plug.forgetChannel(address)) {
                    purged++;
                }
            }
        }
        if (purged > 0) {
            ModularGrid.LOGGER.debug("Forgot the deleted channel {} on {} access plug(s)", address, purged);
        }
        return purged;
    }

}
