package cc4p1.snake.server;
import cc4p1.snake.client.ClientSession;
import java.io.*;
import java.net.*;
import java.util.*;
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
          if (running) e.printStackTrace();
        }
      }
    });

    long periodMs = 1000L / Math.max(1, tps);
    exec.scheduleAtFixedRate(this::tick, 0, periodMs, TimeUnit.MILLISECONDS);

    // Añadimos un shutdown hook para cerrar bien
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  private synchronized int assignPlayerId() { return nextPlayerId++; }

  private void tick() {
    try {
      if(!state.hasPlayers()){
        return;
      }
      // 1) aplicar inputs
      for (ClientSession cs : clients.values()) {
        String dir = cs.consumeLastDirection();
        state.applyInput(cs.getPlayerId(), dir);
      }
      // 2) avanzar el mundo
      state.step();
      // 3) construir STATE y difundir
      String payload = "STATE " + state.toJson() + "\n";
      for (ClientSession cs : clients.values()) {
        cs.send(payload);
      }

      // 4) opcional: enviar SCORE si cambió (aquí simplificado, cada tick)
      String scorePayload = "SCORE " + state.scoresJson() + "\n";
      for (ClientSession cs : clients.values()) cs.send(scorePayload);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  // llamadas desde ClientSession
  public void onJoin(int playerId, String name) {
    state.addPlayer(playerId, name);
    ClientSession cs = clients.get(playerId);
    if (cs != null) cs.send("WELCOME " + playerId + "\n");
    System.out.println("Player joined: " + playerId + " name=" + name);
  }

  public void onInput(int playerId, String dir) {
    // guardado en ClientSession; GameServer aplica en el tick
    ClientSession cs = clients.get(playerId);
    if (cs != null) cs.setLastDirection(dir);
  }

  public void onQuit(int playerId) {
    clients.remove(playerId);
    state.removePlayer(playerId);
    System.out.println("Player quit: " + playerId);
  }

  private void shutdown() {
    running = false;
    try {
      if (serverSocket != null) serverSocket.close();
    } catch (IOException ignored) {}
    exec.shutdownNow();
    for (ClientSession cs : new ArrayList<>(clients.values())) {
      try { cs.closeSilently(); } catch (Exception ignored) {}
    }
    System.out.println("Server stopped.");
  }
}
