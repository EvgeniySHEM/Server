package ru.avalon.javapp.j130.server;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class Server extends Thread {
    public static final int SERVER_PORT = 45678;
    public static final String SERVER_ADDRESS = "localhost";
    private ServerSocket serverSocket;


    public Server() throws IOException {
        this.serverSocket = new ServerSocket(SERVER_PORT);
    }

    @Override
    public void run() {
        System.out.println("Server started");
        while (true) {
            try {
                Socket client = serverSocket.accept();
                System.out.println("accept");
                ClientSocket clientSocket = new ClientSocket(client);
                clientSocket.start();
                System.out.println("start " + clientSocket.getName());
            } catch (IOException e) {
            } catch (IllegalArgumentException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.start();
    }
}
