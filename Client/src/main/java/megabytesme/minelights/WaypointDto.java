package megabytesme.minelights;

public class WaypointDto {
    private double relativeYaw;
    private int color;
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

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }
}