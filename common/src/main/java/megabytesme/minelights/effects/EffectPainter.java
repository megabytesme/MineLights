package megabytesme.minelights.effects;

import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.config.ExperienceLocatorMode;
import megabytesme.minelights.model.CompassState;
import megabytesme.minelights.model.CompassType;
import megabytesme.minelights.model.PlayerDto;
//? if >=1.21.6 {
import megabytesme.minelights.model.WaypointDto;
//?}
//? if loader_forge && <1.17 {
import net.minecraft.client.Minecraft;
//? if loader_forge && <=1.8.9 {
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
//?} else if loader_forge && <=1.12.2 {
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
//?} else {
import net.minecraft.client.GameSettings;
//? if loader_forge && <=1.13.2 {
import net.minecraft.client.settings.KeyBinding;
//?}
//?}
//?} else if loader_neoforge || >=26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
//?} else if >=1.17 {
/* import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
*///?} else {
/* import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.GameOptions;
*///?}

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class EffectPainter {
    public static final Logger LOGGER = LogManager.getLogger("MineLights - EffectPainter");

    private final Random random = new Random();
    private final List<DeviceLayout> deviceLayouts;
    private final Map<DeviceLayout, DeviceEffectState> deviceStates = new HashMap<>();

    private static class Raindrop {
        int column;
        int row;
        long lastFallTime;
        final boolean isVertical;
        final RGBColorDto color;
        final RGBColorDto tailColor;
        final RGBColorDto tailColor2;

        Raindrop(int column, boolean isVertical, RGBColorDto color, long now) {
            this.column = column;
            this.row = 0;
            this.isVertical = isVertical;
            this.lastFallTime = now;
            this.color = color;
            this.tailColor = new RGBColorDto((int) (color.r * 0.5), (int) (color.g * 0.5), (int) (color.b * 0.5));
            this.tailColor2 = new RGBColorDto((int) (color.r * 0.2), (int) (color.g * 0.2), (int) (color.b * 0.2));
        }
    }

    private static class RamRaindrop {
        int position;
        long lastFallTime;
        final RGBColorDto color;
        final RGBColorDto tailColor;
        final RGBColorDto tailColor2;

        RamRaindrop(RGBColorDto color, long now) {
            this.position = -2;
            this.lastFallTime = now;
            this.color = color;
            this.tailColor = new RGBColorDto((int) (color.r * 0.5), (int) (color.g * 0.5), (int) (color.b * 0.5));
            this.tailColor2 = new RGBColorDto((int) (color.r * 0.2), (int) (color.g * 0.2), (int) (color.b * 0.2));
        }
    }

    private static class DeviceEffectState {
        final List<RamRaindrop> ramRaindrops = new ArrayList<>();
        long lastRamRaindropSpawn = 0;
        long nextRamRaindropDelay = 500;
    }

    private boolean rainPhase = false;
    private long lastRainPhaseStep = 0;
    private final List<Raindrop> raindrops = new ArrayList<>();
    private long lastRaindropSpawn = 0;
    private static final int RAINDROP_SPAWN_RATE_MS = 120;
    private static final int RAINDROP_FALL_SPEED_MS = 90;

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
    private float lastBrightnessFactor = 1.0F;

    private long chatPulseStartTime = 0;
    private boolean chatPulseActive = false;


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

    private long lastEndFlashCrackleUpdate = 0;
    private final List<Integer> endFlashCracklingKeys = new ArrayList<>();

    private static RGBColorDto blend(RGBColorDto base, RGBColorDto overlay, double overlayOpacity) {
        if (base == null)
            return overlay;
        if (overlay == null)
            return base;
        double baseOpacity = 1.0 - overlayOpacity;
        int r = (int) (base.r * baseOpacity + overlay.r * overlayOpacity);
        int g = (int) (base.g * baseOpacity + overlay.g * overlayOpacity);
        int b = (int) (base.b * baseOpacity + overlay.b * overlayOpacity);
        return new RGBColorDto(r, g, b);
    }

    public EffectPainter(List<DeviceLayout> deviceLayouts) {
        this.deviceLayouts = deviceLayouts;
        for (DeviceLayout layout : deviceLayouts) {
            deviceStates.put(layout, new DeviceEffectState());
        }
    }

    private List<Integer> getMappedIds(String keyName) {
        List<Integer> allIds = new ArrayList<>();
        for (DeviceLayout layout : deviceLayouts) {
            allIds.addAll(layout.getKeyMap().getOrDefault(keyName, Collections.emptyList()));
        }
        return allIds;
    }

    public FrameStateDto paint(PlayerDto player) {
        FrameStateDto state = new FrameStateDto();
        if (!MineLightsClient.CONFIG.enableMod)
            return state;

        if (!player.getInGame()) {
            for (DeviceLayout layout : deviceLayouts) {
                for (Integer ledId : layout.getAllLeds()) {
                    state.keys.put(ledId, new RGBColorDto(255, 0, 0));
                }
            }
            return state;
        }

        for (DeviceLayout layout : deviceLayouts) {
            for (Integer ledId : layout.getAllLeds()) {
                state.keys.put(ledId, new RGBColorDto(0, 0, 0));
            }
        }

        long now = System.currentTimeMillis();
        paintEnvironmentalBase(state, player, now);
        ExperienceLocatorMode barMode = MineLightsClient.CONFIG.experienceLocatorMode == null
                ? ExperienceLocatorMode.AUTO
                : MineLightsClient.CONFIG.experienceLocatorMode;
        //? if <1.21.6 {
        /* barMode = ExperienceLocatorMode.EXPERIENCE; */
        //?}
        boolean showExperience;
        boolean showLocator;
        if (barMode == ExperienceLocatorMode.AUTO) {
            String hudBar = player.getHudBar();
            if (hudBar == null) {
                boolean hasWaypoints = player.getWaypoints() != null && !player.getWaypoints().isEmpty();
                hudBar = hasWaypoints ? "LOCATOR" : "EXPERIENCE";
            }
            showLocator = hudBar.equals("LOCATOR") && MineLightsClient.CONFIG.enableLocatorBar;
            showExperience = hudBar.equals("EXPERIENCE") || (hudBar.equals("LOCATOR") && !showLocator);
        } else {
            showExperience = barMode == ExperienceLocatorMode.EXPERIENCE || barMode == ExperienceLocatorMode.BOTH;
            showLocator = barMode == ExperienceLocatorMode.LOCATOR || barMode == ExperienceLocatorMode.BOTH;
        }
        if (showExperience && MineLightsClient.CONFIG.enableExperienceBar)
            paintExperienceBar(state, player);
        //? if >=1.21.6 {
        if (showLocator && MineLightsClient.CONFIG.enableLocatorBar) {
            paintLocatorBar(state, player, barMode != ExperienceLocatorMode.BOTH);
        }
        //?}
        paintPlayerBars(state, player);
        if (MineLightsClient.CONFIG.enableSaturationBar
                && !(MineLightsClient.CONFIG.mergeSaturationIntoHunger && MineLightsClient.CONFIG.enableHungerBar))
            paintSaturationAndAirBar(state, player);
        if (MineLightsClient.CONFIG.enableCompassEffect)
            paintCompass(state, player);
        if (MineLightsClient.CONFIG.enableLowHealthWarning)
            paintHealthEffects(state, player);
        paintPlayerEffects(state, player, now);
        return state;
    }

    private void paintEnvironmentalBase(FrameStateDto state, PlayerDto player, long now) {
        RGBColorDto baseColor = resolveEnvironmentalBaseColor(player, now);

        for (DeviceLayout layout : deviceLayouts) {
            for (Integer ledId : layout.getAllLeds()) {
                state.keys.put(ledId, baseColor);
            }
        }

        paintSpecialWorldEffects(state, player, now);
    }

    private boolean isPlayerInOrOnBlock(PlayerDto player, String... blockIds) {
        List<String> relevantBlocks = new ArrayList<>();
        if (player.getBlockAtFeet() != null) {
            relevantBlocks.add(player.getBlockAtFeet());
        }
        if (player.getBlockOn() != null) {
            relevantBlocks.add(player.getBlockOn());
        }
        if (player.getBlockAtHead() != null) {
            relevantBlocks.add(player.getBlockAtHead());
        }

        if (relevantBlocks.isEmpty()) {
            return false;
        }
        
        for (String blockId : blockIds) {
            String normalizedBlockId = normalizeBlockId(blockId);
            for (String relevantBlock : relevantBlocks) {
                if (normalizeBlockId(relevantBlock).equals(normalizedBlockId)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private String normalizeBlockId(String blockId) {
        if (blockId == null) {
            return "";
        }
        if (blockId.startsWith("block.")) {
            return blockId.substring("block.".length()).replace('.', ':');
        }
        return blockId;
    }

    private RGBColorDto resolveEnvironmentalBaseColor(PlayerDto player, long now) {
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

        boolean isSoulFire = false;
        if (player.getIsOnFire()) {
            isSoulFire = isPlayerInOrOnBlock(player, "block.minecraft.soul_fire", "block.minecraft.soul_sand",
                    "minecraft:soul_soil");
        }
        boolean isRegularFire = isPlayerInOrOnBlock(player, "block.minecraft.fire", "block.minecraft.lava");
        
        if (MineLightsClient.CONFIG.enableOnFireEffect && (player.getIsOnFire() || isRegularFire || isSoulFire)) {
            baseColor = isSoulFire ? new RGBColorDto(0, 100, 255) : new RGBColorDto(255, 69, 0);
        } else if (MineLightsClient.CONFIG.enablePortalEffects
                && isPlayerInOrOnBlock(player, "block.minecraft.nether_portal")) {
            baseColor = new RGBColorDto(128, 0, 128);
        } else if (MineLightsClient.CONFIG.enablePortalEffects
                && isPlayerInOrOnBlock(player, "block.minecraft.end_portal")) {
            baseColor = new RGBColorDto(0, 0, 50);
        } else {
            baseColor = applyLightDimming(baseColor, player);
        }

        return baseColor;
    }

    private RGBColorDto applyLightDimming(RGBColorDto color, PlayerDto player) {
        float targetFactor = 1.0F;

        switch (MineLightsClient.CONFIG.dimmingMode) {
            case SKY_LIGHT:
                int skyLight = player.getSkyLightLevel();
                if (isNetherOrEnd(player)) {
                    targetFactor = 1.0F;
                } else {
                    targetFactor = skyLight / 15.0F;
                }
                break;

            case LOCAL_LIGHT:
                float rendered = player.getRenderedBrightnessLevel();
                if (isNetherOrEnd(player)) {
                    targetFactor = 1.0F;
                } else {
                    targetFactor = rendered;
                }
                break;

            case NONE:
            default:
                return color;
        }

        targetFactor = Math.max(MineLightsClient.CONFIG.minBrightness, targetFactor);

        float t = 0.1f;
        float smoothedFactor = lerp(lastBrightnessFactor, targetFactor, t);
        lastBrightnessFactor = smoothedFactor;

        return new RGBColorDto(
            (int)(color.r * smoothedFactor),
            (int)(color.g * smoothedFactor),
            (int)(color.b * smoothedFactor)
        );
    }

    private boolean isNetherOrEnd(PlayerDto player) {
        String dim = player.getCurrentWorld();
        return "minecraft:the_nether".equals(dim) || "minecraft:the_end".equals(dim);
    }

    private void paintSpecialWorldEffects(FrameStateDto state, PlayerDto player, long now) {
        paintWeatherEffects(state, player, now);

        //? if >= 1.21.9 {
        if (MineLightsClient.CONFIG.enableEndFlashEffect && player.getCurrentWorld().equals("minecraft:the_end")) {
            paintEndFlashEffect(state, player, now);
        }
        //?}

        if (player.getIsLightningFlashing())
            return;

        List<Integer> allDeviceLeds = deviceLayouts.stream()
                .flatMap(d -> d.getAllLeds().stream())
                .collect(Collectors.toList());

        boolean isSoulFire = false;
        if (player.getIsOnFire()) {
            isSoulFire = isPlayerInOrOnBlock(player, "block.minecraft.soul_fire", "block.minecraft.soul_sand",
                    "minecraft:soul_soil");
        }
        boolean isRegularFire = isPlayerInOrOnBlock(player, "block.minecraft.fire", "block.minecraft.lava");
        
        if (MineLightsClient.CONFIG.enableOnFireEffect && (player.getIsOnFire() || isRegularFire || isSoulFire)) {
            if (now - lastFireCrackleUpdate > 100) {
                updateRandomKeys(cracklingKeys, allDeviceLeds, 0.2f);
                lastFireCrackleUpdate = now;
            }
            RGBColorDto crackleColor = isSoulFire ? new RGBColorDto(173, 216, 230) : new RGBColorDto(200, 0, 0);
            for (Integer keyId : cracklingKeys)
                state.keys.put(keyId, crackleColor);
        } else if (MineLightsClient.CONFIG.enablePortalEffects
                && isPlayerInOrOnBlock(player, "block.minecraft.nether_portal")) {
            if (now - lastPortalTwinkleUpdate > 500) {
                updateRandomKeys(twinklingKeys, allDeviceLeds, 0.2f);
                lastPortalTwinkleUpdate = now;
            }
            for (Integer keyId : twinklingKeys)
                state.keys.put(keyId, new RGBColorDto(50, 0, 100));
        } else if (MineLightsClient.CONFIG.enablePortalEffects
                && isPlayerInOrOnBlock(player, "block.minecraft.end_portal")) {
            if (now - lastPortalTwinkleUpdate > 500) {
                updateRandomKeys(twinklingKeys, allDeviceLeds, 0.2f);
                lastPortalTwinkleUpdate = now;
            }
            for (Integer keyId : twinklingKeys)
                state.keys.put(keyId, new RGBColorDto(50, 50, 50));
        }
    }

    //? if >= 1.21.9 {
    private void paintEndFlashEffect(FrameStateDto state, PlayerDto player, long now) {
        float intensity = player.getEndFlashIntensity();
        if (intensity <= 0.0f) {
            return;
        }

        RGBColorDto tintColor = new RGBColorDto(150, 80, 255);
        RGBColorDto flashColor = new RGBColorDto(220, 180, 255);

        for (DeviceLayout layout : deviceLayouts) {
            for (Integer ledId : layout.getAllLeds()) {
                RGBColorDto baseColor = state.keys.get(ledId);
                state.keys.put(ledId, blend(baseColor, tintColor, intensity * 0.8));
            }
        }

        List<Integer> allDeviceLeds = deviceLayouts.stream()
                .flatMap(d -> d.getAllLeds().stream())
                .collect(Collectors.toList());

        if (now - lastEndFlashCrackleUpdate > 120) {
            updateRandomKeys(endFlashCracklingKeys, allDeviceLeds, 0.15f);
            lastEndFlashCrackleUpdate = now;
        }

        for (Integer keyId : endFlashCracklingKeys) {
            RGBColorDto currentColor = state.keys.get(keyId);
            state.keys.put(keyId, blend(currentColor, flashColor, intensity));
        }
    }
    //?}

    private void paintWeatherEffects(FrameStateDto state, PlayerDto player, long now) {
        if (player.getIsLightningFlashing()) {
            for (DeviceLayout layout : deviceLayouts) {
                for (Integer ledId : layout.getAllLeds())
                    state.keys.put(ledId, new RGBColorDto(255, 255, 255));
            }
            return;
        }

        boolean isRaining = MineLightsClient.CONFIG.enableWeatherEffects
                && (player.getWeather().equals("Rain") || player.getWeather().equals("Thunderstorm"))
                && BiomeData.isBiomeRainy(player.getCurrentBiome());
        if (!isRaining) {
            raindrops.clear();
            return;
        }

        RGBColorDto rainColor = BiomeData.getBiomeRainColor(player.getCurrentBiome());
        if (now - lastRainPhaseStep > 500) {
            rainPhase = !rainPhase;
            lastRainPhaseStep = now;
        }

        if (now - lastRaindropSpawn > RAINDROP_SPAWN_RATE_MS) {
            if (random.nextInt(100) < 75) {
                int startRow = random.nextInt(2);
                List<String> rowKeys = KeyMap.KEYBOARD_ROWS.get(startRow);
                raindrops.add(new Raindrop(random.nextInt(rowKeys.size()), false, rainColor, now));
            } else {
                raindrops.add(new Raindrop(random.nextInt(KeyMap.KEYBOARD_COLUMNS.size()), true, rainColor, now));
            }
            lastRaindropSpawn = now;
        }

        for (DeviceLayout layout : deviceLayouts) {
            boolean hasKeyboardKeys = layout.getKeyMap().keySet().stream().anyMatch(k -> k.equals("Q"));
            boolean hasRamKeys = layout.getKeyMap().keySet().stream().anyMatch(k -> k.startsWith("RAM_"));
            boolean hasPlayerKeys = layout.getKeyMap().keySet().stream().anyMatch(k -> k.startsWith("PLAYER_"));

            if (hasKeyboardKeys) {
                applyWaveEffect(layout, state, "UNDERGLOW_", rainColor, now, 40, 8);
            } else if (hasRamKeys) {
                applyRamRainEffect(layout, state, rainColor, now);
            } else if (hasPlayerKeys) {
                applyCascadeEffect(layout, state, "PLAYER_", rainColor, now, 150);
            } else {
                applyZonalRainEffect(layout, state, rainColor);
            }
        }

        Iterator<Raindrop> iterator = raindrops.iterator();
        while (iterator.hasNext()) {
            Raindrop drop = iterator.next();
            if (now - drop.lastFallTime > RAINDROP_FALL_SPEED_MS) {
                drop.row++;
                drop.lastFallTime = now;
            }
            int pathLength = drop.isVertical ? KeyMap.KEYBOARD_COLUMNS.get(drop.column).size()
                    : KeyMap.KEYBOARD_ROWS.size();
            if (drop.row >= pathLength + 2) {
                iterator.remove();
                continue;
            }
            if (drop.isVertical) {
                drawVerticalRaindropSegment(state, drop, 0, drop.color);
                drawVerticalRaindropSegment(state, drop, -1, drop.tailColor);
                drawVerticalRaindropSegment(state, drop, -2, drop.tailColor2);
            } else {
                drawHorizontalRaindropSegment(state, drop, 0, drop.color);
                drawHorizontalRaindropSegment(state, drop, -1, drop.tailColor);
                drawHorizontalRaindropSegment(state, drop, -2, drop.tailColor2);
            }
        }
    }

    private void applyZonalRainEffect(DeviceLayout layout, FrameStateDto state, RGBColorDto rainColor) {
        List<Integer> leds = layout.getAllLeds();
        for (int i = 0; i < leds.size(); i++) {
            if (i % 2 == (rainPhase ? 0 : 1)) {
                Integer ledId = leds.get(i);
                RGBColorDto baseColor = state.keys.get(ledId);
                state.keys.put(ledId, blend(baseColor, rainColor, 0.7));
            }
        }
    }

    private void applyRamRainEffect(DeviceLayout layout, FrameStateDto state, RGBColorDto color, long now) {
        DeviceEffectState deviceState = deviceStates.get(layout);
        if (deviceState == null)
            return;

        List<String> ramKeys = layout.getKeyMap().keySet().stream()
                .filter(k -> k.startsWith("RAM_"))
                .sorted(Comparator.comparingInt(k -> Integer.parseInt(k.substring(4))))
                .collect(Collectors.toList());

        if (ramKeys.isEmpty())
            return;

        boolean mirrorDirection = MineLightsClient.CONFIG.mirrorRamRainDirection;
        int direction = mirrorDirection ? -1 : 1;

        if (now > deviceState.lastRamRaindropSpawn + deviceState.nextRamRaindropDelay) {
            deviceState.ramRaindrops.add(new RamRaindrop(color, now));
            deviceState.lastRamRaindropSpawn = now;
            deviceState.nextRamRaindropDelay = 200 + random.nextInt(800);
        }

        Map<String, RGBColorDto> ramKeyColors = new HashMap<>();

        Iterator<RamRaindrop> iterator = deviceState.ramRaindrops.iterator();
        while (iterator.hasNext()) {
            RamRaindrop drop = iterator.next();

            if (now - drop.lastFallTime > RAINDROP_FALL_SPEED_MS) {
                drop.position++;
                drop.lastFallTime = now;
            }

            if (drop.position >= ramKeys.size() + 2) {
                iterator.remove();
                continue;
            }

            int headPosition = mirrorDirection ? ramKeys.size() - 1 - drop.position : drop.position;
            if (headPosition >= 0 && headPosition < ramKeys.size()) {
                ramKeyColors.put(ramKeys.get(headPosition), drop.color);
            }
            int tail1Pos = headPosition - direction;
            if (tail1Pos >= 0 && tail1Pos < ramKeys.size()) {
                ramKeyColors.putIfAbsent(ramKeys.get(tail1Pos), drop.tailColor);
            }
            int tail2Pos = headPosition - 2 * direction;
            if (tail2Pos >= 0 && tail2Pos < ramKeys.size()) {
                ramKeyColors.putIfAbsent(ramKeys.get(tail2Pos), drop.tailColor2);
            }
        }

        for (Map.Entry<String, RGBColorDto> entry : ramKeyColors.entrySet()) {
            for (Integer ledId : layout.getKeyMap().get(entry.getKey())) {
                RGBColorDto baseColor = state.keys.get(ledId);
                state.keys.put(ledId, blend(baseColor, entry.getValue(), 0.9));
            }
        }
    }

    private void applyWaveEffect(DeviceLayout layout, FrameStateDto state, String prefix, RGBColorDto color, long now,
            int speed, int tailLength) {
        List<String> keyNames = layout.getKeyMap().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .sorted(Comparator.comparingInt(k -> Integer.parseInt(k.substring(prefix.length()))))
                .collect(Collectors.toList());

        if (keyNames.isEmpty())
            return;
        int headIndex = (int) ((now / speed) % keyNames.size());

        for (int i = 0; i < keyNames.size(); i++) {
            int distance = (i - headIndex + keyNames.size()) % keyNames.size();

            if (distance < tailLength) {
                float brightness = 1.0f - ((float) distance / tailLength);
                RGBColorDto waveColor = new RGBColorDto((int) (color.r * brightness), (int) (color.g * brightness),
                        (int) (color.b * brightness));
                for (Integer ledId : layout.getKeyMap().get(keyNames.get(i))) {
                    RGBColorDto baseColor = state.keys.get(ledId);
                    state.keys.put(ledId, blend(baseColor, waveColor, 0.8));
                }
            }
        }
    }

    private void applyCascadeEffect(DeviceLayout layout, FrameStateDto state, String prefix, RGBColorDto color,
            long now, int speed) {
        List<String> keyNames = layout.getKeyMap().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .sorted(Comparator.comparingInt(k -> Integer.parseInt(k.substring(prefix.length()))))
                .collect(Collectors.toList());

        if (keyNames.isEmpty())
            return;
        int headIndex = (int) ((now / speed) % (keyNames.size() + 3));

        for (int i = 0; i < keyNames.size(); i++) {
            RGBColorDto finalColor = new RGBColorDto(0, 0, 0);
            if (i == headIndex)
                finalColor = color;
            else if (i == headIndex - 1)
                finalColor = new RGBColorDto((int) (color.r * 0.5), (int) (color.g * 0.5), (int) (color.b * 0.5));
            else if (i == headIndex - 2)
                finalColor = new RGBColorDto((int) (color.r * 0.2), (int) (color.g * 0.2), (int) (color.b * 0.2));

            for (Integer ledId : layout.getKeyMap().get(keyNames.get(i))) {
                RGBColorDto baseColor = state.keys.get(ledId);
                state.keys.put(ledId, blend(baseColor, finalColor, 0.9));
            }
        }
    }

    private void drawHorizontalRaindropSegment(FrameStateDto state, Raindrop drop, int rowOffset, RGBColorDto color) {
        int targetRow = drop.row + rowOffset;
        if (targetRow < 0 || targetRow >= KeyMap.KEYBOARD_ROWS.size())
            return;
        List<String> rowKeys = KeyMap.KEYBOARD_ROWS.get(targetRow);
        if (drop.column >= rowKeys.size())
            return;
        String keyName = rowKeys.get(drop.column);
        for (Integer ledId : getMappedIds(keyName)) {
            RGBColorDto baseColor = state.keys.get(ledId);
            state.keys.put(ledId, blend(baseColor, color, 0.9));
        }
    }

    private void drawVerticalRaindropSegment(FrameStateDto state, Raindrop drop, int rowOffset, RGBColorDto color) {
        List<String> columnKeys = KeyMap.KEYBOARD_COLUMNS.get(drop.column);
        int targetRow = drop.row + rowOffset;
        if (targetRow < 0 || targetRow >= columnKeys.size())
            return;
        String keyName = columnKeys.get(targetRow);
        for (Integer ledId : getMappedIds(keyName)) {
            RGBColorDto baseColor = state.keys.get(ledId);
            state.keys.put(ledId, blend(baseColor, color, 0.9));
        }
    }

    private void paintExperienceBar(FrameStateDto state, PlayerDto player) {
        List<String> logicalKeys = KeyMap.getExperienceBar();
        float filledKeys = player.getExperience() * logicalKeys.size();
        RGBColorDto fullColor = color(MineLightsClient.CONFIG.experienceColor);
        RGBColorDto dimColor = color(MineLightsClient.CONFIG.experienceDimColor);
        for (int i = 0; i < logicalKeys.size(); i++) {
            RGBColorDto color = lerpColor(dimColor, fullColor, filledKeys - i);
            for (Integer ledId : getMappedIds(logicalKeys.get(i)))
                state.keys.put(ledId, color);
        }
    }

    //? if >=1.21.6 {
    private void paintLocatorBar(FrameStateDto state, PlayerDto player, boolean drawBackground) {
        List<String> locatorBarKeys = KeyMap.getLocatorBar();
        if (locatorBarKeys.isEmpty()) {
            return;
        }
        if (drawBackground) {
            RGBColorDto background = color(MineLightsClient.CONFIG.locatorBarColor);
            for (String key : locatorBarKeys) {
                for (Integer ledId : getMappedIds(key))
                    state.keys.put(ledId, background);
            }
        }
        if (player.getWaypoints() == null || player.getWaypoints().isEmpty()) {
            return;
        }

        int numKeys = locatorBarKeys.size();
        float viewAngle = 60.0f;
        float dotSize = Math.max(0.5f, MineLightsClient.CONFIG.locatorDotSize);

        List<WaypointDto> waypoints = new ArrayList<>(player.getWaypoints());
        waypoints.sort((a, b) -> Float.compare(b.getDistance(), a.getDistance()));

        for (WaypointDto waypoint : waypoints) {
            double yaw = waypoint.getRelativeYaw();
            if (yaw <= -viewAngle || yaw > viewAngle) {
                continue;
            }

            double position = (yaw + viewAngle) / (viewAngle * 2.0) * (numKeys - 1);

            double radius = dotSize * LOCATOR_DOT_STEPS[locatorDotStep(waypoint.getDistance())];

            RGBColorDto dotColor = brighten(color(waypoint.getColor()));
            if (waypoint.getPitch() == WaypointDto.Pitch.UP) {
                dotColor = lerpColor(dotColor, new RGBColorDto(255, 255, 255), 0.35f);
            } else if (waypoint.getPitch() == WaypointDto.Pitch.DOWN) {
                dotColor = lerpColor(dotColor, new RGBColorDto(0, 0, 0), 0.35f);
            }

            double[] weights = new double[numKeys];
            double peak = 0;
            for (int i = 0; i < numKeys; i++) {
                weights[i] = Math.max(0, Math.min(1, 1.0 - (Math.abs(i - position) - radius)));
                peak = Math.max(peak, weights[i]);
            }
            for (int i = 0; i < numKeys; i++) {
                double weight = weights[i] / peak;
                if (weight <= 0) {
                    continue;
                }
                for (Integer ledId : getMappedIds(locatorBarKeys.get(i))) {
                    state.keys.put(ledId, blend(state.keys.get(ledId), dotColor, weight));
                }
            }
        }
    }

    private static final double[] LOCATOR_DOT_STEPS = { 1.0, 0.65, 0.35, 0.0 };

    private static int locatorDotStep(float distance) {
        float near = 128.0f;
        float far = 332.0f;
        if (distance <= near) {
            return 0;
        }
        if (distance >= far) {
            return LOCATOR_DOT_STEPS.length - 1;
        }
        return Math.min(LOCATOR_DOT_STEPS.length - 1,
                (int) ((distance - near) / (far - near) * LOCATOR_DOT_STEPS.length));
    }

    private static RGBColorDto brighten(RGBColorDto color) {
        int max = Math.max(color.r, Math.max(color.g, color.b));
        if (max == 0) {
            return color;
        }
        float scale = 255.0f / max;
        return new RGBColorDto((int) (color.r * scale), (int) (color.g * scale), (int) (color.b * scale));
    }
    //?}

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
            RGBColorDto healthDim = color(MineLightsClient.CONFIG.healthDimColor);
            RGBColorDto healthFull = color(MineLightsClient.CONFIG.healthColor);
            RGBColorDto absorptionColor = color(MineLightsClient.CONFIG.absorptionColor);
            List<String> healthKeys = KeyMap.getHealthBar();
            float healthPerKey = 20.0f / Math.max(1, healthKeys.size());
            for (int i = 0; i < healthKeys.size(); i++) {
                RGBColorDto finalColor;
                if (isDamageFlashActive)
                    finalColor = new RGBColorDto(255, 255, 255);
                else if (player.getIsWithering())
                    finalColor = new RGBColorDto(43, 43, 43);
                else if (player.getIsPoisoned())
                    finalColor = new RGBColorDto(148, 120, 24);
                else {
                    float t = (player.getHealth() - (i * healthPerKey)) / healthPerKey;
                    finalColor = lerpColor(healthDim, healthFull, t);
                }
                if (!isDamageFlashActive && MineLightsClient.CONFIG.enableAbsorptionOverlay
                        && player.getAbsorption() > 0) {
                    float goldT = (player.getAbsorption() - (i * healthPerKey)) / healthPerKey;
                    finalColor = lerpColor(finalColor, absorptionColor, goldT);
                }
                for (Integer ledId : getMappedIds(healthKeys.get(i)))
                    state.keys.put(ledId, finalColor);
            }
        }

        if (MineLightsClient.CONFIG.enableHungerBar) {
            RGBColorDto hungerDim = color(MineLightsClient.CONFIG.hungerDimColor);
            RGBColorDto hungerFull = color(MineLightsClient.CONFIG.hungerColor);
            List<String> hungerKeys = KeyMap.getHungerBar();
            boolean merged = MineLightsClient.CONFIG.mergeSaturationIntoHunger
                    && MineLightsClient.CONFIG.enableSaturationBar;
            if (merged && isAirBarVisible(player)) {
                paintBar(state, hungerKeys, player.getAir(), 300f, color(MineLightsClient.CONFIG.airDimColor),
                        color(MineLightsClient.CONFIG.airColor));
                return;
            }
            RGBColorDto saturationColor = color(MineLightsClient.CONFIG.saturationColor);
            float hungerPerKey = 20.0f / Math.max(1, hungerKeys.size());
            for (int i = 0; i < hungerKeys.size(); i++) {
                float t = (player.getHunger() - (i * hungerPerKey)) / hungerPerKey;
                RGBColorDto color = lerpColor(hungerDim, hungerFull, t);
                if (merged) {
                    float saturationT = (player.getSaturation() - (i * hungerPerKey)) / hungerPerKey;
                    float strength = Math.max(0f, Math.min(1f, MineLightsClient.CONFIG.saturationTintStrength));
                    color = lerpColor(color, saturationColor, strength * Math.max(0f, Math.min(1f, saturationT)));
                }
                for (Integer ledId : getMappedIds(hungerKeys.get(i)))
                    state.keys.put(ledId, color);
            }
        }
    }

    private void paintSaturationAndAirBar(FrameStateDto state, PlayerDto player) {
        List<String> barKeys = KeyMap.getSaturationBar();
        if (isAirBarVisible(player)) {
            paintBar(state, barKeys, player.getAir(), 300f, color(MineLightsClient.CONFIG.airDimColor),
                    color(MineLightsClient.CONFIG.airColor));
        } else {
            paintBar(state, barKeys, player.getSaturation(), 20f, color(MineLightsClient.CONFIG.saturationDimColor),
                    color(MineLightsClient.CONFIG.saturationColor));
        }
    }

    private boolean isAirBarVisible(PlayerDto player) {
        if (player.getAirBarVisible() != null)
            return player.getAirBarVisible();
        return player.getAir() < 300 || isPlayerInOrOnBlock(player, "block.minecraft.water");
    }

    private void paintBar(FrameStateDto state, List<String> keys, float value, float maxValue, RGBColorDto dimColor,
            RGBColorDto fullColor) {
        if (keys.isEmpty())
            return;
        float valuePerKey = maxValue / keys.size();
        for (int i = 0; i < keys.size(); i++) {
            float t = (value - (i * valuePerKey)) / valuePerKey;
            RGBColorDto color = lerpColor(dimColor, fullColor, t);
            for (Integer ledId : getMappedIds(keys.get(i)))
                state.keys.put(ledId, color);
        }
    }

    private void paintHealthEffects(FrameStateDto state, PlayerDto player) {
        boolean isLowHealthNow = player.getHealth() < MineLightsClient.CONFIG.lowHealthThreshold;
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
        RGBColorDto heartbeatColor = color(MineLightsClient.CONFIG.heartbeatColor);
        RGBColorDto pulsedHeartbeat = new RGBColorDto((int) (heartbeatColor.r * heartbeatBrightness),
                (int) (heartbeatColor.g * heartbeatBrightness), (int) (heartbeatColor.b * heartbeatBrightness));
        for (String keyName : KeyMap.getHeartbeatRed()) {
            for (Integer ledId : getMappedIds(keyName))
                state.keys.put(ledId, pulsedHeartbeat);
        }
        for (String keyName : KeyMap.getHeartbeatWhite()) {
            for (Integer ledId : getMappedIds(keyName))
                state.keys.put(ledId, new RGBColorDto(r, r, r));
        }
    }

    private void paintCompass(FrameStateDto state, PlayerDto player) {
        if (player.getCompassType() == CompassType.NONE)
            return;
        List<String> compassKeys = KeyMap.getNumpadDirectional();
        if (compassKeys.isEmpty())
            return;

        RGBColorDto compassColor, backgroundColor;
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

        for (String keyName : compassKeys) {
            for (Integer ledId : getMappedIds(keyName))
                state.keys.put(ledId, backgroundColor);
        }
        for (Integer ledId : getMappedIds(KeyMap.getNumpadCenter())) {
            state.keys.put(ledId, compassColor);
        }

        if (player.getCompassState() == CompassState.SPINNING) {
            long now = System.currentTimeMillis();
            if (now - lastCompassSpinTime > 75) {
                compassSpinIndex = (compassSpinIndex + 1) % compassKeys.size();
                lastCompassSpinTime = now;
            }
            String keyToLight = compassKeys.get(compassSpinIndex % compassKeys.size());
            for (Integer ledId : getMappedIds(keyToLight))
                state.keys.put(ledId, compassColor);
        } else if (player.getCompassState() == CompassState.POINTING) {
            if (player.getCompassDistance() != null && player.getCompassDistance() < 8) {
                for (Integer centerLedId : getMappedIds(KeyMap.getNumpadCenter())) {
                    long now = System.currentTimeMillis();
                    double pulseWave = (Math.sin(now / 2000.0) + 1.0) / 2.0;
                    float brightness = (float) (0.6 + pulseWave * 0.4);
                    RGBColorDto pulsedColor = new RGBColorDto((int) (compassColor.r * brightness),
                            (int) (compassColor.g * brightness), (int) (compassColor.b * brightness));
                    state.keys.put(centerLedId, pulsedColor);
                }
            } else {
                Double relativeYaw = player.getCompassRelativeYaw();
                if (relativeYaw == null)
                    return;
                double sector = 360.0 / compassKeys.size();
                int keyIndex = (int) Math.floor(((relativeYaw + sector / 2.0) % 360.0 + 360.0) % 360.0 / sector);
                String keyToLight = compassKeys.get(keyIndex % compassKeys.size());
                for (Integer ledId : getMappedIds(keyToLight))
                    state.keys.put(ledId, compassColor);
            }
        }

        if (player.getCompassType() == CompassType.LODESTONE && MineLightsClient.CONFIG.lodestoneCompassShimmer) {
            long now = System.currentTimeMillis();
            if (now - lastSheenTime > 60) {
                sheenIndex = (sheenIndex + 1) % compassKeys.size();
                lastSheenTime = now;
            }
            List<String> directionalKeys = compassKeys;
            RGBColorDto sheenTintColor = new RGBColorDto(120, 0, 255);
            double[] opacities = { 0.6, 0.4, 0.2 };
            for (int i = 0; i < opacities.length; i++) {
                int keyIndex = (sheenIndex - i + directionalKeys.size()) % directionalKeys.size();
                String keyName = directionalKeys.get(keyIndex);
                for (Integer ledId : getMappedIds(keyName)) {
                    RGBColorDto baseColor = state.keys.get(ledId);
                    state.keys.put(ledId, blend(baseColor, sheenTintColor, opacities[i]));
                }
            }
        }
    }

    private void paintPlayerEffects(FrameStateDto state, PlayerDto player, long now) {
        RGBColorDto keyColor = null;
        if (MineLightsClient.CONFIG.enableInWaterEffect && isPlayerInOrOnBlock(player, "block.minecraft.water")) {
            keyColor = new RGBColorDto(0, 100, 255);
        } else if (MineLightsClient.CONFIG.enableOnFireEffect
                && (isPlayerInOrOnBlock(player, "block.minecraft.lava", "block.minecraft.fire", "block.minecraft.soul_fire",
                        "block.minecraft.soul_sand", "minecraft:soul_soil"))) {
                    boolean isSoulFire = false;
                    if (player.getIsOnFire()) {
                        isSoulFire = isPlayerInOrOnBlock(player, "block.minecraft.soul_fire", "block.minecraft.soul_sand",
                                "minecraft:soul_soil");
                    }        
            keyColor = isSoulFire ? new RGBColorDto(0, 100, 255) : new RGBColorDto(255, 0, 0);
        } else if (MineLightsClient.CONFIG.highlightMovementKeys) {
            keyColor = new RGBColorDto(255, 255, 255);
        }
        if (keyColor != null) {
            for (String keyName : getMovementKeyNames()) {
                for (Integer ledId : getMappedIds(keyName))
                    state.keys.put(ledId, keyColor);
            }
        }

        if (MineLightsClient.CONFIG.pulseChatKey) {
            paintChatPulseEffect(state, player, now);
        }
    }

    private List<String> getMovementKeyNames() {
        List<String> friendlyNames = new ArrayList<>();
        List<String> keybindsToFetch = new ArrayList<>();
        //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        GameSettings options = MineLightsClient.getMinecraft().gameSettings;
        //?} else if loader_forge && <1.17 {
        GameSettings options = MineLightsClient.getMinecraft().options;
        //?} else if loader_neoforge || >=26.1 {
        Options options = MineLightsClient.getMinecraft().options;
        //?} else {
        /* GameOptions options = MinecraftClient.getInstance().options;
        *///?}
        //? if loader_forge && <=1.8.9 {
        keybindsToFetch = Arrays.asList(
                Keyboard.getKeyName(options.keyBindForward.getKeyCode()),
                Keyboard.getKeyName(options.keyBindBack.getKeyCode()),
                Keyboard.getKeyName(options.keyBindLeft.getKeyCode()),
                Keyboard.getKeyName(options.keyBindRight.getKeyCode()),
                Keyboard.getKeyName(options.keyBindJump.getKeyCode()),
                Keyboard.getKeyName(options.keyBindSneak.getKeyCode()),
                Keyboard.getKeyName(options.keyBindSprint.getKeyCode()));
        //?} else if loader_forge && <=1.11.2 {
        keybindsToFetch = Arrays.asList(
                options.keyBindForward.getDisplayName(),
                options.keyBindBack.getDisplayName(),
                options.keyBindLeft.getDisplayName(),
                options.keyBindRight.getDisplayName(),
                options.keyBindJump.getDisplayName(),
                options.keyBindSneak.getDisplayName(),
                options.keyBindSprint.getDisplayName());
        //?} else if loader_forge && <=1.13.2 {
        keybindsToFetch = Arrays.asList(
                KeyBinding.getDisplayString(options.keyBindForward.getKeyDescription()).get(),
                KeyBinding.getDisplayString(options.keyBindBack.getKeyDescription()).get(),
                KeyBinding.getDisplayString(options.keyBindLeft.getKeyDescription()).get(),
                KeyBinding.getDisplayString(options.keyBindRight.getKeyDescription()).get(),
                KeyBinding.getDisplayString(options.keyBindJump.getKeyDescription()).get(),
                KeyBinding.getDisplayString(options.keyBindSneak.getKeyDescription()).get(),
                KeyBinding.getDisplayString(options.keyBindSprint.getKeyDescription()).get());
        //?} else if loader_forge && <=1.14.3 {
        keybindsToFetch = Arrays.asList(
                options.keyBindForward.getLocalizedName(),
                options.keyBindBack.getLocalizedName(),
                options.keyBindLeft.getLocalizedName(),
                options.keyBindRight.getLocalizedName(),
                options.keyBindJump.getLocalizedName(),
                options.keyBindSneak.getLocalizedName(),
                options.keyBindSprint.getLocalizedName());
        //?} else if loader_forge && <=1.14.4 {
        keybindsToFetch = Arrays.asList(
                options.keyUp.getTranslatedKeyMessage(),
                options.keyDown.getTranslatedKeyMessage(),
                options.keyLeft.getTranslatedKeyMessage(),
                options.keyRight.getTranslatedKeyMessage(),
                options.keyJump.getTranslatedKeyMessage(),
                options.keySneak.getTranslatedKeyMessage(),
                options.keySprint.getTranslatedKeyMessage());
        //?} else if loader_forge && <=1.15.2 {
        keybindsToFetch = Arrays.asList(
                options.keyUp.getTranslatedKeyMessage(),
                options.keyDown.getTranslatedKeyMessage(),
                options.keyLeft.getTranslatedKeyMessage(),
                options.keyRight.getTranslatedKeyMessage(),
                options.keyJump.getTranslatedKeyMessage(),
                options.keyShift.getTranslatedKeyMessage(),
                options.keySprint.getTranslatedKeyMessage());
        //?} else if loader_forge && 1.16.1 {
        keybindsToFetch = Arrays.asList(
                options.keyBindForward.getKey().getTranslationKey(),
                options.keyBindBack.getKey().getTranslationKey(),
                options.keyBindLeft.getKey().getTranslationKey(),
                options.keyBindRight.getKey().getTranslationKey(),
                options.keyBindJump.getKey().getTranslationKey(),
                options.keyBindSneak.getKey().getTranslationKey(),
                options.keyBindSprint.getKey().getTranslationKey());
        //?} else if loader_forge || >= 1.19 {
        keybindsToFetch = Arrays.asList(
                //? if loader_neoforge || >=26.1 {
                options.keyUp.getTranslatedKeyMessage().getString(),
                options.keyDown.getTranslatedKeyMessage().getString(),
                options.keyLeft.getTranslatedKeyMessage().getString(),
                options.keyRight.getTranslatedKeyMessage().getString(),
                options.keyJump.getTranslatedKeyMessage().getString(),
                options.keyShift.getTranslatedKeyMessage().getString(),
                options.keySprint.getTranslatedKeyMessage().getString()
                //?} else {
                /* options.forwardKey.getBoundKeyTranslationKey(),
                options.backKey.getBoundKeyTranslationKey(),
                options.leftKey.getBoundKeyTranslationKey(),
                options.rightKey.getBoundKeyTranslationKey(),
                options.jumpKey.getBoundKeyTranslationKey(),
                options.sneakKey.getBoundKeyTranslationKey(),
                options.sprintKey.getBoundKeyTranslationKey() */
                //?}
        );
        //?} else if >=1.16 {
        /*
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
         keybindsToFetch = Arrays.asList(
         options.keyForward.getDefaultKeyCode().toString(),
         options.keyBack.getDefaultKeyCode().toString(),
         options.keyLeft.getDefaultKeyCode().toString(),
         options.keyRight.getDefaultKeyCode().toString(),
         options.keyJump.getDefaultKeyCode().toString(),
         options.keySneak.getDefaultKeyCode().toString(),
         options.keySprint.getDefaultKeyCode().toString()
         );
         *///?}

        for (String key : keybindsToFetch) {
            //? if loader_forge && <=1.13.2 {
            if (key != null && !key.trim().isEmpty()) {
                friendlyNames.add(key.trim().toUpperCase().replace("CONTROL", "CTRL"));
            }
            //?} else if loader_neoforge || >=26.1 {
            String friendlyName = normalize26KeyName(key);
            if (friendlyName != null) {
                friendlyNames.add(friendlyName);
            }
            //?} else {
            if (key == null || !key.startsWith("key.keyboard."))
                continue;
            String[] parts = key.split("\\.");
            String legacyFriendlyName = "";
            if (parts.length == 4)
                legacyFriendlyName = (parts[2].substring(0, 1) + parts[3]).toUpperCase();
            else if (parts.length == 3)
                legacyFriendlyName = parts[2].toUpperCase();
            if (!legacyFriendlyName.isEmpty()) {
                legacyFriendlyName = legacyFriendlyName.replace("CONTROL", "CTRL");
                friendlyNames.add(legacyFriendlyName);
            }
            //?}
        }
        return friendlyNames;
    }

    private void paintChatPulseEffect(FrameStateDto state, PlayerDto player, long now) {
        RGBColorDto baseColor = resolveEnvironmentalBaseColor(player, now);
        RGBColorDto white = new RGBColorDto(255, 255, 255);

        float pulsePeriod = 0.6f;
        int pulseCount = 3;
        float totalPulseDuration = pulsePeriod * pulseCount;

        if (player.getIsChatReceived() && !chatPulseActive) {
            chatPulseActive = true;
            chatPulseStartTime = now;
        }

        if (!chatPulseActive) return;

        float elapsed = (now - chatPulseStartTime) / 1000f;
        if (elapsed > totalPulseDuration) {
            chatPulseActive = false;
            return;
        }

        float t = (float) ((Math.sin((elapsed / pulsePeriod) * Math.PI * 2) + 1) / 2);
        RGBColorDto pulseColor = lerpColor(baseColor, white, t);

        for (String keyName : getChatKeyNames()) {
            for (Integer ledId : getMappedIds(keyName)) {
                state.keys.put(ledId, pulseColor);
            }
        }
    }

    private List<String> getChatKeyNames() {
        List<String> friendlyNames = new ArrayList<>();
        //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        GameSettings options = MineLightsClient.getMinecraft().gameSettings;
        //?} else if loader_forge && <1.17 {
        GameSettings options = MineLightsClient.getMinecraft().options;
        //?} else if loader_neoforge || >=26.1 {
        Options options = MineLightsClient.getMinecraft().options;
        //?} else {
        /* GameOptions options = MinecraftClient.getInstance().options;
        *///?}
        String keybindToFetch = null;

        //? if loader_forge && <=1.8.9 {
        keybindToFetch = Keyboard.getKeyName(options.keyBindChat.getKeyCode());
        //?} else if loader_forge && <=1.11.2 {
        keybindToFetch = options.keyBindChat.getDisplayName();
        //?} else if loader_forge && <=1.13.2 {
        keybindToFetch = KeyBinding.getDisplayString(options.keyBindChat.getKeyDescription()).get();
        //?} else if loader_forge && <=1.14.3 {
        keybindToFetch = options.keyBindChat.getLocalizedName();
        //?} else if loader_forge && <=1.15.2 {
        keybindToFetch = options.keyChat.getTranslatedKeyMessage();
        //?} else if loader_forge && 1.16.1 {
        keybindToFetch = options.keyBindChat.getKey().getTranslationKey();
        //?} else if loader_forge || >= 1.19 {
        //? if loader_neoforge || >=26.1 {
        keybindToFetch = options.keyChat.getTranslatedKeyMessage().getString();
        //?} else {
        /* keybindToFetch = options.chatKey.getBoundKeyTranslationKey(); */
        //?}
        //?} else if >=1.16 && !loader_forge {
        /* keybindToFetch = options.keyChat.getBoundKeyTranslationKey();
        *///?} else {
        /* keybindToFetch = options.keyChat.getDefaultKeyCode().toString();
        *///?}

        //? if loader_forge && <=1.13.2 {
        if (keybindToFetch != null && !keybindToFetch.trim().isEmpty()) {
            friendlyNames.add(keybindToFetch.trim().toUpperCase().replace("CONTROL", "CTRL"));
        }
        //?} else if loader_neoforge || >=26.1 {
        String friendlyName = normalize26KeyName(keybindToFetch);
        if (friendlyName != null) {
            friendlyNames.add(friendlyName);
        }
        //?} else {
        if (keybindToFetch != null && keybindToFetch.startsWith("key.keyboard.")) {
            String[] parts = keybindToFetch.split("\\.");
            String legacyFriendlyName = "";
            if (parts.length == 4) {
                legacyFriendlyName = (parts[2].substring(0, 1) + parts[3]).toUpperCase();
            } else if (parts.length == 3) {
                legacyFriendlyName = parts[2].toUpperCase();
            }
            if (!legacyFriendlyName.isEmpty()) {
                legacyFriendlyName = legacyFriendlyName.replace("CONTROL", "CTRL");
                friendlyNames.add(legacyFriendlyName);
            }
        }
        //?}

        return friendlyNames;
    }

    //? if loader_neoforge || >=26.1 {
    private String normalize26KeyName(String keyName) {
        if (keyName == null || keyName.trim().isEmpty()) {
            return null;
        }

        String normalized = keyName.trim().toUpperCase().replace(' ', '_');
        switch (normalized) {
            case "LEFT_SHIFT":
                return "LSHIFT";
            case "RIGHT_SHIFT":
                return "RSHIFT";
            case "LEFT_CONTROL":
            case "LEFT_CTRL":
                return "LCTRL";
            case "RIGHT_CONTROL":
            case "RIGHT_CTRL":
                return "RCTRL";
            case "LEFT_ALT":
                return "LALT";
            case "RIGHT_ALT":
                return "RALT";
            case "LEFT_WINDOWS":
            case "LEFT_WIN":
            case "LEFT_SUPER":
                return "LWIN";
            case "UP_ARROW":
                return "UP";
            case "DOWN_ARROW":
                return "DOWN";
            case "LEFT_ARROW":
                return "LEFT";
            case "RIGHT_ARROW":
                return "RIGHT";
            default:
                return normalized;
        }
    }
    //?}

    private void updateRandomKeys(List<Integer> keyList, List<Integer> sourceList, float density) {
        keyList.clear();
        if (sourceList.isEmpty())
            return;
        int count = (int) (sourceList.size() * density);
        for (int i = 0; i < count; i++) {
            keyList.add(sourceList.get(random.nextInt(sourceList.size())));
        }
    }

    private static RGBColorDto color(int rgb) {
        return new RGBColorDto((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
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

    private float lerp(float start, float end, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return start + (end - start) * t;
    }
}
