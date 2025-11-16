package com;

import java.io.InputStream;
import java.net.Socket;

public class ClientReader implements Runnable {
    private final InputStream inputStream;

    public ClientReader(Socket socket) throws Exception {
        this.inputStream = socket.getInputStream();
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        int bytesRead;

        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                System.out.println("Received from client: " + new String(buffer, 0, bytesRead));
            }
        } catch (Exception e) {
            System.out.println("Client disconnected.");
        }
    }
}
