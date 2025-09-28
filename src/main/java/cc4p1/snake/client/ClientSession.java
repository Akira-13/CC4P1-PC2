package cc4p1.snake.client;

import cc4p1.snake.server.GameServer;
import java.io.*;
import java.net.*;

/**
 * Maneja I/O por socket para un jugador. - Lee líneas del cliente
 * (JOIN/INPUT/QUIT) - Expone get/consume de la última dirección para que el
 * GameServer la use en el tick
 */
public class ClientSession {

    private final int playerId;
    private final Socket socket;
    private final GameServer server;
    private final PrintWriter out;
    private volatile String lastDir = "RIGHT"; // dirección por defecto
    private volatile boolean running = true;

    public ClientSession(int playerId, Socket socket, GameServer server) throws IOException {
        this.playerId = playerId;
        this.socket = socket;
        this.server = server;
        this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setLastDirection(String d) {
        if (d == null) {
            return;
        }
        d = d.trim().toUpperCase();
        if (d.equals("UP") || d.equals("DOWN") || d.equals("LEFT") || d.equals("RIGHT")) {
            lastDir = d;
        }
    }

    public String consumeLastDirection() {
        return lastDir;
    }

    public void send(String line) {
        // println añade '\n' y hace flush (PrintWriter autoflush con println)
        out.print(line);
        out.flush();
    }

    public void closeSilently() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public void start() {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String line;
                while (running && (line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (line.startsWith("JOIN ")) {
                        String name = line.substring(5).trim();
                        server.onJoin(playerId, name);
                    } else if (line.startsWith("INPUT ")) {
                        String dir = line.substring(6).trim();
                        server.onInput(playerId, dir);
                    } else if (line.equals("QUIT")) {
                        server.onQuit(playerId);
                        break;
                    } else {
                        // mensaje desconocido, se puede ignorar o loggear
                        send("ERR Unknown command\n");
                    }
                }
            } catch (IOException e) {
                // probable desconexión del cliente
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                server.onQuit(playerId);
            }
        }, "ClientSession-" + playerId);
        t.setDaemon(true);
        t.start();
    }
}
