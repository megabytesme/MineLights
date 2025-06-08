package megabytesme.minelights.rgb;

import io.gitlab.mguimard.openrgb.client.OpenRGBClient;
import io.gitlab.mguimard.openrgb.entity.OpenRGBColor;
import io.gitlab.mguimard.openrgb.entity.OpenRGBDevice;
import megabytesme.minelights.effects.KeyColorDto;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenRGBController {
    private OpenRGBClient client;
    private final List<OpenRGBDevice> devices = new ArrayList<>();

    public boolean connect() {
        try {
            client = new OpenRGBClient("127.0.0.1", 6742, "MineLights");
            client.connect();
            int controllerCount = client.getControllerCount();
            for (int i = 0; i < controllerCount; i++) {
                devices.add(client.getDeviceController(i));
            }
            return true;
        } catch (IOException e) {
            System.err.println("Could not connect to OpenRGB server. Is it running?");
            return false;
        }
    }

    public List<OpenRGBDevice> getDevices() {
        return devices;
    }

    public void updateLeds(Map<Integer, List<KeyColorDto>> updates) {
        if (!isConnected())
            return;

        for (Map.Entry<Integer, List<KeyColorDto>> entry : updates.entrySet()) {
            int deviceId = entry.getKey();
            List<KeyColorDto> colors = entry.getValue();

            OpenRGBColor[] ledColors = devices.get(deviceId).getColors().toArray(new OpenRGBColor[0]);
            for (KeyColorDto dto : colors) {
                int localLedId = dto.id - getLedOffsetForDevice(deviceId);
                if (localLedId >= 0 && localLedId < ledColors.length) {
                    ledColors[localLedId] = new OpenRGBColor(dto.r, dto.g, dto.b);
                }
            }

            try {
                client.updateLeds(deviceId, ledColors);
            } catch (IOException e) {
                // Ignore send errors
            }
        }
    }

    public void disconnect() {
        if (isConnected()) {
            try {
                client.disconnect();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private int getLedOffsetForDevice(int deviceIndex) {
        int offset = 2000;
        for (int i = 0; i < deviceIndex; i++) {
            offset += devices.get(i).getLeds().size();
        }
        return offset;
    }

    public boolean isConnected() {
        if (client == null) {
            return false;
        }
        try {
            Field socketField = OpenRGBClient.class.getDeclaredField("socket");
            socketField.setAccessible(true);
            Socket socket = (Socket) socketField.get(client);
            return socket != null && !socket.isClosed();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return false;
        }
    }
}