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
    private String currentWorld;
    private String weather;
    private boolean isLightningFlashing;
    private int skyLightLevel;
    private float renderedBrightnessLevel;
    private boolean isOnFire;
    private boolean isPoisoned;
    private boolean isWithering;
    private boolean isTakingDamage;
    private List<WaypointDto> waypoints;
    private CompassState compassState = CompassState.NONE;
    private Double compassRelativeYaw;
    private Double compassDistance;
    private CompassType compassType = CompassType.NONE;
    private boolean isChatReceived;

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

    public boolean getIsLightningFlashing() {
        return isLightningFlashing;
    }

    public int getSkyLightLevel() {
        return skyLightLevel;
    }

    public float getRenderedBrightnessLevel() {
        return renderedBrightnessLevel;
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

    public boolean getIsChatReceived() {
        return isChatReceived;
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

    public void setIsLightningFlashing(boolean isLightningFlashing) {
        this.isLightningFlashing = isLightningFlashing;
    }

    public void setSkyLightLevel(int skyLightLevel) {
        this.skyLightLevel = skyLightLevel;
    }

    public void setRenderedBrightnessLevel(float renderedBrightnessLevel) {
        this.renderedBrightnessLevel = renderedBrightnessLevel;
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

    public CompassState getCompassState() {
        return compassState;
    }

    public void setCompassState(CompassState compassState) {
        this.compassState = compassState;
    }

    public Double getCompassRelativeYaw() {
        return compassRelativeYaw;
    }

    public void setCompassRelativeYaw(Double compassRelativeYaw) {
        this.compassRelativeYaw = compassRelativeYaw;
    }

    public Double getCompassDistance() {
        return compassDistance;
    }

    public void setCompassDistance(Double compassDistance) {
        this.compassDistance = compassDistance;
    }

    public CompassType getCompassType() {
        return compassType;
    }

    public void setCompassType(CompassType compassType) {
        this.compassType = compassType;
    }

    public String getCurrentWorld() {
        return currentWorld;
    }

    public void setCurrentWorld(String currentWorld) {
        this.currentWorld = currentWorld;
    }

    public void setIsChatReceived(boolean isChatReceived) {
        this.isChatReceived = isChatReceived;
    }
}