package cc4p1.snake.game;

import java.io.*;
import java.util.*;

/**
 * Gestor de niveles que carga configuraciones de mapas desde archivos
 */
public class LevelManager {
    private static final String LEVELS_DIR = "levels/";
    private List<String> availableLevels;
    private int currentLevel;

    public LevelManager() {
        this.availableLevels = new ArrayList<>();
        this.currentLevel = 0;
        loadAvailableLevels();
    }

    /**
     * Carga la lista de niveles disponibles
     */
    private void loadAvailableLevels() {
        // Niveles por defecto
        availableLevels.add("level1.txt");
        availableLevels.add("level2.txt");
        availableLevels.add("level3.txt");

        System.out.println("Niveles disponibles: " + availableLevels.size());
    }

    /**
     * Carga un mapa desde archivo
     * 
     * @param levelFile nombre del archivo de nivel
     * @return mapa como matriz de caracteres
     */
    public char[][] loadLevel(String levelFile) {
        try {
            // Intentar cargar desde recursos primero
            InputStream is = getClass().getClassLoader().getResourceAsStream(LEVELS_DIR + levelFile);
            if (is == null) {
                // Si no existe en recursos, crear nivel por defecto
                return createDefaultLevel();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            List<String> lines = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();

            if (lines.isEmpty()) {
                return createDefaultLevel();
            }

            // Convertir a matriz
            int height = lines.size();
            int width = lines.get(0).length();
            char[][] map = new char[height][width];

            for (int y = 0; y < height; y++) {
                String mapLine = lines.get(y);
                for (int x = 0; x < width && x < mapLine.length(); x++) {
                    map[y][x] = mapLine.charAt(x);
                }
            }

            System.out.println("Nivel cargado: " + levelFile + " (" + width + "x" + height + ")");
            return map;

        } catch (IOException e) {
            System.err.println("Error cargando nivel " + levelFile + ": " + e.getMessage());
            return createDefaultLevel();
        }
    }

    /**
     * Crea un nivel por defecto con bordes
     */
    private char[][] createDefaultLevel() {
        char[][] map = new char[12][30];

        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 30; x++) {
                if (y == 0 || y == 11 || x == 0 || x == 29) {
                    map[y][x] = '['; // Paredes exteriores
                } else {
                    map[y][x] = ' '; // Espacio libre
                }
            }
        }

        System.out.println("Usando nivel por defecto");
        return map;
    }

    /**
     * Obtiene el nivel actual
     */
    public char[][] getCurrentLevel() {
        if (availableLevels.isEmpty()) {
            return createDefaultLevel();
        }
        return loadLevel(availableLevels.get(currentLevel));
    }

    /**
     * Cambia al siguiente nivel
     */
    public void nextLevel() {
        if (!availableLevels.isEmpty()) {
            currentLevel = (currentLevel + 1) % availableLevels.size();
            System.out.println("Cambiando a nivel: " + (currentLevel + 1));
        }
    }

    /**
     * Obtiene el número del nivel actual
     */
    public int getCurrentLevelNumber() {
        return currentLevel + 1;
    }

    /**
     * Obtiene el total de niveles disponibles
     */
    public int getTotalLevels() {
        return availableLevels.size();
    }

    /**
     * Establece un nivel específico
     */
    public void setLevel(int levelNumber) {
        if (levelNumber >= 1 && levelNumber <= availableLevels.size()) {
            currentLevel = levelNumber - 1;
            System.out.println("Nivel establecido: " + levelNumber);
        }
    }
}