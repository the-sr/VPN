package com;

import java.io.InputStream;
import java.net.Socket;

public class Reader implements Runnable {

    private final InputStream inputStream;

    public Reader(Socket socket) throws Exception {
        this.inputStream = socket.getInputStream();
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        int bytesRead;
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                String received = new String(buffer, 0, bytesRead);
                System.out.println("\nServer: " + received);
            }
        } catch (Exception e) {
            System.out.println("Disconnected from server.");
        }
    }
}
