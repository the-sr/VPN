package com;

import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) {

        try {

            ServerSocket serverSocket = new ServerSocket(5000);
            System.out.println("Socket Server stared, listening at port 5000, waiting for client...");
            Socket socket = serverSocket.accept();
            System.out.println("Client successfully connected: " + socket.getInetAddress());

        } catch (Exception e) {
            System.out.println("Error in server socket: " + e.getMessage());
        }

    }
}