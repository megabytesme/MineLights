package megabytesme.minelights.effects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceLayout {
    public final String deviceName;
    public final String deviceType;
    private final Map<String, List<Integer>> keyMap = new HashMap<>();
    private final List<Integer> allLeds = new ArrayList<>();

    public DeviceLayout(String deviceName, String deviceType) {
        this.deviceName = deviceName;
        this.deviceType = deviceType;
    }

    public void addMapping(String keyName, int ledId) {
        keyMap.computeIfAbsent(keyName, k -> new ArrayList<>()).add(ledId);
        if (!allLeds.contains(ledId)) {
            allLeds.add(ledId);
        }
    }

    public Map<String, List<Integer>> getKeyMap() {
        return keyMap;
    }

    public List<Integer> getAllLeds() {
        return allLeds;
    }
}