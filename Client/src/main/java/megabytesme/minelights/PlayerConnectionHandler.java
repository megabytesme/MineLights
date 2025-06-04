package megabytesme.minelights;

import com.google.gson.Gson;

public class PlayerConnectionHandler {
    public void onDisconnect() {
        PlayerDto playerDto = new PlayerDto();
        playerDto.setInGame(false);

        // initialize the player data to default values
        playerDto.setHealth(0);
        playerDto.setHunger(0);
        playerDto.setWeather("Clear");
        playerDto.setCurrentBlock("minecraft:air");
        playerDto.setCurrentBiome("minecraft:plains");

        // Serialize playerDto to JSON
        Gson gson = new Gson();
        String playerDtoJson = gson.toJson(playerDto);

        // Send the JSON data via UDP
        UDPClient.sendPlayerData(playerDtoJson);
    }
}
