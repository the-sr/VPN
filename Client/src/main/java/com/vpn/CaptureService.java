package com.vpn;

import com.crypto.PacketCipher;
import com.tun.TunDevice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * The client's hot loop: pull a packet off the tunnel, encrypt it, send it as one UDP
 * datagram.
 *
 * A single tun read already returns one whole packet, so one packet becomes one datagram
 * with no framing of our own - the main reason UDP was chosen over a stream transport.
 */
public final class CaptureService implements Runnable {

  private final TunDevice tun;
  private final PacketCipher cipher;
  private final DatagramSocket socket;
  private final InetSocketAddress server;
  private final int progressInterval;

  public CaptureService(TunDevice tun, PacketCipher cipher, DatagramSocket socket,
      InetSocketAddress server, int progressInterval) {
    this.tun = tun;
    this.cipher = cipher;
    this.socket = socket;
    this.server = server;
    this.progressInterval = progressInterval;
  }

  @Override
  public void run() {
    byte[] buf = new byte[65535];
    long forwarded = 0;
    long bytes = 0;
    try {
      while (true) {
        int n = tun.read(buf);
        if (n <= 0) break;

        byte[] wire = cipher.encrypt(buf, n);
        socket.send(new DatagramPacket(wire, wire.length, server));

        forwarded++;
        bytes += n;

        // Sending is fire-and-forget, so a server that is down never surfaces as an
        // error. The counter is the only sign the client is doing anything at all.
        if (progressInterval > 0 && forwarded % progressInterval == 0) {
          System.out.println("forwarded " + forwarded + " packets, " + bytes + " bytes");
        }
      }
      System.out.println("tun closed - forwarded " + forwarded + " packets, " + bytes + " bytes");
    } catch (Exception e) {
      System.out.println("capture stopped: " + e.getMessage());
    }
  }
}
