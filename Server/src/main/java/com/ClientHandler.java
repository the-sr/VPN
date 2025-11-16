package com;

import java.net.Socket;

public class ClientHandler {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void start() {
        try {
            Thread.startVirtualThread(new ClientWriter(socket));
            Thread.startVirtualThread(new ClientReader(socket));
        } catch (Exception e) {
            System.out.println("Error starting client handler: " + e.getMessage());
        }
    }
}
