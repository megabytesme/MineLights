package minelights.minelights;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;

public class PlayerDataProcessor {
    
        public void processPlayerData(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        World world = client.world;

        System.out.println(player.getHealth());
        String worldLevel = world.getRegistryKey().getValue().getPath();
        System.out.println("Current world level: " + worldLevel);

        if (world.getBlockState(player.getBlockPos()).getBlock() == net.minecraft.block.Blocks.NETHER_PORTAL) {
            System.out.println("Player is standing in a nether portal");
        }

        if (world.isRaining()) {
            System.out.println("Rain is falling in the world");
        }
    }
}
