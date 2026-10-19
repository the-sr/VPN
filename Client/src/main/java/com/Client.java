package com;

import com.config.Config;
import com.crypto.PacketCipher;
import com.tun.TunDevice;
import com.tun.TunSetup;
import com.vpn.CaptureService;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * Brings up the tunnel, points outbound IPv4 traffic at it, and forwards every captured
 * packet to the server encrypted as a single UDP datagram.
 *
 * Run as root - creating the interface and installing routes both need CAP_NET_ADMIN:
 *   sudo $(which java) --enable-native-access=ALL-UNNAMED \
 *        -cp build/libs/Client-1.0-SNAPSHOT.jar com.Client
 */
public class Client {

  public static void main(String[] args) throws Exception {
    Config config = Config.load();

    TunDevice tun = new TunSetup(config).bringUp();
    PacketCipher cipher = new PacketCipher(config.cryptoKey());
    DatagramSocket socket = new DatagramSocket();
    InetSocketAddress server = new InetSocketAddress(config.serverHost(), config.serverPort());

    // Routes normally die with the process anyway, but closing explicitly makes teardown
    // deterministic instead of relying on the kernel reclaiming the descriptor.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        System.out.println("shutting down - closing tun (its routes go with it)");
        tun.close();
      } catch (Exception ignored) {
        // Harmless: the kernel reclaims the descriptor on exit regardless.
      }
    }));

    System.out.println("forwarding encrypted packets to " + server);

    Thread.startVirtualThread(
        new CaptureService(tun, cipher, socket, server, config.progressInterval()));
    Thread.currentThread().join();
  }
}
