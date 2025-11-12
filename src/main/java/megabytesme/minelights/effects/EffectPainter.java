package megabytesme.minelights.effects;

import megabytesme.minelights.CompassState;
import megabytesme.minelights.CompassType;
import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.PlayerDto;
//? if >=1.21.8 {
import megabytesme.minelights.WaypointDto;
//?}
import net.minecraft.client.MinecraftClient;
//? if >=1.17 {
import net.minecraft.client.option.GameOptions;
//?} else {
/* import net.minecraft.client.options.GameOptions;
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
        if (MineLightsClient.CONFIG.enableExperienceBar)
            paintExperienceBar(state, player);
        //? if >=1.21.8 {
        if (MineLightsClient.CONFIG.enableLocatorBar) {
            paintLocatorBar(state, player);
        }
        //?}
        paintPlayerBars(state, player);
        if (MineLightsClient.CONFIG.enableSaturationBar)
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
            if (relevantBlocks.contains(blockId)) {
                return true;
            }
        }
        
        return false;
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
            isSoulFire = isPlayerInOrOnBlock(player, "block.minecraft.soul_fire", "block.minecraft.soul_sand");
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
            isSoulFire = isPlayerInOrOnBlock(player, "block.minecraft.soul_fire", "block.minecraft.soul_sand");
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

            if (drop.position >= 0 && drop.position < ramKeys.size()) {
                ramKeyColors.put(ramKeys.get(drop.position), drop.color);
            }
            int tail1Pos = drop.position - 1;
            if (tail1Pos >= 0 && tail1Pos < ramKeys.size()) {
                ramKeyColors.putIfAbsent(ramKeys.get(tail1Pos), drop.tailColor);
            }
            int tail2Pos = drop.position - 2;
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
        int ledsToLight = (int) (player.getExperience() * logicalKeys.size());
        for (int i = 0; i < logicalKeys.size(); i++) {
            RGBColorDto color = (i < ledsToLight) ? new RGBColorDto(0, 255, 0) : new RGBColorDto(10, 30, 10);
            for (Integer ledId : getMappedIds(logicalKeys.get(i)))
                state.keys.put(ledId, color);
        }
    }

    //? if >=1.21.8 {
    private void paintLocatorBar(FrameStateDto state, PlayerDto player) {
        if (player.getWaypoints() == null || player.getWaypoints().isEmpty()) {
            return;
        }

        List<String> locatorBarKeys = KeyMap.getExperienceBar();
        if (locatorBarKeys.isEmpty()) {
            return;
        }

        int numKeys = locatorBarKeys.size();
        float viewAngle = 60.0f;

        for (WaypointDto waypoint : player.getWaypoints()) {
            double yaw = waypoint.getRelativeYaw();

            if (yaw > -viewAngle && yaw <= viewAngle) {
                double normalizedPosition = (yaw + viewAngle) / (viewAngle * 2.0);
                int keyIndex = (int) (normalizedPosition * numKeys);
                keyIndex = Math.max(0, Math.min(numKeys - 1, keyIndex));

                for (Integer ledId : getMappedIds(locatorBarKeys.get(keyIndex))) {
                    int packedColor = waypoint.getColor();
                    int r = (packedColor >> 16) & 0xFF;
                    int g = (packedColor >> 8) & 0xFF;
                    int b = packedColor & 0xFF;
                    RGBColorDto waypointColor = new RGBColorDto(r, g, b);

                    if (waypoint.getPitch() == net.minecraft.world.waypoint.TrackedWaypoint.Pitch.UP) {
                        waypointColor = lerpColor(waypointColor, new RGBColorDto(255, 255, 255), 0.5f);
                    } else if (waypoint.getPitch() == net.minecraft.world.waypoint.TrackedWaypoint.Pitch.DOWN) {
                        waypointColor = lerpColor(waypointColor, new RGBColorDto(0, 0, 0), 0.5f);
                    }

                    state.keys.put(ledId, waypointColor);
                }
            }
        }
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
            RGBColorDto healthDim = new RGBColorDto(30, 0, 0);
            RGBColorDto healthFull = new RGBColorDto(255, 0, 0);
            List<String> healthKeys = KeyMap.getHealthBar();
            for (int i = 0; i < healthKeys.size(); i++) {
                RGBColorDto finalColor;
                if (isDamageFlashActive)
                    finalColor = new RGBColorDto(255, 255, 255);
                else if (player.getIsWithering())
                    finalColor = new RGBColorDto(43, 43, 43);
                else if (player.getIsPoisoned())
                    finalColor = new RGBColorDto(148, 120, 24);
                else {
                    float t = (player.getHealth() - (i * 5.0f)) / 5.0f;
                    finalColor = lerpColor(healthDim, healthFull, t);
                }
                for (Integer ledId : getMappedIds(healthKeys.get(i)))
                    state.keys.put(ledId, finalColor);
            }
        }

        if (MineLightsClient.CONFIG.enableHungerBar) {
            RGBColorDto hungerDim = new RGBColorDto(30, 15, 0);
            RGBColorDto hungerFull = new RGBColorDto(255, 165, 0);
            List<String> hungerKeys = KeyMap.getHungerBar();
            for (int i = 0; i < hungerKeys.size(); i++) {
                float t = (player.getHunger() - (i * 5.0f)) / 5.0f;
                RGBColorDto color = lerpColor(hungerDim, hungerFull, t);
                for (Integer ledId : getMappedIds(hungerKeys.get(i)))
                    state.keys.put(ledId, color);
            }
        }
    }

    private void paintSaturationAndAirBar(FrameStateDto state, PlayerDto player) {
        List<String> barKeys = KeyMap.getSaturationBar();
        if (isPlayerInOrOnBlock(player, "block.minecraft.water"))
            lastInWaterTime = System.currentTimeMillis();
        boolean showAirBar = isPlayerInOrOnBlock(player, "block.minecraft.water")
                || (System.currentTimeMillis() - lastInWaterTime < AIR_BAR_VISIBLE_DURATION_MS);

        float value, maxValue;
        RGBColorDto fullColor, dimColor;

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
            float t = (value - (i * valuePerKey)) / valuePerKey;
            RGBColorDto color = lerpColor(dimColor, fullColor, t);
            for (Integer ledId : getMappedIds(barKeys.get(i)))
                state.keys.put(ledId, color);
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
            for (Integer ledId : getMappedIds(keyName))
                state.keys.put(ledId, new RGBColorDto(r, 0, 0));
        }
        for (String keyName : KeyMap.getHeartbeatWhite()) {
            for (Integer ledId : getMappedIds(keyName))
                state.keys.put(ledId, new RGBColorDto(r, r, r));
        }
    }

    private void paintCompass(FrameStateDto state, PlayerDto player) {
        if (player.getCompassType() == CompassType.NONE)
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

        for (String keyName : KeyMap.getNumpadDirectional()) {
            for (Integer ledId : getMappedIds(keyName))
                state.keys.put(ledId, backgroundColor);
        }
        for (Integer ledId : getMappedIds(KeyMap.getNumpadCenter())) {
            state.keys.put(ledId, compassColor);
        }

        if (player.getCompassState() == CompassState.SPINNING) {
            long now = System.currentTimeMillis();
            if (now - lastCompassSpinTime > 75) {
                compassSpinIndex = (compassSpinIndex + 1) % KeyMap.getNumpadDirectional().size();
                lastCompassSpinTime = now;
            }
            String keyToLight = KeyMap.getNumpadDirectional().get(compassSpinIndex);
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
                String keyToLight;
                if (relativeYaw >= -22.5 && relativeYaw < 22.5)
                    keyToLight = "NUMPAD8";
                else if (relativeYaw >= 22.5 && relativeYaw < 67.5)
                    keyToLight = "NUMPAD9";
                else if (relativeYaw >= 67.5 && relativeYaw < 112.5)
                    keyToLight = "NUMPAD6";
                else if (relativeYaw >= 112.5 && relativeYaw < 157.5)
                    keyToLight = "NUMPAD3";
                else if (relativeYaw >= 157.5 || relativeYaw < -157.5)
                    keyToLight = "NUMPAD2";
                else if (relativeYaw >= -157.5 && relativeYaw < -112.5)
                    keyToLight = "NUMPAD1";
                else if (relativeYaw >= -112.5 && relativeYaw < -67.5)
                    keyToLight = "NUMPAD4";
                else
                    keyToLight = "NUMPAD7";
                for (Integer ledId : getMappedIds(keyToLight))
                    state.keys.put(ledId, compassColor);
            }
        }

        if (player.getCompassType() == CompassType.LODESTONE) {
            long now = System.currentTimeMillis();
            if (now - lastSheenTime > 60) {
                sheenIndex = (sheenIndex + 1) % KeyMap.getNumpadDirectional().size();
                lastSheenTime = now;
            }
            List<String> directionalKeys = KeyMap.getNumpadDirectional();
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
                && (isPlayerInOrOnBlock(player, "block.minecraft.lava", "block.minecraft.fire", "block.minecraft.soul_fire", "block.minecraft.soul_sand"))) {
                    boolean isSoulFire = false;
                    if (player.getIsOnFire()) {
                        isSoulFire = isPlayerInOrOnBlock(player, "block.minecraft.soul_fire", "block.minecraft.soul_sand");
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
        GameOptions options = MinecraftClient.getInstance().options;
        // ? if >= 1.19 {
        keybindsToFetch = Arrays.asList(
                options.forwardKey.getBoundKeyTranslationKey(),
                options.backKey.getBoundKeyTranslationKey(),
                options.leftKey.getBoundKeyTranslationKey(),
                options.rightKey.getBoundKeyTranslationKey(),
                options.jumpKey.getBoundKeyTranslationKey(),
                options.sneakKey.getBoundKeyTranslationKey(),
                options.sprintKey.getBoundKeyTranslationKey());
        // ?} else if >=1.16 {
        /*
         keybindsToFetch = Arrays.asList(
         options.keyForward.getBoundKeyTranslationKey(),
         options.keyBack.getBoundKeyTranslationKey(),
         options.keyLeft.getBoundKeyTranslationKey(),
         options.keyRight.getBoundKeyTranslationKey(),
         options.keyJump.getBoundKeyTranslationKey(),
         options.keySneak.getBoundKeyTranslationKey(),
         options.keySprint.getBoundKeyTranslationKey());
         */// ?} else {
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
         */// ?}

        for (String key : keybindsToFetch) {
            if (key == null || !key.startsWith("key.keyboard."))
                continue;
            String[] parts = key.split("\\.");
            String friendlyName = "";
            if (parts.length == 4)
                friendlyName = (parts[2].substring(0, 1) + parts[3]).toUpperCase();
            else if (parts.length == 3)
                friendlyName = parts[2].toUpperCase();
            if (!friendlyName.isEmpty()) {
                friendlyName = friendlyName.replace("CONTROL", "CTRL");
                friendlyNames.add(friendlyName);
            }
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
        GameOptions options = MinecraftClient.getInstance().options;
        String keybindToFetch = null;

        //? if >= 1.19 {
        keybindToFetch = options.chatKey.getBoundKeyTranslationKey();
        //?} else if >=1.16 {
        /* keybindToFetch = options.keyChat.getBoundKeyTranslationKey();
        *///?} else {
        /* keybindToFetch = options.keyChat.getDefaultKeyCode().toString();
        *///?}

        if (keybindToFetch != null && keybindToFetch.startsWith("key.keyboard.")) {
            String[] parts = keybindToFetch.split("\\.");
            String friendlyName = "";
            if (parts.length == 4) {
                friendlyName = (parts[2].substring(0, 1) + parts[3]).toUpperCase();
            } else if (parts.length == 3) {
                friendlyName = parts[2].toUpperCase();
            }
            if (!friendlyName.isEmpty()) {
                friendlyName = friendlyName.replace("CONTROL", "CTRL");
                friendlyNames.add(friendlyName);
            }
        }

        return friendlyNames;
    }

    private void updateRandomKeys(List<Integer> keyList, List<Integer> sourceList, float density) {
        keyList.clear();
        if (sourceList.isEmpty())
            return;
        int count = (int) (sourceList.size() * density);
        for (int i = 0; i < count; i++) {
            keyList.add(sourceList.get(random.nextInt(sourceList.size())));
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

    private float lerp(float start, float end, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return start + (end - start) * t;
    }
}