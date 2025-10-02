package megabytesme.minelights;

//? if >=1.21.8 {
import net.minecraft.world.waypoint.TrackedWaypoint.Pitch;
//?}

public class WaypointDto {
    private double relativeYaw;
    private int color;
    //? if >=1.21.8 {
    private Pitch pitch;
    //?}    
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

    //? if >=1.21.8 {
    public Pitch getPitch() {
        return pitch;
    }

    public void setPitch(Pitch pitch) {
        this.pitch = pitch;
    }
    //?}

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }
}