package cc4p1.snake.server;

import cc4p1.snake.commonlogic.Fruit;
import cc4p1.snake.commonlogic.Pt;
import cc4p1.snake.commonlogic.Snake;
import cc4p1.snake.game.LevelManager;
import java.util.*;

/**
 * Representación muy simple del estado del juego.
 * - mantiene para cada         // Hacer crecer la serpiente según el valor de la fruta
        // Agregar tantos segmentos como puntos valga la fruta
        for (int i = 0; i < fruitScore; i++) {
          // Agregar segmentos al final de la cola (duplicar el último segmento)
          if (body.size() > 0) {
            body.addLast(new Pt(body.peekLast().x, body.peekLast().y));
          } else {
            // Si no tiene cola, agregar segmento en la posición actual de la cabeza
            Pt currentHead = body.get(0); // La cabeza actual (primer elemento)
            body.addLast(new Pt(currentHead.x, currentHead.y));
          }
        } "snake" como lista de puntos
 * - direcciones deseadas (aplicadas por GameServer)
 * - frutas en el tablero
 *
 * Nota: implementación básica para desarrollo y pruebas. P2 debería mover
 * la lógica más completa al módulo core/ y añadir tests.
 */
public class GameState {
  public static final int WIDTH = 28; // Área de juego: 28 caracteres
  public static final int HEIGHT = 10; // Área interna: 10 líneas (archivos nivel = 12, bordes incluidos)

  // Sistema de paredes: true = hay pared, false = espacio abierto (wrap-around)
  private final boolean[][] walls = new boolean[12][30]; // Tamaño exacto de archivos de nivel

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

  public GameState() {
    this.levelManager = new LevelManager();
    initializeWalls();
    // spawn inicial de frutas
    spawnFruit();
    spawnFruit();
    spawnFruit();
    spawnFruit();
  }

  private void initializeWalls() {
    // Inicializar todas las posiciones como espacios libres (dimensiones exactas)
    for (int y = 0; y < 12; y++) {
      for (int x = 0; x < 30; x++) {
        walls[y][x] = false;
      }
    }

    // Cargar nivel desde archivo
    char[][] levelMap = levelManager.getCurrentLevel();
    System.out.println("=== DEBUG: Inicializando paredes ===");
    System.out.println("Nivel cargado: " + levelMap.length + " filas");
    if (levelMap.length > 0) {
      System.out.println("Primera fila tiene " + levelMap[0].length + " columnas");
    }

    // Adaptar el mapa cargado a nuestro sistema de paredes (tamaño exacto)
    int mapHeight = Math.min(levelMap.length, 12);
    int mapWidth = levelMap.length > 0 ? Math.min(levelMap[0].length, 30) : 0;

    for (int y = 0; y < mapHeight; y++) {
      StringBuilder rowDebug = new StringBuilder();
      for (int x = 0; x < mapWidth; x++) {
        // '#' representa paredes, ' ' espacios libres
        char cell = levelMap[y][x];
        walls[y][x] = (cell == '#');
        rowDebug.append(cell);
        if (y < 3 || y >= mapHeight - 3 || x < 3 || x >= mapWidth - 3) {
          // Debug solo para bordes para ver qué está pasando
          if (walls[y][x]) {
            System.out.println("Pared detectada en (" + x + "," + y + ") caracter: '" + cell + "'");
          }
        }
      }
      // Mostrar las primeras y últimas filas completas para debug
      if (y < 2 || y >= mapHeight - 2) {
        System.out.println("Fila " + y + ": '" + rowDebug.toString() + "'");
      }
    }
    System.out.println("=== FIN DEBUG paredes ===");
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
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
      int id = e.getKey();
      if (dead.contains(id))
        continue; // Ya murió por pared, saltar verificación de colisión

      Pt nh = newHeads.get(id);
      if (nh == null)
        continue; // No tiene nueva cabeza (murió por pared), saltar

      boolean collided = false;
      for (Map.Entry<Integer, Snake> e2 : snakes.entrySet()) {
        for (Pt seg : e2.getValue().points) {
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
        // No debería pasar, pero por seguridad
        System.err.println("Error: newHead es null para jugador " + id);
        dead.add(id);
        snakes.remove(id);
        directions.remove(id);
        continue;
      }

      body.addFirst(nh);

      // si comió fruta -> no quitar cola y +score según fruta
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
        scores.put(id, scores.getOrDefault(id, 0) + fruitScore);
        System.out.println(
            "Jugador " + id + " comió fruta con " + fruitScore + " puntos. Puntuación total: " + scores.get(id));
        
        // No quitar cola para que crezca naturalmente
        // Y además no quitar cola las próximas (fruitScore-1) veces para que crezca más
        // Esto se maneja con un contador de crecimiento pendiente
        Snake currentSnake = e.getValue();
        currentSnake.growthPending = currentSnake.growthPending + fruitScore;
        
        // Debug: mostrar estado actual de la serpiente
        int totalSize = 1 + currentSnake.points.size(); // cabeza + cuerpo
        System.out.println("Estado serpiente " + id + " - Puntuación: " + scores.get(id) + 
                          ", Tamaño total: " + totalSize + 
                          ", Crecimiento pendiente: " + currentSnake.growthPending);
        
        System.out.println("Serpiente del jugador " + id + " crecerá " + fruitScore + " segmentos en los próximos movimientos");
        
        // generar una fruta nueva
        spawnFruit();
      } else {
        // avanzar: quitar cola solo si no hay crecimiento pendiente
        Snake currentSnake = e.getValue();
        if (currentSnake.growthPending > 0) {
          // No quitar cola, pero reducir contador
          currentSnake.growthPending--;
          int totalSize = 1 + body.size(); // cabeza + cuerpo
          System.out.println("Jugador " + id + " creció 1 segmento. Tamaño actual: " + totalSize + 
                            ", Crecimiento pendiente: " + currentSnake.growthPending + 
                            ", Puntuación: " + scores.getOrDefault(id, 0));
        } else {
          // Movimiento normal: quitar cola
          if (body.size() > 0)
            body.removeLast();
        }
      }
    }

    // opcional: re-spawn players que murieron (aquí se elimina y deja puntaje)
    for (int idDead : dead) {
      scores.remove(idDead); // simplificación: quitar score si muere
      // Si prefieres mantener score, comenta la línea anterior.
    }

    // Mantener al menos una fruta
    if (fruits.size() < 1)
      spawnFruit();
  }

  private synchronized void spawnFruit() {
    for (int tries = 0; tries < 20; tries++) {
      int x = rand.nextInt(WIDTH);
      int y = rand.nextInt(HEIGHT);
      int fruitScore = rand.nextInt(1, 4); // Frutas de 1 a 3 puntos para crecimiento más equilibrado

      // Verificar que no esté en una pared
      if (walls[y][x]) {
        continue; // Intentar otra posición
      }

      boolean occ = false;
      for (Snake snake : snakes.values()) {
        for (Pt p : snake.points)
          if (p.x == x && p.y == y) {
            occ = true;
            break;
          }
        if (occ)
          break;
      }
      if (!occ) {
        fruits.add(new Fruit(new Pt(x, y), fruitScore));
        System.out.println("Generada nueva fruta en (" + x + "," + y + ") con " + fruitScore + " puntos");
        return;
      }
    }
    // si no encontró sitio, no hace nada
    System.out.println("No se pudo generar fruta después de 20 intentos");
  }

  // Renderizar tablero en TextArea con sistema de paredes reales
  public synchronized String renderBoard() {
    // El tablero usa dimensiones exactas de 12x30 para coincidir con nivel y
    // TextArea
    int displayWidth = 30;
    int displayHeight = 12;
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
      char fruitSymbol;
      // Diferentes símbolos según la puntuación de la fruta (1-3 puntos)
      if (f.score == 1) {
        fruitSymbol = '·'; // Fruta pequeña (1 punto)
      } else if (f.score == 2) {
        fruitSymbol = '*'; // Fruta mediana (2 puntos)
      } else { // f.score == 3
        fruitSymbol = '♦'; // Fruta grande (3 puntos)
      }
      // Las frutas se dibujan directamente en sus coordenadas (sin offset)
      board[f.point.y][f.point.x] = fruitSymbol;
    }

    // Dibujar serpientes
    for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
      int pid = e.getKey();
      LinkedList<Pt> body = e.getValue().points;
      char playerLetter = (char) ('A' + (pid % 26)); // usa letras A-Z para identificar jugadores
      boolean head = true;
      for (Pt p : body) {
        // Dibujar directamente en las coordenadas (sin offset)
        if (head) {
          board[p.y][p.x] = 'O'; // cabeza siempre es "O"
          head = false;
        } else {
          board[p.y][p.x] = playerLetter; // cuerpo con letra del jugador
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
      for (Map.Entry<Integer, Snake> e : snakes.entrySet()) {
        int playerId = e.getKey();
        String playerName = e.getValue().name;
        int score = scores.getOrDefault(playerId, 0);
        sb.append("Jugador ").append(playerId).append(" (").append(playerName).append("): ").append(score)
            .append(" puntos\n");
      }

      // Agregar leyenda de frutas
      sb.append("\nLeyenda de frutas:\n");
      sb.append("· = 1 pt | * = 2 pts | ♦ = 3 pts\n");
      sb.append("Paredes: # (mortales) | Espacios abiertos (wrap-around)\n");
      sb.append("Serpientes: O = cabeza | A,B,C... = cuerpo del jugador");
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
      if (!firstSnake)
        sb.append(",");
      firstSnake = false;
      sb.append("{\"id\":").append(e.getKey()).append(",\"body\":[");
      boolean first = true;
      for (Pt p : e.getValue().points) {
        if (!first)
          sb.append(",");
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
      if (!ffirst)
        sb.append(",");
      ffirst = false;
      sb.append("[").append(f.point.x).append(",").append(f.point.y).append("]");
    }
    sb.append("],");
    // scores
    sb.append("\"scores\":{");
    boolean sfirst = true;
    for (Map.Entry<Integer, Integer> e : scores.entrySet()) {
      if (!sfirst)
        sb.append(",");
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
    spawnFruit();
    spawnFruit();
    spawnFruit();
    spawnFruit();
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
    spawnFruit();
    spawnFruit();
    spawnFruit();
    spawnFruit();
    System.out.println("Nivel establecido: " + levelNumber);
  }

  /**
   * Obtiene información del nivel actual
   */
  public String getCurrentLevelInfo() {
    return "Nivel " + levelManager.getCurrentLevelNumber() + " de " + levelManager.getTotalLevels();
  }
}
