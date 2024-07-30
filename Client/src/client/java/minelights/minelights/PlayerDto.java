package minelights.minelights;

public class PlayerDto {
    private boolean inGame;
    private float health;
    private int hunger;
    private String weather;
    private String currentBlock;
    private String currentBiome;

    public boolean getInGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
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