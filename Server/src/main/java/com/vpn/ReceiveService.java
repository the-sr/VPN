package com.vpn;

import com.crypto.PacketCipher;
import com.crypto.ReplayWindow;
import com.net.PacketPrinter;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * The server's loop: take a datagram, authenticate it, reject replays, decrypt it, print
 * it.
 *
 * Order matters. The tag is verified before the replay window is consulted, so only a
 * packet that has proven possession of the key can move the window - otherwise anyone
 * who can reach the port could poison it with a high counter and get every legitimate
 * packet rejected.
 */
public final class ReceiveService implements Runnable {

  /** Sized for a full datagram, so no packet can be truncated by the receive call. */
  private static final int BUFFER_SIZE = 65535;

  /** Stats are reported every N packets; printing them per packet would swamp the output. */
  private static final long STATS_INTERVAL = 1000;

  private final DatagramSocket socket;
  private final PacketCipher cipher;
  private final ReplayWindow replayWindow;

  public ReceiveService(DatagramSocket socket, PacketCipher cipher, ReplayWindow replayWindow) {
    this.socket = socket;
    this.cipher = cipher;
    this.replayWindow = replayWindow;
  }

  @Override
  public void run() {
    byte[] buf = new byte[BUFFER_SIZE];
    long received = 0;
    long ok = 0;
    long dropped = 0;
    try {
      while (true) {
        DatagramPacket datagram = new DatagramPacket(buf, buf.length);
        socket.receive(datagram);
        received++;

        byte[] plain = cipher.decrypt(datagram.getData(), datagram.getLength());
        if (plain == null) {
          dropped++; // bad tag: tampered, wrong key, or truncated
          continue;
        }

        if (!replayWindow.accept(PacketCipher.counterOf(datagram.getData()))) {
          dropped++; // a duplicate or one that fell out of the window
          continue;
        }

        PacketPrinter.print(plain, plain.length);
        ok++;

        // Watching `dropped` stay at zero is the clearest proof the tunnel is healthy.
        if (ok % STATS_INTERVAL == 0) {
          System.out.println("received " + received + ", ok " + ok + ", dropped " + dropped);
        }
      }
    } catch (Exception e) {
      System.out.println("receive stopped: " + e.getMessage());
    }
  }
}
