package com;

import com.tun.PacketPrinter;
import com.tun.TunDevice;

import java.net.Socket;

/**
 * Entry point for the client. It brings up tun0, connects to the server, redirects
 * all outbound IPv4 traffic into tun0, and prints every packet it catches.
 *
 * Printing is a stand-in for the real work: in the next phase each captured packet
 * will be encrypted and shipped to the server over this socket instead of to the
 * console.
 *
 * Run as root - creating the interface and installing routes both need CAP_NET_ADMIN:
 *   sudo $(which java) --enable-native-access=ALL-UNNAMED \
 *        -cp build/libs/Client-1.0-SNAPSHOT.jar com.Client
 */
public class Client {

  private static final String SERVER_HOST = "127.0.0.1";
  private static final int SERVER_PORT = 5000;

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

    // Connect before hijacking the default route. Loopback traffic uses lo rather than
    // the default route so it would survive either way, but connecting first also means
    // a failed handshake cannot be masked by the hijack.
    Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
    System.out.println("Connected to " + socket.getRemoteSocketAddress());
    System.out.println("Intercepting all outbound IPv4 traffic...");

    // These two /1 routes are more specific than the 0.0.0.0/0 default, so together
    // they catch every non-local IPv4 destination and the machine's internet stops
    // working until this process exits.
    run("ip", "route", "add", "0.0.0.0/1", "dev", "tun0");
    run("ip", "route", "add", "128.0.0.0/1", "dev", "tun0");

    Thread.startVirtualThread(() -> capture(tun));
    Thread.currentThread().join();
  }

  /**
   * Reads packets as the kernel routes them into tun0 and prints each one. A single
   * read returns one whole packet, so the loop needs no framing of its own.
   */
  private static void capture(TunDevice tun) {
    byte[] buf = new byte[65535];
    try {
      while (true) {
        int n = tun.read(buf);
        if (n <= 0) break;
        PacketPrinter.print(buf, n);
      }
      System.out.println("tun0 closed - capture finished.");
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
