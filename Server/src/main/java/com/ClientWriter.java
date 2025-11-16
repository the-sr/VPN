package com;

import java.io.OutputStream;
import java.net.Socket;

public class ClientWriter implements Runnable {
    private final OutputStream outputStream;

    public ClientWriter(Socket socket) throws Exception {
        this.outputStream = socket.getOutputStream();
    }

    @Override
    public void run() {
        try {
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.out.println("Error writing to client: " + e.getMessage());
        }
    }
}
