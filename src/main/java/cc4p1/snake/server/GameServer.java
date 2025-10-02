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
 * - ejecuta tick (TPS) y difunde STATE/BOARD/SCORES
 */
public class GameServer {
  private final int port;
  // 🔸 TPS ahora es dinámico; ya no final
  private volatile int tps;

  private final Map<Integer, ClientSession> clients = new ConcurrentHashMap<>();
  private final GameState state = new GameState();
  private volatile boolean running = true;
  public int nextPlayerId = 1;

  private ServerSocket serverSocket;

  // Un solo hilo es suficiente para el loop; otro para aceptar
  private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(2);
  private ScheduledFuture<?> loopHandle;

  public GameServer(int port, int tps) {
    this.port = port;
    this.tps  = Math.max(1, tps); // fallback si el nivel no define tick aún
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
          if (running) e.printStackTrace();
        }
      }
    });

    // 🔸 Programa el loop con el tick del nivel actual (si existe), si no usa el constructor
    int initialTps = Math.max(1, safeLevelTps());
    scheduleLoop(initialTps);

    // Shutdown ordenado
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  // Reprograma el loop del juego al TPS indicado
  private synchronized void scheduleLoop(int newTps) {
    if (loopHandle != null) {
      loopHandle.cancel(false);
    }
    this.tps = Math.max(1, newTps);
    long periodMs = 1000L / this.tps;
    loopHandle = exec.scheduleAtFixedRate(this::tick, 0, periodMs, TimeUnit.MILLISECONDS);
    System.out.println("Loop programado a " + this.tps + " TPS");
  }

  private int safeLevelTps() {
    try { return state.getCurrentTickRateHz(); }
    catch (Throwable t) { return this.tps; }
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

      // 3) difundir estado (STATE JSON + BOARD ASCII) y puntajes
      //    (mantengo BOARD/SCORES por compatibilidad con tu cliente actual)
      String stateJson = state.toJson();
      String boardContent = state.renderBoard();
      String boardPayload  = "BOARD "  + boardContent.replace("\n", "\\n") + "\n";
      String statePayload  = "STATE "  + stateJson + "\n";
      String scoresContent = state.renderScores();
      String scoresPayload = "SCORES " + scoresContent.replace("\n", "\\n") + "\n";

      for (ClientSession cs : clients.values()) {
        cs.send(statePayload);
        cs.send(boardPayload);
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
    if (cs != null) cs.send("WELCOME " + playerId + "\n");
    System.out.println("Player joined: " + playerId + " name=" + name);
  }

  public void onInput(int playerId, String dir) {
    // guardado en ClientSession; GameServer aplica en el tick
    ClientSession cs = clients.get(playerId);
    if (cs != null) cs.setLastDirection(dir);
  }

  public void onLevelCommand(int playerId, String levelCmd) {
    // Cambiar nivel y reprogramar TPS del loop
    if (levelCmd.equals("NEXT")) {
      state.nextLevel();
      System.out.println("Player " + playerId + " cambió al siguiente nivel");
      onLevelChangedBroadcastAndReschedule();
    } else if (levelCmd.startsWith("SET ")) {
      try {
        int levelNumber = Integer.parseInt(levelCmd.substring(4));
        state.setLevel(levelNumber);
        System.out.println("Player " + playerId + " cambió al nivel " + levelNumber);
        onLevelChangedBroadcastAndReschedule();
      } catch (NumberFormatException e) {
        ClientSession cs = clients.get(playerId);
        if (cs != null) cs.send("ERR Invalid level number\n");
      }
    } else {
      ClientSession cs = clients.get(playerId);
      if (cs != null) cs.send("ERR Unknown level command. Use NEXT or SET <number>\n");
    }
  }

  private void onLevelChangedBroadcastAndReschedule() {
    // 🔸 Reprograma el loop con el tick del nuevo nivel
    scheduleLoop(safeLevelTps());

    // 🔸 Difunde inmediatamente el nuevo estado (para ver el mapa al instante)
    String stateJson = state.toJson();
    String boardContent = state.renderBoard();
    String scoresContent = state.renderScores();

    String statePayload  = "STATE "  + stateJson + "\n";
    String boardPayload  = "BOARD "  + boardContent.replace("\n", "\\n") + "\n";
    String scoresPayload = "SCORES " + scoresContent.replace("\n", "\\n") + "\n";

    for (ClientSession cs : clients.values()) {
      cs.send(statePayload);
      cs.send(boardPayload);
      cs.send(scoresPayload);
    }
  }

  public void onQuit(int playerId) {
    clients.remove(playerId);
    state.removePlayer(playerId);
    System.out.println("Player quit: " + playerId);
    nextPlayerId--;
  }

  private void shutdown() {
    running = false;
    try {
      if (serverSocket != null) serverSocket.close();
    } catch (IOException ignored) {}
    if (loopHandle != null) loopHandle.cancel(false);
    exec.shutdownNow();
    for (ClientSession cs : new ArrayList<>(clients.values())) {
      try { cs.closeSilently(); } catch (Exception ignored) {}
    }
    System.out.println("Server stopped.");
  }
}
