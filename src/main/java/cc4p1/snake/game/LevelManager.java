package cc4p1.snake.game;

import java.io.*;
import java.util.*;

/**
 * Gestor de niveles que carga configuraciones de mapas desde archivos
 */
public class LevelManager {
    private static final String LEVELS_DIR = "levels/";

    // 🔹 NUEVO: describimos cada nivel con archivo + tick + maxFruits
    private static final class LevelInfo {
        final String file;
        final int tickRateHz;
        final int maxFruits;
        LevelInfo(String file, int tickRateHz, int maxFruits) {
            this.file = file; this.tickRateHz = tickRateHz; this.maxFruits = maxFruits;
        }
    }

    private final List<LevelInfo> levels = new ArrayList<>();
    private int currentLevel;

    public LevelManager() {
        this.currentLevel = 0;
        loadAvailableLevels();
    }

    /** Carga la lista de niveles disponibles */
    private void loadAvailableLevels() {
        // 🔹 Ajusta valores a tu gusto (ejemplo: dificultad creciente)
        levels.add(new LevelInfo("level1.txt", 3, 3));
        levels.add(new LevelInfo("level2.txt", 3, 3));
        levels.add(new LevelInfo("level3.txt", 5, 5));
        levels.add(new LevelInfo("level4.txt", 10, 7));
        levels.add(new LevelInfo("level5.txt", 12, 10));

        System.out.println("Niveles disponibles: " + levels.size());
    }

    /** Carga un mapa desde archivo */
    public char[][] loadLevel(String levelFile) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(LEVELS_DIR + levelFile)) {
            if (is == null) return createDefaultLevel();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            List<String> lines = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null; ) lines.add(line);
            if (lines.isEmpty()) return createDefaultLevel();

            int height = lines.size();
            int width  = lines.stream().mapToInt(String::length).max().orElse(0); // right-pad por si hay filas de distinta longitud
            char[][] map = new char[height][width];
            for (int y = 0; y < height; y++) {
                String row = lines.get(y);
                for (int x = 0; x < width; x++) {
                    map[y][x] = (x < row.length() ? row.charAt(x) : ' ');
                }
            }
            System.out.println("Nivel cargado: " + levelFile + " (" + width + "x" + height + ")");
            return map;

        } catch (IOException e) {
            System.err.println("Error cargando nivel " + levelFile + ": " + e.getMessage());
            return createDefaultLevel();
        }
    }

    /** Crea un nivel por defecto con bordes */
    private char[][] createDefaultLevel() {
        char[][] map = new char[12][30];
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 30; x++) {
                map[y][x] = (y == 0 || y == 11 || x == 0 || x == 29) ? '#' : ' ';
            }
        }
        System.out.println("Usando nivel por defecto");
        return map;
    }

    /** 🔹 API igual que antes, pero usando LevelInfo */

    public char[][] getCurrentLevel() {
        if (levels.isEmpty()) return createDefaultLevel();
        return loadLevel(levels.get(currentLevel).file);
    }

    public void nextLevel() {
        if (!levels.isEmpty()) {
            currentLevel = (currentLevel + 1) % levels.size();
            System.out.println("Cambiando a nivel: " + (currentLevel + 1));
        }
    }

    public int getCurrentLevelNumber() { return currentLevel + 1; }
    public int getTotalLevels()        { return levels.size(); }

    public void setLevel(int levelNumber) {
        if (levelNumber >= 1 && levelNumber <= levels.size()) {
            currentLevel = levelNumber - 1;
            System.out.println("Nivel establecido: " + levelNumber);
        }
    }

    // 🔹 NUEVO: getters para tick y frutas
    public int getCurrentTickRateHz() { return levels.get(currentLevel).tickRateHz; }
    public int getCurrentMaxFruits()  { return levels.get(currentLevel).maxFruits; }
}
