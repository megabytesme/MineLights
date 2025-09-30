package megabytesme.minelights.effects;

import megabytesme.minelights.CompassState;
import megabytesme.minelights.CompassType;
import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.PlayerDto;
import net.minecraft.client.MinecraftClient;

//? if >=1.17 {
/*import net.minecraft.client.option.GameOptions;
*///?} else {
/* import net.minecraft.client.options.GameOptions;
*///?}
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EffectPainter {
    private final Random random = new Random();
    private final List<Integer> allLedIds;
    private final Map<String, Integer> namedKeyMap;

    private boolean rainPhase = false;
    private long lastRainStep = 0;
    private boolean isFlashing = false;
    private long flashStartTime = 0;
    private long lastFireCrackleUpdate = 0;
    private final List<Integer> cracklingKeys = new ArrayList<>();
    private long lastPortalTwinkleUpdate = 0;
    private final List<Integer> twinklingKeys = new ArrayList<>();

    private RGBColorDto currentSmoothBiomeColor = new RGBColorDto(0, 0, 0);
    private RGBColorDto transitionStartColor = new RGBColorDto(0, 0, 0);
    private RGBColorDto targetBiomeColor = new RGBColorDto(0, 0, 0);
    private String lastKnownBiome = "";
    private long transitionStartTime = 0;
    private static final int TRANSITION_DURATION_MS = 750;

    private long lastInWaterTime = 0;
    private static final int AIR_BAR_VISIBLE_DURATION_MS = 2500;

    private boolean wasTakingDamageLastFrame = false;
    private boolean isDamageFlashActive = false;
    private long damageFlashStartTime = 0;
    private boolean wasLowHealthLastFrame = false;

    private float heartbeatBrightness = 0.1f;
    private boolean isHeartBeatingUp = true;
    private long lastHeartbeatStep = 0;

    private long lastCompassSpinTime = 0;
    private int compassSpinIndex = 0;
    private static long lastSheenTime = 0;
    private static int sheenIndex = 0;

    private static RGBColorDto blend(RGBColorDto base, RGBColorDto overlay, double overlayOpacity) {
        if (base == null) return overlay;
        if (overlay == null) return base;

        double baseOpacity = 1.0 - overlayOpacity;
        int r = (int) (base.r * baseOpacity + overlay.r * overlayOpacity);
        int g = (int) (base.g * baseOpacity + overlay.g * overlayOpacity);
        int b = (int) (base.b * baseOpacity + overlay.b * overlayOpacity);

        return new RGBColorDto(r, g, b);
    }

    public EffectPainter(List<Integer> allLedIds, Map<String, Integer> namedKeyMap) {
        this.allLedIds = allLedIds;
        this.namedKeyMap = namedKeyMap;
    }

    private Integer getMappedId(String keyName) {
        return namedKeyMap.get(keyName);
    }

    public FrameStateDto paint(PlayerDto player) {
        FrameStateDto state = new FrameStateDto();

        if (!MineLightsClient.CONFIG.enableMod || allLedIds.isEmpty()) {
            return state;
        }

        if (!player.getInGame()) {
            for (Integer ledId : allLedIds) {
                state.keys.put(ledId, new RGBColorDto(255, 0, 0));
            }
            return state;
        }

        for (Integer ledId : allLedIds) {
            state.keys.put(ledId, new RGBColorDto(255, 0, 0));
        }

        paintEnvironmentalBase(state, player);
        if (MineLightsClient.CONFIG.enableExperienceBar)
            paintExperienceBar(state, player);
        paintPlayerBars(state, player);
        if (MineLightsClient.CONFIG.enableSaturationBar)
            paintSaturationAndAirBar(state, player);
        if (MineLightsClient.CONFIG.enableCompassEffect)
            paintCompass(state, player);
        if (MineLightsClient.CONFIG.enableLowHealthWarning)
            paintHealthEffects(state, player);
        paintPlayerEffects(state, player);

        return state;
    }

    private void paintEnvironmentalBase(FrameStateDto state, PlayerDto player) {
        long now = System.currentTimeMillis();
        RGBColorDto baseColor = new RGBColorDto(0, 0, 0);

        if (MineLightsClient.CONFIG.enableBiomeEffects) {
            if (!player.getCurrentBiome().equals(lastKnownBiome) && !player.getCurrentBiome().isEmpty()) {
                transitionStartColor = currentSmoothBiomeColor;
                targetBiomeColor = BiomeData.getBiomeColor(player.getCurrentBiome());
                lastKnownBiome = player.getCurrentBiome();
                transitionStartTime = now;
            }
            long elapsedMs = now - transitionStartTime;
            float t = Math.min(1.0f, (float) elapsedMs / TRANSITION_DURATION_MS);
            currentSmoothBiomeColor = lerpColor(transitionStartColor, targetBiomeColor, t);
            baseColor = currentSmoothBiomeColor;
        }

        if (MineLightsClient.CONFIG.enableOnFireEffect && player.getIsOnFire()) {
            baseColor = new RGBColorDto(255, 69, 0);
        } else if (MineLightsClient.CONFIG.enablePortalEffects
                && player.getCurrentBlock().equals("block.minecraft.nether_portal")) {
            baseColor = new RGBColorDto(128, 0, 128);
        } else if (MineLightsClient.CONFIG.enablePortalEffects
                && player.getCurrentBlock().equals("block.minecraft.end_portal")) {
            baseColor = new RGBColorDto(0, 0, 50);
        }

        for (Integer ledId : allLedIds) {
            state.keys.put(ledId, baseColor);
        }

        paintSpecialWorldEffects(state, player, now);
    }

    private void paintSpecialWorldEffects(FrameStateDto state, PlayerDto player, long now) {
        if (MineLightsClient.CONFIG.enableWeatherEffects && player.getWeather().equals("Thunderstorm") && !isFlashing
                && random.nextInt(200) < 1) {
            isFlashing = true;
            flashStartTime = now;
        }
        if (isFlashing) {
            if (now - flashStartTime < 150) {
                for (Integer ledId : allLedIds) {
                    state.keys.put(ledId, new RGBColorDto(255, 255, 255));
                }
                return;
            } else {
                isFlashing = false;
            }
        }

        if (MineLightsClient.CONFIG.enableOnFireEffect && player.getIsOnFire()) {
            if (now - lastFireCrackleUpdate > 100) {
                updateRandomKeys(cracklingKeys, 0.2f);
                lastFireCrackleUpdate = now;
            }
            for (Integer keyId : cracklingKeys) {
                state.keys.put(keyId, new RGBColorDto(200, 0, 0));
            }
        } else if (MineLightsClient.CONFIG.enablePortalEffects
                && player.getCurrentBlock().equals("block.minecraft.nether_portal")) {
            if (now - lastPortalTwinkleUpdate > 500) {
                updateRandomKeys(twinklingKeys, 0.2f);
                lastPortalTwinkleUpdate = now;
            }
            for (Integer keyId : twinklingKeys) {
                state.keys.put(keyId, new RGBColorDto(50, 0, 100));
            }
        } else if (MineLightsClient.CONFIG.enablePortalEffects
                && player.getCurrentBlock().equals("block.minecraft.end_portal")) {
            if (now - lastPortalTwinkleUpdate > 500) {
                updateRandomKeys(twinklingKeys, 0.2f);
                lastPortalTwinkleUpdate = now;
            }
            for (Integer keyId : twinklingKeys) {
                state.keys.put(keyId, new RGBColorDto(50, 50, 50));
            }
        } else if (MineLightsClient.CONFIG.enableWeatherEffects
                && (player.getWeather().equals("Rain") || player.getWeather().equals("Thunderstorm"))
                && BiomeData.isBiomeRainy(player.getCurrentBiome())) {
            if (now - lastRainStep > 500) {
                rainPhase = !rainPhase;
                lastRainStep = now;
            }
            RGBColorDto rainColor = BiomeData.getBiomeRainColor(player.getCurrentBiome());
            for (int i = 0; i < allLedIds.size(); i++) {
                if (i % 2 == (rainPhase ? 0 : 1)) {
                    state.keys.put(allLedIds.get(i), rainColor);
                }
            }
        }
    }

    private void paintExperienceBar(FrameStateDto state, PlayerDto player) {
        List<String> logicalKeys = KeyMap.getExperienceBar();
        int ledsToLight = (int) (player.getExperience() * logicalKeys.size());
        for (int i = 0; i < logicalKeys.size(); i++) {
            Integer ledId = getMappedId(logicalKeys.get(i));
            if (ledId != null) {
                if (i < ledsToLight) {
                    state.keys.put(ledId, new RGBColorDto(0, 255, 0));
                } else {
                    state.keys.put(ledId, new RGBColorDto(10, 30, 10));
                }
            }
        }
    }

    private void paintPlayerBars(FrameStateDto state, PlayerDto player) {
        if (player.getIsTakingDamage() && !wasTakingDamageLastFrame) {
            isDamageFlashActive = true;
            damageFlashStartTime = System.currentTimeMillis();
        }
        wasTakingDamageLastFrame = player.getIsTakingDamage();
        if (isDamageFlashActive && (System.currentTimeMillis() - damageFlashStartTime) >= 120) {
            isDamageFlashActive = false;
        }

        if (MineLightsClient.CONFIG.enableHealthBar) {
            RGBColorDto healthDim = new RGBColorDto(30, 0, 0);
            RGBColorDto healthFull = new RGBColorDto(255, 0, 0);
            List<String> healthKeys = KeyMap.getHealthBar();
            for (int i = 0; i < healthKeys.size(); i++) {
                Integer ledId = getMappedId(healthKeys.get(i));
                if (ledId == null)
                    continue;

                RGBColorDto finalColor;
                if (isDamageFlashActive) {
                    finalColor = new RGBColorDto(255, 255, 255);
                } else if (player.getIsWithering()) {
                    finalColor = new RGBColorDto(43, 43, 43);
                } else if (player.getIsPoisoned()) {
                    finalColor = new RGBColorDto(148, 120, 24);
                } else {
                    float t = (player.getHealth() - (i * 5.0f)) / 5.0f;
                    finalColor = lerpColor(healthDim, healthFull, t);
                }
                state.keys.put(ledId, finalColor);
            }
        }

        if (MineLightsClient.CONFIG.enableHungerBar) {
            RGBColorDto hungerDim = new RGBColorDto(30, 15, 0);
            RGBColorDto hungerFull = new RGBColorDto(255, 165, 0);
            List<String> hungerKeys = KeyMap.getHungerBar();
            for (int i = 0; i < hungerKeys.size(); i++) {
                Integer ledId = getMappedId(hungerKeys.get(i));
                if (ledId == null)
                    continue;

                float t = (player.getHunger() - (i * 5.0f)) / 5.0f;
                state.keys.put(ledId, lerpColor(hungerDim, hungerFull, t));
            }
        }
    }

    private void paintSaturationAndAirBar(FrameStateDto state, PlayerDto player) {
        List<String> barKeys = KeyMap.getSaturationBar();
        boolean isInWaterNow = player.getCurrentBlock().equals("block.minecraft.water");

        if (isInWaterNow) {
            lastInWaterTime = System.currentTimeMillis();
        }

        boolean showAirBar = isInWaterNow
                || (System.currentTimeMillis() - lastInWaterTime < AIR_BAR_VISIBLE_DURATION_MS);

        float value;
        float maxValue;
        RGBColorDto fullColor;
        RGBColorDto dimColor;

        if (showAirBar) {
            value = player.getAir();
            maxValue = 300f;
            fullColor = new RGBColorDto(173, 216, 230);
            dimColor = new RGBColorDto(0, 0, 50);
        } else {
            value = player.getSaturation();
            maxValue = 20f;
            fullColor = new RGBColorDto(200, 255, 0);
            dimColor = new RGBColorDto(40, 50, 0);
        }

        float valuePerKey = maxValue / barKeys.size();

        for (int i = 0; i < barKeys.size(); i++) {
            Integer ledId = getMappedId(barKeys.get(i));
            if (ledId == null)
                continue;

            float t = (value - (i * valuePerKey)) / valuePerKey;
            state.keys.put(ledId, lerpColor(dimColor, fullColor, t));
        }
    }

    private void paintHealthEffects(FrameStateDto state, PlayerDto player) {
        boolean isLowHealthNow = player.getHealth() < 10;
        if (isLowHealthNow && !wasLowHealthLastFrame) {
            heartbeatBrightness = 0.1f;
            isHeartBeatingUp = true;
            lastHeartbeatStep = System.currentTimeMillis();
        }
        wasLowHealthLastFrame = isLowHealthNow;
        if (!isLowHealthNow)
            return;

        long now = System.currentTimeMillis();
        if (now - lastHeartbeatStep > 40) {
            if (isHeartBeatingUp) {
                heartbeatBrightness += 0.1f;
                if (heartbeatBrightness >= 1.0f) {
                    heartbeatBrightness = 1.0f;
                    isHeartBeatingUp = false;
                }
            } else {
                heartbeatBrightness -= 0.1f;
                if (heartbeatBrightness <= 0.1f) {
                    heartbeatBrightness = 0.1f;
                    isHeartBeatingUp = true;
                }
            }
            lastHeartbeatStep = now;
        }

        int r = (int) (255 * heartbeatBrightness);
        for (String keyName : KeyMap.getHeartbeatRed()) {
            Integer ledId = getMappedId(keyName);
            if (ledId != null)
                state.keys.put(ledId, new RGBColorDto(r, 0, 0));
        }
        for (String keyName : KeyMap.getHeartbeatWhite()) {
            Integer ledId = getMappedId(keyName);
            if (ledId != null)
                state.keys.put(ledId, new RGBColorDto(r, r, r));
        }
    }

    private void paintCompass(FrameStateDto state, PlayerDto player) {
        if (player.getCompassType() == CompassType.NONE) {
            return;
        }

        RGBColorDto compassColor;
        RGBColorDto backgroundColor;

        switch (player.getCompassType()) {
            case RECOVERY:
                compassColor = new RGBColorDto(0, 191, 255);
                backgroundColor = new RGBColorDto(0, 20, 35);
                break;
            case LODESTONE:
                compassColor = new RGBColorDto(238, 130, 238);
                backgroundColor = new RGBColorDto(25, 0, 25);
                break;
            default:
                compassColor = new RGBColorDto(255, 0, 0);
                backgroundColor = new RGBColorDto(35, 0, 0);
                break;
        }

        for (String keyName : KeyMap.getNumpadDirectional()) {
            Integer ledId = getMappedId(keyName);
            if (ledId != null) {
                state.keys.put(ledId, backgroundColor);
            }
        }

        Integer centerLedId = getMappedId(KeyMap.getNumpadCenter());
        if (centerLedId != null) {
            state.keys.put(centerLedId, compassColor);
        }

        if (player.getCompassState() == CompassState.SPINNING) {
            long now = System.currentTimeMillis();
            if (now - lastCompassSpinTime > 75) {
                compassSpinIndex = (compassSpinIndex + 1) % KeyMap.getNumpadDirectional().size();
                lastCompassSpinTime = now;
            }
            String keyToLight = KeyMap.getNumpadDirectional().get(compassSpinIndex);
            Integer ledId = getMappedId(keyToLight);
            if (ledId != null) {
                state.keys.put(ledId, compassColor);
            }
        } else if (player.getCompassState() == CompassState.POINTING) {
            if (player.getCompassDistance() != null && player.getCompassDistance() < 8) {
                if (centerLedId != null) {
                    long now = System.currentTimeMillis();
                    double pulseWave = (Math.sin(now / (2000.0 / (2.0 * Math.PI))) + 1.0) / 2.0;
                    float brightness = (float) (0.6 + pulseWave * 0.4);

                    RGBColorDto pulsedColor = new RGBColorDto(
                            (int) (compassColor.r * brightness),
                            (int) (compassColor.g * brightness),
                            (int) (compassColor.b * brightness));
                    state.keys.put(centerLedId, pulsedColor);
                }
            } else {
                Double relativeYaw = player.getCompassRelativeYaw();
                if (relativeYaw == null)
                    return;

                String keyToLight;
                if (relativeYaw >= -22.5 && relativeYaw < 22.5)
                    keyToLight = "NUMPAD8"; // N
                else if (relativeYaw >= 22.5 && relativeYaw < 67.5)
                    keyToLight = "NUMPAD9"; // NE
                else if (relativeYaw >= 67.5 && relativeYaw < 112.5)
                    keyToLight = "NUMPAD6"; // E
                else if (relativeYaw >= 112.5 && relativeYaw < 157.5)
                    keyToLight = "NUMPAD3"; // SE
                else if (relativeYaw >= 157.5 || relativeYaw < -157.5)
                    keyToLight = "NUMPAD2"; // S
                else if (relativeYaw >= -157.5 && relativeYaw < -112.5)
                    keyToLight = "NUMPAD1"; // SW
                else if (relativeYaw >= -112.5 && relativeYaw < -67.5)
                    keyToLight = "NUMPAD4"; // W
                else
                    keyToLight = "NUMPAD7"; // NW

                Integer ledId = getMappedId(keyToLight);
                if (ledId != null) {
                    state.keys.put(ledId, compassColor);
                }
            }
        }

        if (player.getCompassType() == CompassType.LODESTONE) {
            long now = System.currentTimeMillis();
            if (now - lastSheenTime > 60) {
                sheenIndex = (sheenIndex + 1) % KeyMap.getNumpadDirectional().size();
                lastSheenTime = now;
            }

            List<String> directionalKeys = KeyMap.getNumpadDirectional();
            int keyCount = directionalKeys.size();
            
            RGBColorDto sheenTintColor = new RGBColorDto(120, 0, 255);
            double[] opacities = {0.6, 0.4, 0.2};

            for (int i = 0; i < opacities.length; i++) {
                int keyIndex = (sheenIndex - i + keyCount) % keyCount;
                String keyName = directionalKeys.get(keyIndex);
                Integer ledId = getMappedId(keyName);

                if (ledId != null) {
                    RGBColorDto baseColor = state.keys.get(ledId);
                    RGBColorDto finalColor = blend(baseColor, sheenTintColor, opacities[i]);
                    state.keys.put(ledId, finalColor);
                }
            }
        }
    }

    private void paintPlayerEffects(FrameStateDto state, PlayerDto player) {
        RGBColorDto keyColor = null;

        if (MineLightsClient.CONFIG.enableInWaterEffect && player.getCurrentBlock().equals("block.minecraft.water")) {
            keyColor = new RGBColorDto(0, 100, 255);
        } else if (MineLightsClient.CONFIG.enableOnFireEffect
                && (player.getCurrentBlock().equals("block.minecraft.lava")
                        || player.getCurrentBlock().equals("block.minecraft.fire"))) {
            keyColor = new RGBColorDto(255, 0, 0);
        } else if (MineLightsClient.CONFIG.highlightMovementKeys) {
            keyColor = new RGBColorDto(255, 255, 255);
        }

        if (keyColor != null) {
            for (String keyName : getMovementKeyNames()) {
                Integer ledId = getMappedId(keyName);
                if (ledId != null)
                    state.keys.put(ledId, keyColor);
            }
        }
    }

    private List<String> getMovementKeyNames() {
        List<String> friendlyNames = new ArrayList<>();
        List<String> keybindsToFetch = new ArrayList<>();
        //? if >=1.16 {
        /* 
         GameOptions options = MinecraftClient.getInstance().options;
         keybindsToFetch = Arrays.asList(
         options.keyForward.getBoundKeyTranslationKey(),
         options.keyBack.getBoundKeyTranslationKey(),
         options.keyLeft.getBoundKeyTranslationKey(),
         options.keyRight.getBoundKeyTranslationKey(),
         options.keyJump.getBoundKeyTranslationKey(),
         options.keySneak.getBoundKeyTranslationKey(),
         options.keySprint.getBoundKeyTranslationKey());
        *///?} else {
        /*
         GameOptions options = MinecraftClient.getInstance().options; 
         keybindsToFetch = Arrays.asList(
         options.keyForward.getDefaultKeyCode().toString(),
         options.keyBack.getDefaultKeyCode().toString(),
         options.keyLeft.getDefaultKeyCode().toString(),
         options.keyRight.getDefaultKeyCode().toString(),
         options.keyJump.getDefaultKeyCode().toString(),
         options.keySneak.getDefaultKeyCode().toString(),
         options.keySprint.getDefaultKeyCode().toString());
        *///?}

        for (String key : keybindsToFetch) {
            if (key == null || !key.startsWith("key.keyboard.")) {
                continue;
            }

            String[] parts = key.split("\\.");
            String friendlyName = "";
            if (parts.length == 4) {
                friendlyName = (parts[2].substring(0, 1) + parts[3]).toUpperCase();
            } else if (parts.length == 3) {
                friendlyName = parts[2].toUpperCase();
            }

            if (!friendlyName.isEmpty()) {
                friendlyNames.add(friendlyName);
            }
        }
        return friendlyNames;
    }

    private void updateRandomKeys(List<Integer> keyList, float density) {
        keyList.clear();
        if (allLedIds.isEmpty())
            return;
        int count = (int) (allLedIds.size() * density);
        for (int i = 0; i < count; i++) {
            keyList.add(allLedIds.get(random.nextInt(allLedIds.size())));
        }
    }

    private RGBColorDto lerpColor(RGBColorDto start, RGBColorDto end, float t) {
        return new RGBColorDto(
                lerp(start.r, end.r, t),
                lerp(start.g, end.g, t),
                lerp(start.b, end.b, t));
    }

    private int lerp(int start, int end, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return (int) (start + (end - start) * t);
    }
}