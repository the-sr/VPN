package com;

import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Writer implements Runnable {
    private final OutputStream outputStream;

    public Writer(Socket socket) throws Exception {
        this.outputStream = socket.getOutputStream();
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        try {
            while (true) {
                System.out.print("Enter message: ");
                System.out.flush();
                String msg = scanner.nextLine();
                if (msg.equalsIgnoreCase("exit"))
                    break;

                byte[] data = msg.getBytes();
                outputStream.write(data);
            }
        } catch (Exception e) {
            System.out.println("Error sending message: " + e.getMessage());
        }
    }
}
