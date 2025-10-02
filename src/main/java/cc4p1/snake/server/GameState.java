package cc4p1.snake.server;

import cc4p1.snake.commonlogic.Fruit;
import cc4p1.snake.commonlogic.Pt;
import cc4p1.snake.commonlogic.Snake;
import cc4p1.snake.game.LevelManager;
import java.util.*;

/**
 * Representación muy simple del estado del juego.
 * - mantiene para cada // Hacer crecer la serpiente según el valor de la fruta
 * // Agregar tantos segmentos como puntos valga la fruta
 * for (int i = 0; i < fruitScore; i++) {
 * // Agregar segmentos al final de la cola (duplicar el último segmento)
 * if (body.size() > 0) {
 * body.addLast(new Pt(body.peekLast().x, body.peekLast().y));
 * } else {
 * // Si no tiene cola, agregar segmento en la posición actual de la cabeza
 * Pt currentHead = body.get(0); // La cabeza actual (primer elemento)
 * body.addLast(new Pt(currentHead.x, currentHead.y));
 * }
 * } "snake" como lista de puntos
 * - direcciones deseadas (aplicadas por GameServer)
 * - frutas en el tablero
 *
 * Nota: implementación básica para desarrollo y pruebas. P2 debería mover
 * la lógica más completa al módulo core/ y añadir tests.
 */
public class GameState {
    private int WIDTH;               // dimensiones dinámicas según el nivel
    private int HEIGHT;
    private boolean[][] walls;  // Tamaño exacto de archivos de nivel

  // Gestor de niveles
  private final LevelManager levelManager;

  // Punto simple
  // static class Pt { int x, y; Pt(int x, int y){this.x=x;this.y=y;} }
  // private final Map<Integer, LinkedList<Pt>> snakes = new HashMap<>();
  private final Map<Integer, Snake> snakes = new HashMap<>();
  private final Map<Integer, String> directions = new HashMap<>();
  private final Map<Integer, Integer> scores = new HashMap<>();
  private final List<Fruit> fruits = new ArrayList<>();
  private final Random rand = new Random();
  private final Map<Integer, Integer> growLeft = new HashMap<>();
  
  private int maxFruits = 1; // por nivel


  public GameState() {
    this.levelManager = new LevelManager();
    initializeWalls();
    // spawn inicial de frutas
    fillFruitsToMax();
  }

    private void initializeWalls() {
      char[][] levelMap = levelManager.getCurrentLevel();

      // Dimensiones reales desde el archivo del nivel
      HEIGHT = levelMap.length;
      WIDTH  = (HEIGHT > 0 ? levelMap[0].length : 0);

      // Crear matriz de paredes con el tamaño exacto del nivel
      walls = new boolean[HEIGHT][WIDTH];

      // Copiar paredes: '#' = true
      for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < WIDTH; x++) {
          walls[y][x] = (levelMap[y][x] == '#');
        }
      }
      
      this.maxFruits = levelManager.getCurrentMaxFruits();

      System.out.println("=== DEBUG paredes ===");
      System.out.println("Level size: " + WIDTH + "x" + HEIGHT);
      System.out.println("=== FIN DEBUG paredes ===");
    }


  public synchronized void addPlayer(int id, String name) {
    // coloca la serpiente en una posición no colisionada

    int x = rand.nextInt(1, WIDTH-1);
    int y = rand.nextInt(1, HEIGHT-1);



    LinkedList<Pt> body = new LinkedList<>();
    body.add(new Pt(x, y));

    char bodyLetter = Character.toLowerCase(name.trim().charAt(0));

    snakes.put(id, new Snake(name, body, bodyLetter));
    directions.put(id, "RIGHT");
    scores.put(id, 0);
    growLeft.put(id, 0); 
  }

  public synchronized void removePlayer(int id) {
    snakes.remove(id);
    directions.remove(id);
    scores.remove(id);
    growLeft.remove(id);
  }

  public synchronized void applyInput(int id, String dir) {
    if (dir == null)
      return;
    dir = dir.trim().toUpperCase();
    if (!snakes.containsKey(id))
      return;

    // Validar direcciones válidas
    if (dir.equals("UP") || dir.equals("DOWN") || dir.equals("LEFT") || dir.equals("RIGHT")) {
      // Evitar reversa inmediata (validación anti-suicidio)
      String currentDir = directions.getOrDefault(id, "RIGHT");

      // Verificar si la nueva dirección es opuesta a la actual
      boolean isOppositeDirection = false;
      switch (currentDir) {
        case "UP":
          isOppositeDirection = dir.equals("DOWN");
          break;
        case "DOWN":
          isOppositeDirection = dir.equals("UP");
          break;
        case "LEFT":
          isOppositeDirection = dir.equals("RIGHT");
          break;
        case "RIGHT":
          isOppositeDirection = dir.equals("LEFT");
          break;
      }

      // Solo aplicar el cambio de dirección si no es opuesta o si la serpiente tiene
      // solo un segmento
      Snake snake = snakes.get(id);
      if (!isOppositeDirection || snake.points.size() <= 1) {
        directions.put(id, dir);
        System.out
            .println("Jugador " + id + " cambió dirección a " + dir + " (dirección anterior: " + currentDir + ")");
      } else {
        System.out.println("Jugador " + id + " intentó moverse en dirección opuesta (" + dir + " vs " + currentDir
            + ") - movimiento bloqueado");
      }
    }
  }

  public synchronized boolean hasPlayers() {
    return !snakes.isEmpty();
  }

  public synchronized void step() {
    // mover cada snake
    Set<Integer> dead = new HashSet<>();
    Map<Integer, Pt> newHeads = new HashMap<>();

    // calcular nueva cabeza con sistema de paredes/wrap-around
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
      int id = e.getKey();
      LinkedList<Pt> body = e.getValue().points;
      Pt head = body.getFirst();
      String dir = directions.getOrDefault(id, "RIGHT");
      int nx = head.x, ny = head.y;

      // Calcular la nueva posición
      switch (dir) {
        case "UP":
          ny = head.y - 1;
          break;
        case "DOWN":
          ny = head.y + 1;
          break;
        case "LEFT":
          nx = head.x - 1;
          break;
        case "RIGHT":
          nx = head.x + 1;
          break;
      }

      // Verificar límites y manejar paredes - LÓGICA SIMPLIFICADA
      boolean hitWall = false;

      System.out.println(
          "DEBUG: Jugador " + id + " intentando moverse de (" + head.x + "," + head.y + ") a (" + nx + "," + ny + ")");

      // Manejar wrap-around primero
      if (nx < 0) {
        nx = WIDTH - 1; // wrap-around a la derecha
      } else if (nx >= WIDTH) {
        nx = 0; // wrap-around a la izquierda
      }

      if (ny < 0) {
        ny = HEIGHT - 1; // wrap-around abajo
      } else if (ny >= HEIGHT) {
        ny = 0; // wrap-around arriba
      }

      // Ahora simplemente verificar si la posición final tiene pared
      if (walls[ny][nx]) {
        hitWall = true;
        System.out.println("DEBUG: Jugador " + id + " MURIÓ - chocó con pared en (" + nx + "," + ny + ")");
      } else {
        System.out.println("DEBUG: Jugador " + id + " se mueve libremente a (" + nx + "," + ny + ")");
      }

      if (hitWall) {
        dead.add(id);
      } else {
        newHeads.put(id, new Pt(nx, ny));
      }
    } // detectar colisiones (con otras cabezas o cuerpos)
    // 1. Detectar colisiones de cabeza a cabeza (choque frontal)
    Map<Pt, List<Integer>> headPositions = new HashMap<>();
    for (Map.Entry<Integer, Pt> entry : newHeads.entrySet()) {
      Pt nh = entry.getValue();
      headPositions.computeIfAbsent(nh, k -> new ArrayList<>()).add(entry.getKey());
    }
    // Si dos o más cabezas van al mismo punto, todas mueren
    for (Map.Entry<Pt, List<Integer>> entry : headPositions.entrySet()) {
      if (entry.getValue().size() > 1) {
        dead.addAll(entry.getValue());
      }
    }

    // 2. Detectar colisiones de cabeza con cuerpo (excepto consigo misma)
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
      int id = e.getKey();
      if (dead.contains(id))
        continue; // Ya murió por pared o choque frontal

      Pt nh = newHeads.get(id);
      if (nh == null)
        continue; // No tiene nueva cabeza (murió por pared), saltar

      boolean collided = false;
      for (Map.Entry<Integer, Snake> e2 : snakes.entrySet()) {
        int id2 = e2.getKey();
        // Si es la misma serpiente, ignorar la cabeza (permitir moverse sobre sí misma
        // solo si es cuerpo)
        List<Pt> bodyToCheck = (id == id2) ? e2.getValue().points.subList(1, e2.getValue().points.size())
            : e2.getValue().points;
        for (Pt seg : bodyToCheck) {
          if (seg.x == nh.x && seg.y == nh.y) {
            collided = true;
            break;
          }
        }
        if (collided)
          break;
      }
      if (collided)
        dead.add(id);
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
    if (nh == null) {
      System.err.println("Error: newHead es null para jugador " + id);
      dead.add(id);
      snakes.remove(id);
      directions.remove(id);
      continue;
    }


    body.addFirst(nh);

    // --- detectar fruta en la nueva cabeza ---
    boolean ate = false;
    int fruitScore = 0;
    Iterator<Fruit> fIt = fruits.iterator();
    while (fIt.hasNext()) {
      Fruit fruit = fIt.next();
      if (fruit.point.x == nh.x && fruit.point.y == nh.y) {
        ate = true;
        fruitScore = fruit.score;
        fIt.remove();
        break;
      }
    }

    if (ate) {
      // Mantén SOLO un contador de crecimiento pendiente; no “regales” un +1 inmediato.
      scores.put(id, scores.getOrDefault(id, 0) + fruitScore);
      Snake currentSnake = e.getValue();
      currentSnake.growthPending = currentSnake.growthPending + fruitScore;

      System.out.println(
          "Jugador " + id + " comió fruta con " + fruitScore + " puntos. Puntuación total: " + scores.get(id));
      // Nota: body.size() ya incluye la cabeza; no sumes +1 aquí.
      System.out.println("Estado serpiente " + id + " - Tamaño total: " + body.size() +
                         ", Crecimiento pendiente: " + currentSnake.growthPending);

      spawnFruit();
    }

    // --- REGLA ÚNICA DE COLA (aplica SIEMPRE, se haya comido o no) ---
    Snake currentSnake = e.getValue();
    if (currentSnake.growthPending > 0) {
      // Este tick “crece” manteniendo la cola
      currentSnake.growthPending--;
      System.out.println("Jugador " + id + " creció 1 segmento. Tamaño actual: " + body.size() +
                         ", Crecimiento pendiente: " + currentSnake.growthPending +
                         ", Puntuación: " + scores.getOrDefault(id, 0));
    } else {
      // Movimiento normal: quitar cola
      if (!body.isEmpty()) body.removeLast();
    }
    }

    // opcional: re-spawn players que murieron (aquí se elimina y deja puntaje)
    for (int idDead : dead) {
      scores.remove(idDead); // simplificación: quitar score si muere
      // Si prefieres mantener score, comenta la línea anterior.
    }

    // Mantener la cantidad objetivo del nivel
    while (fruits.size() < maxFruits) {
        if (!spawnFruit()) break; // evita bucles infinitos si no hay espacio
    }

  }

private synchronized boolean spawnFruit() {
    for (int tries = 0; tries < Math.max(20, WIDTH*HEIGHT); tries++) {
        int x = rand.nextInt(1, WIDTH-1);
        int y = rand.nextInt(1, HEIGHT-1);
        int fruitScore = rand.nextInt(1, 4);

        if (walls[y][x]) continue;

        boolean occ = false;
        for (Snake snake : snakes.values()) {
            for (Pt p : snake.points) if (p.x == x && p.y == y) { occ = true; break; }
            if (occ) break;
        }
        if (!occ) {
            fruits.add(new Fruit(new Pt(x, y), fruitScore));
            System.out.println("Generada nueva fruta en (" + x + "," + y + ") con " + fruitScore + " puntos");
            return true;
        }
    }
    System.out.println("No se pudo generar fruta tras múltiples intentos");
    return false;
}


  // Renderizar tablero en TextArea con sistema de paredes reales
  public synchronized String renderBoard() {
    // El tablero usa dimensiones exactas de 12x30 para coincidir con nivel y
    // TextArea
    int displayWidth  = WIDTH;
    int displayHeight = HEIGHT;
    char[][] board = new char[displayHeight][displayWidth];


    // Inicializar el tablero basado en el sistema de paredes
    for (int y = 0; y < displayHeight; y++) {
      for (int x = 0; x < displayWidth; x++) {
        if (walls[y][x]) {
          // Hay pared - usar símbolo de #
          board[y][x] = '#'; // Símbolo de pared más visible
        } else {
          // No hay pared - espacio abierto
          board[y][x] = ' '; // Espacio abierto (permite wrap-around)
        }
      }
    }

    // Dibujar frutas con diferentes símbolos según puntuación
    for (Fruit f : fruits) {
      // char fruitSymbol;
      // Diferentes símbolos según la puntuación de la fruta (1-3 puntos)
      // if (f.score == 1) {
      // fruitSymbol = '·'; // Fruta pequeña (1 punto)
      // } else if (f.score == 2) {
      // fruitSymbol = '*'; // Fruta mediana (2 puntos)
      // } else { // f.score == 3
      // fruitSymbol = '♦'; // Fruta grande (3 puntos)
      // }
      // Las frutas se dibujan directamente en sus coordenadas (sin offset)
      board[f.point.y][f.point.x] = (char) ('0' + f.score);
    }

    // Dibujar serpientes
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
      LinkedList<Pt> body = e.getValue().points;
      char bodyLetter = e.getValue().bodyLetter;
      boolean head = true;
      for (Pt p : body) {
        // Dibujar directamente en las coordenadas (sin offset)
        if (head) {
          board[p.y][p.x] = 'O'; // cabeza siempre es "O"
          head = false;
        } else {
          board[p.y][p.x] = bodyLetter; // cuerpo con letra personalizada
        }
      }
    }

    // Convertir matriz a String - SOLO EL TABLERO
    StringBuilder sb = new StringBuilder();

    // Solo mostrar el tablero de juego
    for (int y = 0; y < displayHeight; y++) {
      for (int x = 0; x < displayWidth; x++) {
        sb.append(board[y][x]);
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  // Método separado para obtener los puntajes en formato texto
  public synchronized String renderScores() {
    StringBuilder sb = new StringBuilder();

    // Información del nivel actual
    sb.append("=== ").append(getCurrentLevelInfo()).append(" ===\n\n");

    if (snakes.isEmpty()) {
      sb.append("No hay jugadores conectados");
    } else {
      sb.append("=== PUNTAJES ===\n");
      Set<String> nombresMostrados = new HashSet<>();
      for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
        String playerName = e.getValue().name;
        int score = scores.getOrDefault(e.getKey(), 0);
        if (!nombresMostrados.contains(playerName)) {
          sb.append("Jugador (").append(playerName).append("): ").append(score).append(" puntos\n");
          nombresMostrados.add(playerName);
        }
      }
    }

    String result = sb.toString();
    System.out.println("DEBUG renderScores(): '" + result + "'");
    return result;
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
    sb.append("},");

    // dimensiones
    sb.append("\"width\":").append(WIDTH).append(",\"height\":").append(HEIGHT).append(",");

    // paredes
    sb.append("\"walls\":[");
    boolean firstWall = true;
    for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < WIDTH; x++) {
            if (walls[y][x]) {
                if (!firstWall) sb.append(",");
                firstWall = false;
                sb.append("[").append(x).append(",").append(y).append("]");
            }
        }
    }
    sb.append("]}"); // ← cierra walls y el objeto raíz

    return sb.toString();
    }


  public synchronized String scoresJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    boolean first = true;
    for (Map.Entry<Integer, Integer> e : scores.entrySet()) {
      if (!first)
        sb.append(",");
      first = false;
      sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
    }
    sb.append("}");
    return sb.toString();
  }

  /**
   * Cambia al siguiente nivel
   */
    public synchronized void nextLevel() {
      levelManager.nextLevel();
      initializeWalls();
      // Limpiar frutas y generar nuevas
      fruits.clear();
      fillFruitsToMax();
      System.out.println("Cambiado a nivel " + levelManager.getCurrentLevelNumber());
    }

  /**
   * Establece un nivel específico
   */
    public synchronized void setLevel(int levelNumber) {
      levelManager.setLevel(levelNumber);
      initializeWalls();
      // Limpiar frutas y generar nuevas
      fruits.clear();
      fillFruitsToMax();
      System.out.println("Nivel establecido: " + levelNumber);
    }

  /**
   * Obtiene información del nivel actual
   */
    public String getCurrentLevelInfo() {
      return "Nivel " + levelManager.getCurrentLevelNumber() + " de " + levelManager.getTotalLevels();
    }
  
    private void fillFruitsToMax() {
        int guard = WIDTH * HEIGHT; // corta en escenarios sin espacio
        while (fruits.size() < maxFruits && guard-- > 0) {
            if (!spawnFruit()) break;
        }
    }
    
    public int getCurrentTickRateHz() { return levelManager.getCurrentTickRateHz(); }



}
