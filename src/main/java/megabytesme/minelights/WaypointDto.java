package megabytesme.minelights;

public class WaypointDto {
    public enum Pitch {
        UP,
        DOWN,
        LEVEL
    }

    private double relativeYaw;
    private int color;
    private Pitch pitch;
    private float distance;

    public double getRelativeYaw() {
        return relativeYaw;
    }

    public void setRelativeYaw(double relativeYaw) {
        this.relativeYaw = relativeYaw;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public Pitch getPitch() {
        return pitch;
    }

    public void setPitch(Pitch pitch) {
        this.pitch = pitch;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }
}
