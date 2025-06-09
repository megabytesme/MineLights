package megabytesme.minelights.config;

import java.util.ArrayList;
import java.util.List;

public class MineLightsConfig {
    public transient boolean restartProxy = false;
    public transient boolean restartProxyAsAdmin = false;
    public transient boolean clearDisabledDevices = false;

    public boolean enableMod = true;
    public boolean enableOpenRgb = true;
    public boolean enableIcueProxy = true;
    public boolean enableMysticLightProxy = true;
    public boolean enableAuraSdk = true;
    public List<String> disabledDevices = new ArrayList<>();

    public boolean enableHealthBar = true;
    public boolean enableHungerBar = true;
    public boolean enableExperienceBar = true;
    public boolean enableLowHealthWarning = true;
    public boolean enableBiomeEffects = true;
    public boolean enableWeatherEffects = true;
    public boolean enableOnFireEffect = true;
    public boolean enableInWaterEffect = true;
    public boolean enablePortalEffects = true;
    public boolean highlightMovementKeys = true;
}