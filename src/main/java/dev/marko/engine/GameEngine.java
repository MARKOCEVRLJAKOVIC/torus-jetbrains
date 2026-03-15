package dev.marko.engine;

public interface GameEngine {

    /**
     * Sends a movement command to the game engine.
     *
     * @param direction direction of movement
     * @return true if apple is found, false otherwise
     */
    boolean sendSignal(Direction direction);

}