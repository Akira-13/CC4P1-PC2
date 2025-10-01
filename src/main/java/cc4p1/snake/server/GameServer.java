package cc4p1.snake.server;

import cc4p1.snake.client.ClientSession;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor autoritativo simple:
 * - acepta conexiones
 * - crea una ClientSession por socket
 * - ejecuta tick fijo y difunde STATE a los clientes
 */

public class GameServer {
  private final int port;
  private final int tps;
  private final Map<Integer, ClientSession> clients = new ConcurrentHashMap<>();
  private final GameState state = new GameState();
  private volatile boolean running = true;
  private int nextPlayerId = 1;

  private ServerSocket serverSocket;
  private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(4);

  public GameServer(int port, int tps) {
    this.port = port;
    this.tps = tps;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(port);
    System.out.println("Servidor iniciado en puerto " + port);

    // Hilo aceptador
    exec.execute(() -> {
      System.out.println("Accepting connections...");
      while (running) {
        try {
          Socket s = serverSocket.accept();
          s.setTcpNoDelay(true);
          int pid = assignPlayerId();
          ClientSession cs = new ClientSession(pid, s, this);
          clients.put(pid, cs);
          cs.start();
          System.out.println("Client connected: pid=" + pid + " from " + s.getRemoteSocketAddress());
        } catch (IOException e) {
          if (running)
            e.printStackTrace();
        }
      }
    });

    long periodMs = 1000L / Math.max(1, tps);
    exec.scheduleAtFixedRate(this::tick, 0, periodMs, TimeUnit.MILLISECONDS);

    // Añadimos un shutdown hook para cerrar bien
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  private synchronized int assignPlayerId() {
    return nextPlayerId++;
  }

  private void tick() {
    try {
      // 1) aplicar inputs solo si hay jugadores
      if (state.hasPlayers()) {
        for (ClientSession cs : clients.values()) {
          String dir = cs.consumeLastDirection();
          state.applyInput(cs.getPlayerId(), dir);
        }
        // 2) avanzar el mundo
        state.step();
      }

      // 3) construir BOARD visual y difundir SIEMPRE (incluso sin jugadores)
      String boardContent = state.renderBoard();
      String boardPayload = "BOARD " + boardContent.replace("\n", "\\n") + "\n";
      for (ClientSession cs : clients.values()) {
        cs.send(boardPayload);
      }

      // 4) enviar puntajes en formato texto separado SIEMPRE
      String scoresContent = state.renderScores();
      String scoresPayload = "SCORES " + scoresContent.replace("\n", "\\n") + "\n";
      for (ClientSession cs : clients.values()) {
        cs.send(scoresPayload);
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  // llamadas desde ClientSession
  public void onJoin(int playerId, String name) {
    state.addPlayer(playerId, name);
    ClientSession cs = clients.get(playerId);
    if (cs != null)
      cs.send("WELCOME " + playerId + "\n");
    System.out.println("Player joined: " + playerId + " name=" + name);
  }

  public void onInput(int playerId, String dir) {
    // guardado en ClientSession; GameServer aplica en el tick
    ClientSession cs = clients.get(playerId);
    if (cs != null)
      cs.setLastDirection(dir);
  }

  public void onLevelCommand(int playerId, String levelCmd) {
    // Solo permite cambiar nivel si es un comando válido
    if (levelCmd.equals("NEXT")) {
      state.nextLevel();
      System.out.println("Player " + playerId + " cambió al siguiente nivel");
    } else if (levelCmd.startsWith("SET ")) {
      try {
        int levelNumber = Integer.parseInt(levelCmd.substring(4));
        state.setLevel(levelNumber);
        System.out.println("Player " + playerId + " cambió al nivel " + levelNumber);
      } catch (NumberFormatException e) {
        ClientSession cs = clients.get(playerId);
        if (cs != null) {
          cs.send("ERR Invalid level number\n");
        }
      }
    } else {
      ClientSession cs = clients.get(playerId);
      if (cs != null) {
        cs.send("ERR Unknown level command. Use NEXT or SET <number>\n");
      }
    }
  }

  public void onQuit(int playerId) {
    clients.remove(playerId);
    state.removePlayer(playerId);
    System.out.println("Player quit: " + playerId);
  }

  private void shutdown() {
    running = false;
    try {
      if (serverSocket != null)
        serverSocket.close();
    } catch (IOException ignored) {
    }
    exec.shutdownNow();
    for (ClientSession cs : new ArrayList<>(clients.values())) {
      try {
        cs.closeSilently();
      } catch (Exception ignored) {
      }
    }
    System.out.println("Server stopped.");
  }
}