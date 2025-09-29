package cc4p1.snake.server;
import cc4p1.snake.commonlogic.Fruit;
import cc4p1.snake.commonlogic.Pt;
import cc4p1.snake.commonlogic.Snake;
import java.util.*;

/**
 * Representación muy simple del estado del juego.
 * - mantiene para cada playerId una "snake" como lista de puntos
 * - direcciones deseadas (aplicadas por GameServer)
 * - frutas en el tablero
 *
 * Nota: implementación básica para desarrollo y pruebas. P2 debería mover
 * la lógica más completa al módulo core/ y añadir tests.
 */
public class GameState {
  public static final int WIDTH = 32;
  public static final int HEIGHT = 10;

  // Punto simple
  //static class Pt { int x, y; Pt(int x, int y){this.x=x;this.y=y;} }
  //private final Map<Integer, LinkedList<Pt>> snakes = new HashMap<>();
  private final Map<Integer,Snake> snakes = new HashMap();
  private final Map<Integer, String> directions = new HashMap<>();
  private final Map<Integer, Integer> scores = new HashMap<>();
  private final List<Fruit> fruits = new ArrayList<>();
  private final Random rand = new Random();

  public GameState() {
    // spawn inicial de frutas
    spawnFruit();
    spawnFruit();
    spawnFruit();
    spawnFruit();
  }

  public synchronized void addPlayer(int id, String name) {
    // coloca la serpiente en una posición no colisionada
    int x = rand.nextInt(WIDTH);
    int y = rand.nextInt(HEIGHT);
    
    LinkedList<Pt> body = new LinkedList<>();
    body.add(new Pt(x, y));
    snakes.put(id, new Snake(name, body));
    directions.put(id, "RIGHT");
    scores.put(id, 0);
  }

  public synchronized void removePlayer(int id) {
    snakes.remove(id);
    directions.remove(id);
    scores.remove(id);
  }

  public synchronized void applyInput(int id, String dir) {
    if (dir == null) return;
    dir = dir.trim().toUpperCase();
    if (!snakes.containsKey(id)) return;
    // evitar reversa inmediata (simplificación: permitimos por ahora)
    if (dir.equals("UP") || dir.equals("DOWN") || dir.equals("LEFT") || dir.equals("RIGHT")) {
      directions.put(id, dir);
    }
  }

  public synchronized boolean hasPlayers(){
    return !snakes.isEmpty();
  }

  public synchronized void step() {
    // mover cada snake
    Set<Integer> dead = new HashSet<>();
    Map<Integer, Pt> newHeads = new HashMap<>();

    // calcular nueva cabeza
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
      int id = e.getKey();
      LinkedList<Pt> body = e.getValue().points;
      Pt head = body.getFirst();
      String dir = directions.getOrDefault(id, "RIGHT");
      int nx = head.x, ny = head.y;
      switch (dir) {
        case "UP": ny = (head.y - 1 + HEIGHT) % HEIGHT; break;
        case "DOWN": ny = (head.y + 1) % HEIGHT; break;
        case "LEFT": nx = (head.x - 1 + WIDTH) % WIDTH; break;
        case "RIGHT": nx = (head.x + 1) % WIDTH; break;
      }
      newHeads.put(id, new Pt(nx, ny));
    }

    // detectar colisiones (con otras cabezas o cuerpos)
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
      int id = e.getKey();
      Pt nh = newHeads.get(id);
      boolean collided = false;
      for (Map.Entry<Integer, Snake> e2 : snakes.entrySet()) {
        for (Pt seg : e2.getValue().points) {
          if (seg.x == nh.x && seg.y == nh.y) {
            collided = true;
            break;
          }
        }
        if (collided) break;
      }
      if (collided) dead.add(id);
    }

    // aplicar movimientos para los vivos
    for (Map.Entry<Integer, Snake> e : new HashMap<>(snakes).entrySet()) {
      int id = e.getKey();
      if (dead.contains(id)) {
        snakes.remove(id);
        directions.remove(id);
        continue;
      }
      LinkedList<Pt> body = e.getValue().points;
      Pt nh = newHeads.get(id);
      body.addFirst(nh);

      // si comió fruta -> no quitar cola y +1 score
      boolean ate = false;
      Iterator<Fruit> fIt = fruits.iterator();
      while (fIt.hasNext()) {
        Pt f = fIt.next().point;
        if (f.x == nh.x && f.y == nh.y) { ate = true; fIt.remove(); break; }
      }
      if (ate) {
        scores.put(id, scores.getOrDefault(id, 0) + 1);
        // generar una fruta nueva
        spawnFruit();
      } else {
        // avanzar: quitar cola
        if (body.size() > 0) body.removeLast();
      }
    }

    // opcional: re-spawn players que murieron (aquí se elimina y deja puntaje)
    for (int idDead : dead) {
      scores.remove(idDead); // simplificación: quitar score si muere
      // Si prefieres mantener score, comenta la línea anterior.
    }

    // Mantener al menos una fruta
    if (fruits.size() < 1) spawnFruit();
  }

  private synchronized void spawnFruit() {
    for (int tries = 0; tries < 20; tries++) {
      int x = rand.nextInt(WIDTH);
      int y = rand.nextInt(HEIGHT);
      int fruitScore = rand.nextInt(1,10);
      boolean occ = false;
      for (Snake snake : snakes.values()) {
        for (Pt p : snake.points) if (p.x == x && p.y == y) { occ = true; break; }
        if (occ) break;
      }
      if (!occ) {
        fruits.add(new Fruit(new Pt(x, y), fruitScore));
        return;
      }
    }
    // si no encontró sitio, no hace nada
  }
  
  // Renderizar tablero en TextArea
  public synchronized String renderBoard() {
    char[][] board = new char[HEIGHT][WIDTH];
    for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < WIDTH; x++) {
            board[y][x] = ' '; // fondo
        }
    }

    // Dibujar frutas
    for (Fruit f : fruits) {
        board[f.point.y][f.point.x] = 'F';
    }

    // Dibujar serpientes
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
        int pid = e.getKey();
        LinkedList<Pt> body = e.getValue().points;
        char symbol = (char) ('0' + (pid % 10)); // usa dígitos 0-9 para identificar jugadores
        boolean head = true;
        for (Pt p : body) {
            if (head) {
                board[p.y][p.x] = symbol; // cabeza
                head = false;
            } else {
                board[p.y][p.x] = 's'; // cuerpo
            }
        }
    }

    // Convertir matriz a String
    StringBuilder sb = new StringBuilder();
    for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < WIDTH; x++) {
            sb.append(board[y][x]);
        }
        sb.append("\n");
    }
    return sb.toString();
}
  
  // Serializador muy simple a JSON (manual, sin librerías)
  public synchronized String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    // snakes
    sb.append("\"snakes\":[");
    boolean firstSnake = true;
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
      if (!firstSnake) sb.append(",");
      firstSnake = false;
      sb.append("{\"id\":").append(e.getKey()).append(",\"body\":[");
      boolean first = true;
      for (Pt p : e.getValue().points) {
        if (!first) sb.append(",");
        first = false;
        sb.append("[").append(p.x).append(",").append(p.y).append("]");
      }
      sb.append("]}");
    }
    sb.append("],");
    // fruits
    sb.append("\"fruits\":[");
    boolean ffirst = true;
    for (Fruit f : fruits) {
      if (!ffirst) sb.append(",");
      ffirst = false;
      sb.append("[").append(f.point.x).append(",").append(f.point.y).append("]");
    }
    sb.append("],");
    // scores
    sb.append("\"scores\":{");
    boolean sfirst = true;
    for (Map.Entry<Integer, Integer> e : scores.entrySet()) {
      if (!sfirst) sb.append(",");
      sfirst = false;
      sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
    }
    sb.append("}");
    sb.append("}");
    return sb.toString();
  }

  public synchronized String scoresJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    boolean first = true;
    for (Map.Entry<Integer, Integer> e : scores.entrySet()) {
      if (!first) sb.append(",");
      first = false;
      sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
    }
    sb.append("}");
    return sb.toString();
  }}
