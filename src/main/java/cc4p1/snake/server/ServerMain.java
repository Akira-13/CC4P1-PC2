package cc4p1.snake.server;

import cc4p1.snake.ui.GameWindow;
import cc4p1.snake.ui.GameWindowClient;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Entry point del servidor. Uso: java ServerMain --port 5000
 */
public class ServerMain {
    public static final int DEFAULT_PORT = 8000;
    private static int currentPort = DEFAULT_PORT;

    public static void main(String[] args) throws Exception {
        currentPort = DEFAULT_PORT;

        java.awt.EventQueue.invokeLater(() -> {
            GameWindow gw = new GameWindow();
            gw.setVisible(true);
        });
    }

    public static void startServer(int port) throws Exception {
        currentPort = port;
        System.out.println("Starting server on port " + port);
        GameServer server = new GameServer(port, 7); // 7 ticks por segundo
        server.start();
    }

    public static int getCurrentPort() {
        return currentPort;
    }

    public static void setCurrentPort(int port) {
        currentPort = port;
    }
}
