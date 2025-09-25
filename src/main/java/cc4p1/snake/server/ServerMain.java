package cc4p1.snake.server;

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
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }
        System.out.println("Starting server on port " + port);
        GameServer server = new GameServer(port, 10); // 10 ticks por segundo
        server.start();
    }
}
