package dev.marko.exception;

public class GameOverException extends RuntimeException {
    private final long stepsTaken;
    private final long stepLimit;

    public GameOverException(long stepsTaken, long stepLimit) {
        super(String.format(
                "GAME OVER! Used %d steps, limit is %d (35 * S).",
                stepsTaken, stepLimit
        ));
        this.stepsTaken = stepsTaken;
        this.stepLimit  = stepLimit;
    }

    public long getStepsTaken() { return stepsTaken; }
    public long getStepLimit()  { return stepLimit; }
}