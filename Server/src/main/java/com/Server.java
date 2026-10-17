package com;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Listens for the client on port 5000 and holds each connection open.
 *
 * Nothing is done with the connection yet - the server only proves the socket link
 * works. The next phase switches transport to gRPC, at which point these connections
 * carry encrypted packets from the client down to the network.
 */
public class Server {

  public static void main(String[] args) {

    // Accepted sockets are stored here: a Socket that nothing references can be
    // garbage collected, which closes the connection underneath us. This list keeps
    // the references alive for as long as the server runs.
    List<Socket> clients = new ArrayList<>();

    try (ServerSocket serverSocket = new ServerSocket(5000)) {
      System.out.println("Listening on port 5000...");

      while (true) {
        Socket client = serverSocket.accept();
        clients.add(client);
        System.out.println("Client connected: " + client.getInetAddress());
      }
    } catch (Exception e) {
      System.out.println("Server error: " + e.getMessage());
    }
  }
}
