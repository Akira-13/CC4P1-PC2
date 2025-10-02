package cc4p1.snake.client;

import cc4p1.snake.ui.IBoardUpdater;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Cliente que se conecta al servidor y maneja la comunicación
 */
public class GameClient {
    private final String host;
    private final int port;
    private final IBoardUpdater window;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private volatile boolean running = true;
    private Thread listenerThread;
    private String currentDirection = "RIGHT"; // Dirección actual del snake

    public GameClient(String host, int port, IBoardUpdater window) {
        this.host = host;
        this.port = port;
        this.window = window;
    }

    public void start() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Hilo que escucha mensajes del servidor
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = in.readLine()) != null) {
                    handleServerMessage(line);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error en conexión: " + e.getMessage());
                    window.updateBoard("Error: Conexión perdida");
                }
            }
        }, "ClientListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void handleServerMessage(String line) {
        if (line.startsWith("BOARD ")) {
            // El mensaje BOARD viene con todo el tablero en una línea, con \n como
            // separadores
            String boardContent = line.substring(6); // Quitar "BOARD "
            String formattedBoard = boardContent.replace("\\n", "\n"); // Restaurar los saltos de línea
            System.out.println("Recibido BOARD, longitud: " + formattedBoard.length());
            window.updateBoard(formattedBoard);
        } else if (line.startsWith("STATE ")) {
            // Mensaje: STATE { ...json... } (mantenemos por compatibilidad)
            String json = line.substring(6);
            System.out.println("Recibido STATE: " + json);
            // Parsear y mostrar en el tablero
            window.updateBoard(parseGameState(json));
        } else if (line.startsWith("WELCOME ")) {
            String playerId = line.substring(8);
            System.out.println("Bienvenido! Tu ID es: " + playerId);
        } else if (line.startsWith("SCORE ")) {
            // Opcionalmente manejar scores en formato JSON (deprecated)
            String scoresJson = line.substring(6);
            System.out.println("Scores: " + scoresJson);
        } else if (line.startsWith("SCORES ")) {
            // Manejar puntajes en formato texto plano
            String scoresText = line.substring(7).replace("\\n", "\n");
            System.out.println("Scores text: " + scoresText);
            window.updateScores(scoresText);
        } else if (line.startsWith("ERR ")) {
            System.err.println("Error del servidor: " + line.substring(4));
        } else {
            System.out.println("Mensaje desconocido: " + line);
        }
    }

    private String parseGameState(String json) {
        // Parseo simple del JSON para renderizar el tablero
        // El servidor ya envía el estado, aquí lo formateamos para mostrar
        // Por simplicidad, si el servidor envía el tablero renderizado, lo usamos
        // directo
        // Si no, tendríamos que parsear el JSON manualmente

        // En este caso, vamos a construir el tablero desde el JSON
        try {
            return buildBoardFromJson(json);
        } catch (Exception e) {
            return "Error al parsear estado del juego";
        }
    }

    private String buildBoardFromJson(String json) {
        // Parseo manual simple del JSON
        // Formato esperado:
        // {"snakes":[{"id":1,"body":[[x,y],...]}],"fruits":[[x,y],...],"scores":{"1":0}}

        final int WIDTH = 32;
        final int HEIGHT = 12;
        char[][] board = new char[HEIGHT][WIDTH];

        // Inicializar tablero vacío
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                board[y][x] = ' ';
            }
        }

        try {
            // Extraer frutas
            int fruitsStart = json.indexOf("\"fruits\":[");
            if (fruitsStart != -1) {
                int fruitsEnd = json.indexOf("]", fruitsStart + 10);
                String fruitsStr = json.substring(fruitsStart + 10, fruitsEnd);
                String[] fruitPairs = fruitsStr.split("\\],\\[");
                for (String pair : fruitPairs) {
                    pair = pair.replace("[", "").replace("]", "");
                    if (!pair.isEmpty()) {
                        String[] coords = pair.split(",");
                        if (coords.length == 2) {
                            int x = Integer.parseInt(coords[0].trim());
                            int y = Integer.parseInt(coords[1].trim());
                            if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
                                board[y][x] = 'F';
                            }
                        }
                    }
                }
            }

            // Extraer serpientes
            int snakesStart = json.indexOf("\"snakes\":[");
            if (snakesStart != -1) {
                int snakesEnd = findMatchingBracket(json, snakesStart + 9);
                String snakesStr = json.substring(snakesStart + 10, snakesEnd);

                // Dividir por serpientes (cada una es un objeto {})
                int depth = 0;
                int start = 0;
                for (int i = 0; i < snakesStr.length(); i++) {
                    char c = snakesStr.charAt(i);
                    if (c == '{')
                        depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            String snakeObj = snakesStr.substring(start, i + 1);
                            parseSnake(snakeObj, board, WIDTH, HEIGHT);
                            start = i + 2; // saltar },
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error parseando JSON: " + e.getMessage());
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

    private void parseSnake(String snakeObj, char[][] board, int WIDTH, int HEIGHT) {
        try {
            // Extraer ID
            int idStart = snakeObj.indexOf("\"id\":");
            int idEnd = snakeObj.indexOf(",", idStart);
            int playerId = Integer.parseInt(snakeObj.substring(idStart + 5, idEnd).trim());
            char symbol = (char) ('0' + (playerId % 10));

            // Extraer body
            int bodyStart = snakeObj.indexOf("\"body\":[");
            int bodyEnd = findMatchingBracket(snakeObj, bodyStart + 7);
            String bodyStr = snakeObj.substring(bodyStart + 8, bodyEnd);

            String[] points = bodyStr.split("\\],\\[");
            boolean isHead = true;
            for (String point : points) {
                point = point.replace("[", "").replace("]", "");
                if (!point.isEmpty()) {
                    String[] coords = point.split(",");
                    if (coords.length == 2) {
                        int x = Integer.parseInt(coords[0].trim());
                        int y = Integer.parseInt(coords[1].trim());
                        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
                            if (isHead) {
                                board[y][x] = symbol; // Cabeza con el ID
                                isHead = false;
                            } else {
                                board[y][x] = 's'; // Cuerpo
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parseando serpiente: " + e.getMessage());
        }
    }

    private int findMatchingBracket(String str, int start) {
        int depth = 1;
        for (int i = start + 1; i < str.length(); i++) {
            if (str.charAt(i) == '[' || str.charAt(i) == '{')
                depth++;
            else if (str.charAt(i) == ']' || str.charAt(i) == '}') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return str.length() - 1;
    }

    public void sendJoin(String username) {
        if (out != null) {
            out.println("JOIN " + username);
        }
    }

    public void sendDirection(String dir) {
        if (out != null && running) {
            dir = dir.trim().toUpperCase();

            // Validación anti-suicidio: evitar direcciones opuestas
            boolean isOppositeDirection = false;
            switch (currentDirection) {
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

            if (!isOppositeDirection) {
                out.println("INPUT " + dir);
                String previousDirection = currentDirection;
                currentDirection = dir; // Actualizar la dirección actual
                System.out.println("Cliente: Enviando dirección " + dir + " (anterior: " + previousDirection + ")");
            } else {
                System.out.println("Cliente: Bloqueado movimiento opuesto " + dir + " vs " + currentDirection);
            }
        }
    }

    public void sendQuit() {
        if (out != null && running) {
            out.println("QUIT");
        }
    }

    public void sendLevelCommand(String levelCmd) {
        if (out != null && running) {
            out.println("LEVEL " + levelCmd);
            System.out.println("Cliente: Enviando comando de nivel " + levelCmd);
        }
    }

    public void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}