package minelights.minelights;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPClient {
    public static void sendPlayerData(String playerDataJson) {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName("localhost");
            byte[] buffer = playerDataJson.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 63212);
            socket.send(packet);
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}