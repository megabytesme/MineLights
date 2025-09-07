package megabytesme.minelights;

import java.util.List;

public class PlayerDto {
    private boolean inGame;
    private float health;
    private int hunger;
    private float saturation;
    private int air;
    private float experience;
    private String currentBlock;
    private String currentBiome;
    private String weather;
    private boolean isOnFire;
    private boolean isPoisoned;
    private boolean isWithering;
    private boolean isTakingDamage;
    private List<WaypointDto> waypoints;

    public boolean getInGame() {
        return inGame;
    }

    public float getHealth() {
        return health;
    }

    public int getHunger() {
        return hunger;
    }

    public float getSaturation() {
        return saturation;
    }

    public int getAir() {
        return air;
    }

    public float getExperience() {
        return experience;
    }

    public String getCurrentBlock() {
        return currentBlock;
    }

    public String getCurrentBiome() {
        return currentBiome;
    }

    public String getWeather() {
        return weather;
    }

    public boolean getIsOnFire() {
        return isOnFire;
    }

    public boolean getIsPoisoned() {
        return isPoisoned;
    }

    public boolean getIsWithering() {
        return isWithering;
    }

    public boolean getIsTakingDamage() {
        return isTakingDamage;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public void setHunger(int hunger) {
        this.hunger = hunger;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    public void setAir(int air) {
        this.air = air;
    }

    public void setExperience(float experience) {
        this.experience = experience;
    }

    public void setCurrentBlock(String currentBlock) {
        this.currentBlock = currentBlock;
    }

    public void setCurrentBiome(String currentBiome) {
        this.currentBiome = currentBiome;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public void setIsOnFire(boolean isOnFire) {
        this.isOnFire = isOnFire;
    }

    public void setIsPoisoned(boolean isPoisoned) {
        this.isPoisoned = isPoisoned;
    }

    public void setIsWithering(boolean isWithering) {
        this.isWithering = isWithering;
    }

    public void setIsTakingDamage(boolean isTakingDamage) {
        this.isTakingDamage = isTakingDamage;
    }

    public List<WaypointDto> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<WaypointDto> waypoints) {
        this.waypoints = waypoints;
    }
}