package com;

import com.tun.TunDevice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * Entry point for the client. It brings up tun0, redirects all outbound IPv4 traffic
 * into it, and forwards every captured packet to the server as one UDP datagram.
 *
 * UDP suits the data plane because a captured IP packet is already a message: one
 * packet maps to one datagram, so there is no framing to invent, and the transport
 * never retransmits alongside the retransmission the packets already carry inside them.
 *
 * Printing happens on the server; the client only forwards, keeping a counter as proof
 * it is alive. Encrypting these packets is the next phase.
 *
 * Run as root - creating the interface and installing routes both need CAP_NET_ADMIN:
 *   sudo $(which java) --enable-native-access=ALL-UNNAMED \
 *        -cp build/libs/Client-1.0-SNAPSHOT.jar com.Client
 */
public class Client {

  private static final String SERVER_HOST = "127.0.0.1";
  private static final int SERVER_PORT = 5000;

  /** Reporting every packet would drown the output, so progress shows up in batches. */
  private static final int COUNTER_INTERVAL = 100;

  public static void main(String[] args) throws Exception {

    TunDevice tun = new TunDevice("tun0");

    // Routes normally die with the process anyway, but closing explicitly makes teardown
    // deterministic instead of relying on the kernel reclaiming the fd.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        System.out.println("Shutting down - closing tun0 (its routes go with it)");
        tun.close();
      } catch (Exception ignored) {
        // Harmless: the kernel reclaims the descriptor on exit regardless.
      }
    }));

    // Bring the interface up before sending anything to it - a DOWN interface with no
    // address would silently blackhole traffic instead of yielding it to read().
    run("ip", "addr", "flush", "dev", "tun0");
    run("ip", "addr", "add", "10.0.0.1/24", "dev", "tun0");
    run("ip", "link", "set", "tun0", "up");

    // tun0's default MTU of 1500 would let the kernel hand over a packet that, once
    // wrapped in UDP (8 bytes) and an outer IP header (20 bytes), exceeds the path MTU
    // and gets IP-fragmented - slow when it works and silently dropped through NAT when
    // it doesn't. Shrinking the interface keeps every forwarded datagram to one frame.
    // The value leaves headroom for the crypto overhead added in the next phase.
    run("ip", "link", "set", "tun0", "mtu", "1400");

    // Binding a local port is the entire "connection" UDP has: no handshake happens and
    // nothing reaches the server until a datagram is sent. The server runs on loopback,
    // which the kernel routes over `lo` regardless of the default route, so the hijack
    // below cannot take down the tunnel that carries the captured packets.
    DatagramSocket out = new DatagramSocket();
    InetSocketAddress server = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
    System.out.println("Forwarding captured packets to " + server);

    // These two /1 routes are more specific than the 0.0.0.0/0 default, so together
    // they catch every non-local IPv4 destination and the machine's internet stops
    // working until this process exits.
    run("ip", "route", "add", "0.0.0.0/1", "dev", "tun0");
    run("ip", "route", "add", "128.0.0.0/1", "dev", "tun0");

    Thread.startVirtualThread(() -> capture(tun, out, server));
    Thread.currentThread().join();
  }

  /**
   * Reads packets as the kernel routes them into tun0 and ships each one to the server.
   * A single read already returns one whole packet, so one packet becomes one datagram
   * with no framing of our own.
   */
  private static void capture(TunDevice tun, DatagramSocket out, InetSocketAddress server) {
    byte[] buf = new byte[65535];
    long forwarded = 0;
    long bytes = 0;
    try {
      while (true) {
        int n = tun.read(buf);
        if (n <= 0) break;

        out.send(new DatagramPacket(buf, n, server));
        forwarded++;
        bytes += n;

        // Fire-and-forget means a server that is down never surfaces as a send error,
        // so the counter is the only indication that anything is happening.
        if (forwarded % COUNTER_INTERVAL == 0) {
          System.out.println("forwarded " + forwarded + " packets, " + bytes + " bytes");
        }
      }
      System.out.println("tun0 closed - forwarded " + forwarded + " packets, " + bytes + " bytes");
    } catch (Exception e) {
      System.out.println("Capture stopped: " + e.getMessage());
    }
  }

  /** Runs an external command, inheriting its output so `ip` errors reach the console. */
  private static void run(String... cmd) throws Exception {
    int code = new ProcessBuilder(cmd).inheritIO().start().waitFor();
    if (code != 0) {
      throw new RuntimeException("command failed (" + code + "): " + String.join(" ", cmd));
    }
  }
}
