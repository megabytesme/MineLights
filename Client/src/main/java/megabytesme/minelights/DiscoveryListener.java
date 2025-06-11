package megabytesme.minelights;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class DiscoveryListener implements Runnable {
    private static final int DISCOVERY_PORT = 63214;

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            MineLightsClient.LOGGER.info("Discovery listener started on port {}.", DISCOVERY_PORT);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    if ("MINELIGHTS_PROXY_HELLO".equals(message)) {
                        if (MineLightsClient.proxyDiscoveredLatch.getCount() > 0) {
                            MineLightsClient.LOGGER
                                    .info("Discovered MineLights Server via broadcast. Releasing startup latch.");
                            MineLightsClient.proxyDiscoveredLatch.countDown();
                        }
                    }
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted())
                        break;
                }
            }
        } catch (Exception e) {
            MineLightsClient.LOGGER.error(
                    "Could not start discovery listener on port {}. Auto-discovery will be disabled.", DISCOVERY_PORT,
                    e);
        }
        MineLightsClient.LOGGER.info("Discovery listener stopped.");
    }
}