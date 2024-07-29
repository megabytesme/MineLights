package minelights.minelights;

public class PlayerDto {
    private String worldLevel;
    private float health;
    private int hunger;
    private String weather;
    private String currentBlock;
    private String currentBiome;

    public String getWorldLevel() {
        return worldLevel;
    }

    public void setWorldLevel(String worldLevel) {
        this.worldLevel = worldLevel;
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public int getHunger() {
        return hunger;
    }

    public void setHunger(int hunger) {
        this.hunger = hunger;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public String getCurrentBlock() {
        return currentBlock;
    }

    public void setCurrentBlock(String currentBlock) {
        this.currentBlock = currentBlock;
    }

    public String getCurrentBiome() {
        return currentBiome;
    }

    public void setCurrentBiome(String currentBiome) {
        this.currentBiome = currentBiome;
    }
}