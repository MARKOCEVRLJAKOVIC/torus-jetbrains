package dev.marko.engine;

import dev.marko.exception.GameOverException;

import java.util.Random;

public class SnakeGameEngine implements GameEngine {


    public SnakeGameEngine(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimenzije moraju biti pozitivni cijeli brojevi.");
        }
        this.width  = width;
        this.height = height;
        this.S      = (long) width * height;
        this.stepLimit = 35L * S;

        Random rng = new Random();

        // Nasumična početna pozicija zmije
        this.snakeX = rng.nextInt(width);
        this.snakeY = rng.nextInt(height);

        // Nasumična pozicija jabuke — mora biti drugačija od zmije
        int ax, ay;
        do {
            ax = rng.nextInt(width);
            ay = rng.nextInt(height);
        } while (ax == snakeX && ay == snakeY);

        this.appleX = ax;
        this.appleY = ay;

        this.stepCount = 0;
        this.gameWon   = false;

        System.out.printf("[ENGINE] Inicijalizovano: ekran=%dx%d, S=%d, limit=%d koraka%n",
                width, height, S, stepLimit);
        System.out.printf("[ENGINE] Zmija startuje na (%d,%d), jabuka na (%d,%d)%n",
                snakeX, snakeY, appleX, appleY);
        System.out.println("[ENGINE] ⚠ Ove informacije su SKRIVENE od solver-a!");
        System.out.println("[ENGINE] " + "─".repeat(50));
    }

    public SnakeGameEngine(int width, int height, long seed) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimenzije moraju biti pozitivni cijeli brojevi.");
        }
        this.width  = width;
        this.height = height;
        this.S      = (long) width * height;
        this.stepLimit = 35L * S;

        Random rng = new Random(seed);

        this.snakeX = rng.nextInt(width);
        this.snakeY = rng.nextInt(height);

        int ax, ay;
        do {
            ax = rng.nextInt(width);
            ay = rng.nextInt(height);
        } while (ax == snakeX && ay == snakeY);

        this.appleX = ax;
        this.appleY = ay;

        this.stepCount = 0;
        this.gameWon   = false;

        System.out.printf("[ENGINE] Inicijalizovano (seed=%d): ekran=%dx%d, S=%d, limit=%d koraka%n",
                seed, width, height, S, stepLimit);
        System.out.printf("[ENGINE] Zmija startuje na (%d,%d), jabuka na (%d,%d)%n",
                snakeX, snakeY, appleX, appleY);
        System.out.println("[ENGINE] ⚠ Ove informacije su SKRIVENE od solver-a!");
        System.out.println("[ENGINE] " + "─".repeat(50));
    }

    private final int width;   // A — širina ekrana
    private final int height;  // B — visina ekrana
    private final long S;      // A * B
    private final long stepLimit; // 35 * S

    private int snakeX;  // trenutna X pozicija zmije  (0-indeksovano)
    private int snakeY;  // trenutna Y pozicija zmije  (0-indeksovano)
    private int appleX;  // X pozicija jabuke
    private int appleY;  // Y pozicija jabuke

    private long stepCount; // ukupan broj poslatih komandi
    private boolean gameWon;


    @Override
    public boolean sendSignal(Direction direction) {
        if (gameWon) {
            throw new IllegalStateException("Igra je već završena. Kreiraj novu instancu.");
        }

        stepCount++;
        if (stepCount > stepLimit) {
            throw new GameOverException(stepCount, stepLimit);
        }

        switch (direction) {
            case UP:    snakeY = Math.floorMod(snakeY - 1, height); break;
            case DOWN:  snakeY = Math.floorMod(snakeY + 1, height); break;
            case LEFT:  snakeX = Math.floorMod(snakeX - 1, width);  break;
            case RIGHT: snakeX = Math.floorMod(snakeX + 1, width);  break;
        }

        // Provjeri da li je zmija na poziciji jabuke
        if (snakeX == appleX && snakeY == appleY) {
            gameWon = true;
            System.out.printf("[ENGINE] Jabuka pronađena nakon %d koraka! (limit bio %d)%n",
                    stepCount, stepLimit);
            System.out.printf("[ENGINE] Efikasnost: %.2f%% od dozvoljenih koraka iskorišteno%n",
                    100.0 * stepCount / stepLimit);
            return true;
        }

        return false;
    }



    /** Koliko koraka je do sada potrošeno. */
    public long getStepCount() { return stepCount; }

    /** Koliko koraka je ukupno dozvoljeno (35 * S). */
    public long getStepLimit() { return stepLimit; }

    /** Da li je igra već dobivena. */
    public boolean isGameWon() { return gameWon; }

    /**
     * Ispiše trenutno stanje ekrana u konzolu (ASCII art).
     * NAPOMENA: Ovu metodu NE TREBA pozivati iz solver-a —
     *           solver ne smije znati ove informacije!
     *           Koristi je samo za debug/vizualizaciju tokom razvoja.
     */
    public void debugPrintBoard() {
        System.out.println("[ENGINE DEBUG] Trenutno stanje ekrana:");
        for (int y = 0; y < height; y++) {
            StringBuilder row = new StringBuilder("[ENGINE DEBUG] |");
            for (int x = 0; x < width; x++) {
                if (x == snakeX && y == snakeY && x == appleX && y == appleY) {
                    row.append('!'); // Preklapanje (ne bi trebalo biti moguće)
                } else if (x == snakeX && y == snakeY) {
                    row.append('S'); // Zmija
                } else if (x == appleX && y == appleY) {
                    row.append('A'); // Jabuka
                } else {
                    row.append('.');
                }
            }
            row.append('|');
            System.out.println(row);
        }
        System.out.printf("[ENGINE DEBUG] Koraci: %d / %d%n", stepCount, stepLimit);
    }
}