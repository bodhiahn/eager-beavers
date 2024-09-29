package bodhi.beaver.entity;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BeaverReservationSystem {
    private static final Map<BlockPos, UUID> reservedBlocks = new HashMap<>();

    public static boolean reserveBlock(BlockPos pos, UUID beaverUUID) {
        if (reservedBlocks.containsKey(pos)) {
            // If the block is already reserved by this beaver, return true
            return reservedBlocks.get(pos).equals(beaverUUID);
        }
        reservedBlocks.put(pos.toImmutable(), beaverUUID);
        return true;
    }

    public static void releaseBlock(BlockPos pos) {
        reservedBlocks.remove(pos);
    }

    public static boolean isBlockFree(BlockPos pos, UUID beaverUUID) {
        // Block is free if it's not reserved or reserved by this beaver
        return !reservedBlocks.containsKey(pos) || reservedBlocks.get(pos).equals(beaverUUID);
    }
}
