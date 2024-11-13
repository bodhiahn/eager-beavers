package bodhi.beaver.entity;

import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BeaverReservationSystem {
    private static final Map<BlockPos, UUID> reservedBlocks = new HashMap<>();
    private static final Map<UUID, BlockPos> beaverReservations = new HashMap<>();

    public static boolean reserveBlock(BlockPos pos, UUID beaverUUID) {
        // First, release any existing reservation for this beaver
        if (beaverReservations.containsKey(beaverUUID)) {
            releaseBlock(beaverReservations.get(beaverUUID));
        }

        if (reservedBlocks.containsKey(pos)) {
            return reservedBlocks.get(pos).equals(beaverUUID);
        }

        reservedBlocks.put(pos.toImmutable(), beaverUUID);
        beaverReservations.put(beaverUUID, pos.toImmutable());
        return true;
    }

    public static void releaseBlock(BlockPos pos) {
        UUID beaverUUID = reservedBlocks.get(pos);
        if (beaverUUID != null) {
            beaverReservations.remove(beaverUUID);
        }
        reservedBlocks.remove(pos);
    }

    public static boolean isBlockFree(BlockPos pos, UUID beaverUUID) {
        return !reservedBlocks.containsKey(pos) || reservedBlocks.get(pos).equals(beaverUUID);
    }

    public static void releaseBeaverReservation(UUID beaverUUID) {
        BlockPos pos = beaverReservations.get(beaverUUID);
        if (pos != null) {
            releaseBlock(pos);
        }
    }
}
