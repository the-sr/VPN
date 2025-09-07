package com;

import java.net.Socket;

public class Client {
    public static void main(String[] args) {

        try {

            Socket socket = new Socket("127.0.0.1", 5000);
            System.out.println("Connected successfully");

        } catch (Exception e) {
            System.out.println("Error in socket connection: " + e.getMessage());
        }

    }
}