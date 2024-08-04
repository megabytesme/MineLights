package minelights.minelights;

public class PlayerDto {
    private boolean inGame;
    private float health;
    private int hunger;
    private float experience;
    private String weather;
    private String currentBlock;
    private String currentBiome;
    private boolean isOnFire;
    private boolean isPoisoned;
    private boolean isWithering;
    private boolean isTakingDamage;

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

    public boolean getIsOnFire() {
        return isOnFire;
    }

    public void setIsOnFire(boolean isOnFire) {
        this.isOnFire = isOnFire;
    }

    public boolean getIsPoisoned() {
        return isPoisoned;
    }

    public void setIsPoisoned(boolean isPoisoned) {
        this.isPoisoned = isPoisoned;
    }

    public boolean getIsWithering() {
        return isWithering;
    }

    public void setIsWithering(boolean isWithering) {
        this.isWithering = isWithering;
    }

    public boolean getIsTakingDamage() {
        return isTakingDamage;
    }

    public void setIsTakingDamage(boolean isTakingDamage) {
        this.isTakingDamage = isTakingDamage;
    }

    public float getExperience() {
        return experience;
    }

    public void setExperience(float experience) {
        this.experience = experience;
    }
}