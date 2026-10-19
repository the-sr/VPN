package com;

import com.config.Config;
import com.crypto.PacketCipher;
import com.crypto.ReplayWindow;
import com.vpn.ReceiveService;

import java.net.DatagramSocket;

/**
 * Listens for the client's encrypted packets and decrypts each one onto the console.
 *
 * The server is stateless about its peer: UDP tells it nothing about who is sending, so
 * the client address can only be learned from a received datagram. That matters as soon
 * as the return path is built.
 *
 * Run with the same crypto.key.base64 as the client, or every packet will fail its tag.
 */
public class Server {

  public static void main(String[] args) throws Exception {
    Config config = Config.load();

    // Binding the port makes this a fixed mailbox for the life of the process, and
    // receive() below blocks until a client sends something - no handshake is exchanged.
    try (DatagramSocket socket = new DatagramSocket(config.listenPort())) {
      System.out.println("listening for encrypted packets on UDP port " + config.listenPort());

      new ReceiveService(socket, new PacketCipher(config.cryptoKey()),
          new ReplayWindow(config.replayWindow())).run();
    }
  }
}
