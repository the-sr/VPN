package com;

import com.tun.PacketPrinter;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Listens on UDP port 5000 and prints every packet the client forwards.
 *
 * The server is stateless: UDP tells it nothing about who is sending, so it just
 * decodes whatever datagram turns up. The flip side is that it can only reply after a
 * client has spoken first - the peer address has to be taken from a received packet
 * instead of learned from a connection. That matters as soon as the return path is built.
 *
 * Printing here is temporary output; encrypting the packets and forwarding them out to
 * the internet are the following phases.
 */
public class Server {

  /** Sized for a full IPv4 datagram, so no packet can be truncated by the receive call. */
  private static final int BUFFER_SIZE = 65535;

  public static void main(String[] args) throws Exception {

    // Binding port 5000 makes this a fixed mailbox for the life of the process, and
    // receive() then blocks until a client sends something - no handshake is exchanged
    // and nothing about the client is retained between datagrams.
    try (DatagramSocket socket = new DatagramSocket(5000)) {
      System.out.println("Listening for packets on UDP port 5000...");

      byte[] buf = new byte[BUFFER_SIZE];
      while (true) {
        DatagramPacket datagram = new DatagramPacket(buf, buf.length);
        socket.receive(datagram);
        PacketPrinter.print(datagram.getData(), datagram.getLength());
      }
    }
  }
}
