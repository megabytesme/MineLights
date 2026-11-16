package megabytesme.minelights.runtime;

import net.minecraft.core.BlockPos;

/** Manual policy test; run with the loader's compiled runtime classpath. */
public final class PlayerSpawnFallbackPolicyTest {
    private PlayerSpawnFallbackPolicyTest() { }

    public static void main(String[] args) {
        BlockPos worldSpawn = new BlockPos(10, 64, 10);
        BlockPos playerSpawn = new BlockPos(20, 64, 20);

        same(worldSpawn, PlayerDataCollector.resolvePlayerSpawnFallback(false, false, null, worldSpawn));
        same(worldSpawn, PlayerDataCollector.resolvePlayerSpawnFallback(true, false, null, worldSpawn));
        same(playerSpawn, PlayerDataCollector.resolvePlayerSpawnFallback(true, true, playerSpawn, worldSpawn));
        same(null, PlayerDataCollector.resolvePlayerSpawnFallback(true, true, null, worldSpawn));
        same(null, PlayerDataCollector.resolvePlayerSpawnFallback(false, false, null, null));
    }

    private static void same(Object expected, Object actual) {
        if (expected != actual && (expected == null || !expected.equals(actual))) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
