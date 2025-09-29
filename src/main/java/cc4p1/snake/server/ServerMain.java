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
    public static void main(String[] args) throws Exception {
        int port = 5000;

        java.awt.EventQueue.invokeLater(() -> {
            GameWindow gw = new GameWindow();
            gw.setVisible(true);
        });
    }
    
    public static void startServer(int port) throws Exception{
        System.out.println("Starting server on port " + port);
        GameServer server = new GameServer(port, 10); // 10 ticks por segundo
        server.start();
       
    }
}
