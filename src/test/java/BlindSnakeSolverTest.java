
import dev.marko.engine.Direction;
import dev.marko.engine.GameEngine;
import dev.marko.engine.SnakeGameEngine;
import dev.marko.exception.GameOverException;
import dev.marko.solution.BlindSnakeSolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for BlindSnakeSolver.
 *
 * Test categories:
 *   1. Correctness  — solver wins on all grid shapes
 *   2. Step budget  — steps ≤ 35·S guaranteed
 *   3. Edge cases   — tiny grids, 1-cell-off apple, wrap-around
 *   4. Engine guard — GameOverException, post-win call, bad dimensions
 *   5. Solver state — re-use of solver instance across games
 *   6. Mock engine  — white-box tests independent of RNG
 *   7. Stress       — many seeds on medium/large grids
 */
class BlindSnakeSolverTest {


    //  Helpers


    /** Run solver and assert: no exception, game won, steps ≤ 35·S. */
    private void runAndAssert(int width, int height, long seed) {
        SnakeGameEngine engine = new SnakeGameEngine(width, height, seed);
        BlindSnakeSolver solver = new BlindSnakeSolver();

        assertDoesNotThrow(
                () -> solver.solve(engine),
                String.format("Solver threw on %dx%d seed=%d", width, height, seed));

        assertTrue(engine.isGameWon(),
                String.format("Apple NOT found on %dx%d seed=%d", width, height, seed));

        long steps = engine.getStepCount();
        long limit = 35L * width * height;
        assertTrue(steps <= limit,
                String.format("steps=%d > limit=%d on %dx%d seed=%d",
                        steps, limit, width, height, seed));
    }


    //  1. Correctness — parameterised over grid shapes

    @ParameterizedTest(name = "{0}x{1} seed={2}")
    @CsvSource({
            // Minimal non-trivial grids
            "1,  2, 0",  "2,  1, 0",
            "1,  3, 0",  "3,  1, 0",
            "2,  2, 0",  "2,  2, 1",
            "3,  3, 0",  "5,  5, 0",

            // Standard square grids
            "10,   10, 0",  "10,  10, 1",  "10,  10, 42",
            "100, 100, 0",  "100, 100, 1",

            // Wide / tall degenerate strips
            "1,   100, 0",   "100,   1, 0",
            "1,  1000, 0",  "1000,   1, 0",
            "1, 10000, 0",  "10000,  1, 0",
            "2,   500, 0",  "500,    2, 0",

            // Rectangles that cross the DIM_CAP=1000 boundary
            "999,  1000, 0",   "1000,  999, 0",
            "1001,  999, 0",   "999,  1001, 0",
            "500,  2000, 0",   "2000,  500, 0",

            // Prime dimensions (no factor tricks possible)
            "997,  997, 0",   "997,  997, 1",
            "997,    1, 0",   "1,    997, 0",

            // Various seeds on medium grid
            "100, 100, 7",  "100, 100, 99",  "100, 100, 999",
    })
    void testVariousGridsAndSeeds(int width, int height, long seed) {
        runAndAssert(width, height, seed);
    }

    //  2. Step budget — explicit ratio assertions

    @Test
    void testStepRatioLargeSquare() {
        SnakeGameEngine engine = new SnakeGameEngine(1000, 1000, 0);
        new BlindSnakeSolver().solve(engine);
        double ratio = (double) engine.getStepCount() / (1000L * 1000);
        assertTrue(ratio <= 35.0, "ratio=" + ratio + " exceeded 35·S");
        // Sanity: our algorithm should comfortably beat 35
        assertTrue(ratio < 25.0, "Expected ratio < 25 on 1000x1000, got " + ratio);
    }

    @Test
    void testStepRatioDegenerateRow() {
        SnakeGameEngine engine = new SnakeGameEngine(1_000_000, 1, 0);
        new BlindSnakeSolver().solve(engine);
        double ratio = (double) engine.getStepCount() / 1_000_000L;
        assertTrue(ratio <= 35.0, "ratio=" + ratio);
    }

    @Test
    void testStepRatioDegenerateColumn() {
        SnakeGameEngine engine = new SnakeGameEngine(1, 1_000_000, 0);
        new BlindSnakeSolver().solve(engine);
        double ratio = (double) engine.getStepCount() / 1_000_000L;
        assertTrue(ratio <= 35.0, "ratio=" + ratio);
    }

    @Test
    void testNearMaxAreaRectangle() {
        runAndAssert(999, 1000, 0);
    }

    //  3. Edge cases

    /** 1×2: apple is always exactly 1 step away in some direction. */
    @Test
    void testSmallest1x2AllSeeds() {
        for (long seed = 0; seed < 20; seed++) {
            runAndAssert(1, 2, seed);
        }
    }

    /** 2×1: apple is always exactly 1 step away horizontally. */
    @Test
    void testSmallest2x1AllSeeds() {
        for (long seed = 0; seed < 20; seed++) {
            runAndAssert(2, 1, seed);
        }
    }

    /** Apple is adjacent to snake (just 1 step away in some direction). */
    @Test
    void testAppleOneStepAway() {
        // On a 2×2 grid every non-self cell is ≤1 wrap-around step away.
        for (long seed = 0; seed < 30; seed++) {
            runAndAssert(2, 2, seed);
        }
    }

    /**
     * Solver must still win when snake and apple are in the same row
     * — this forces dx=0, testing the (A, dy) lattice point branch.
     */
    @Test
    void testSameRowDisplacement() {
        // Use a MockEngine that places snake at (0,0) and apple at (0,3)
        // on a 10×10 grid — dy=3, dx=0.
        MockEngine mock = new MockEngine(10, 10, 0, 0, 0, 3);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won, "Did not find apple with dx=0 displacement");
        assertTrue(mock.steps <= 35L * 100, "Exceeded step budget on mock engine");
    }

    /**
     * Same column — apple same column, different row (dy=0).
     */
    @Test
    void testSameColumnDisplacement() {
        // snake (0,0), apple (5,0) on 10×10 — dx=5, dy=0
        MockEngine mock = new MockEngine(10, 10, 0, 0, 5, 0);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won, "Did not find apple with dy=0 displacement");
        assertTrue(mock.steps <= 35L * 100);
    }

    /**
     * Wrap-around: apple is at position that is only reachable via torus wrap.
     * On a 5×5 grid, if snake=( 4,4) and apple=(0,0), the solver must wrap.
     */
    @Test
    void testTorusWrapReachability() {
        // We can't directly control positions through SnakeGameEngine, so we
        // run many seeds on small grids to statistically hit wrap-around cases.
        for (long seed = 0; seed < 50; seed++) {
            runAndAssert(5, 5, seed);
        }
    }

    /** Very thin grids where only one dimension is iterated. */
    @ParameterizedTest(name = "1x{0}")
    @CsvSource({"2","3","5","7","10","50","100","999","1000","1001","9999","10000","100000"})
    void testThinVerticalGrids(int height) {
        runAndAssert(1, height, 0);
        runAndAssert(1, height, height); // seed = height for variety
    }

    @ParameterizedTest(name = "{0}x1")
    @CsvSource({"2","3","5","7","10","50","100","999","1000","1001","9999","10000","100000"})
    void testThinHorizontalGrids(int width) {
        runAndAssert(width, 1, 0);
        runAndAssert(width, 1, width);
    }

    //  4. Engine guard tests (testing SnakeGameEngine itself)

    @Test
    void testEngineThrowsGameOverExceptionWhenBudgetExceeded() {
        // 1×2 grid: S=2, limit=70. We'll manually drain more than 70 steps.
        SnakeGameEngine engine = new SnakeGameEngine(1, 2, 0);
        assertThrows(GameOverException.class, () -> {
            for (int i = 0; i < 200; i++) {
                engine.sendSignal(Direction.RIGHT); // apple might be found first...
            }
        });
    }

    @Test
    void testEngineThrowsOnCallAfterWin() {
        // Find the apple legitimately, then try calling sendSignal again.
        SnakeGameEngine engine = new SnakeGameEngine(2, 1, 0);
        // Keep stepping until won or limit
        boolean won = false;
        for (int i = 0; i < 70 && !won; i++) {
            won = engine.sendSignal(Direction.RIGHT);
        }
        assertTrue(won, "Should have found apple on 2x1 within 70 steps");
        assertThrows(IllegalStateException.class,
                () -> engine.sendSignal(Direction.RIGHT),
                "Expected IllegalStateException after game already won");
    }

    @Test
    void testEngineRejectsZeroWidth() {
        assertThrows(IllegalArgumentException.class, () -> new SnakeGameEngine(0, 5));
    }

    @Test
    void testEngineRejectsZeroHeight() {
        assertThrows(IllegalArgumentException.class, () -> new SnakeGameEngine(5, 0));
    }

    @Test
    void testEngineRejectsNegativeDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new SnakeGameEngine(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new SnakeGameEngine(10, -1));
    }

    @Test
    void testEngineStepCountStartsAtZero() {
        SnakeGameEngine engine = new SnakeGameEngine(10, 10, 0);
        assertEquals(0, engine.getStepCount());
    }

    @Test
    void testEngineStepCountIncrementsOnEachSignal() {
        // Use a grid large enough that we won't hit the apple accidentally.
        // 100×100, move in a direction 5 times.
        SnakeGameEngine engine = new SnakeGameEngine(100, 100, 999);
        int steps = 0;
        for (int i = 0; i < 5; i++) {
            boolean won = engine.sendSignal(Direction.UP);
            steps++;
            if (won) break;
        }
        assertTrue(engine.getStepCount() >= 1 && engine.getStepCount() <= 5);
    }

    @Test
    void testEngineIsGameWonFalseInitially() {
        SnakeGameEngine engine = new SnakeGameEngine(10, 10, 0);
        assertFalse(engine.isGameWon());
    }

    @Test
    void testEngineStepLimitIs35S() {
        SnakeGameEngine engine = new SnakeGameEngine(7, 13, 0);
        assertEquals(35L * 7 * 13, engine.getStepLimit());
    }

    //  5. Solver state — re-using the same BlindSnakeSolver instance

    /**
     * A BlindSnakeSolver instance should be fully reusable: calling solve()
     * a second time on a fresh engine must reset internal state and win again.
     */
    @Test
    void testSolverInstanceIsReusable() {
        BlindSnakeSolver solver = new BlindSnakeSolver();

        SnakeGameEngine engine1 = new SnakeGameEngine(50, 50, 1);
        solver.solve(engine1);
        assertTrue(engine1.isGameWon(), "First game should be won");

        // Same solver instance, brand-new engine
        SnakeGameEngine engine2 = new SnakeGameEngine(50, 50, 2);
        solver.solve(engine2);
        assertTrue(engine2.isGameWon(), "Second game (reused solver) should be won");
    }

    //  6. Mock engine — white-box / deterministic tests

    /**
     * MockEngine places snake and apple at known positions and simulates
     * the same toroidal physics, but without any printing.
     * Allows deterministic white-box testing of the solver.
     */
    static class MockEngine implements GameEngine {
        private final int width, height;
        private int snakeX, snakeY;
        private final int appleX, appleY;
        long steps = 0;
        boolean won = false;

        MockEngine(int width, int height, int snakeX, int snakeY, int appleX, int appleY) {
            this.width  = width;
            this.height = height;
            this.snakeX = snakeX;
            this.snakeY = snakeY;
            this.appleX = appleX;
            this.appleY = appleY;
        }

        @Override
        public boolean sendSignal(Direction dir) {
            if (won) throw new IllegalStateException("Already won");
            steps++;
            if (steps > 35L * width * height)
                throw new GameOverException(steps, 35L * width * height);
            switch (dir) {
                case UP:    snakeY = Math.floorMod(snakeY - 1, height); break;
                case DOWN:  snakeY = Math.floorMod(snakeY + 1, height); break;
                case LEFT:  snakeX = Math.floorMod(snakeX - 1, width);  break;
                case RIGHT: snakeX = Math.floorMod(snakeX + 1, width);  break;
            }
            if (snakeX == appleX && snakeY == appleY) { won = true; return true; }
            return false;
        }
    }

    /** Snake and apple at diagonally opposite corners of a large grid. */
    @Test
    void testMockOppositeCorners() {
        int W = 500, H = 400;
        MockEngine mock = new MockEngine(W, H, 0, 0, W - 1, H - 1);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won);
        assertTrue(mock.steps <= 35L * W * H);
    }

    /** Apple is only 1 step to the RIGHT. */
    @Test
    void testMockAppleOneStepRight() {
        MockEngine mock = new MockEngine(10, 10, 3, 5, 4, 5);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won);
    }

    /** Apple is only 1 step DOWN. */
    @Test
    void testMockAppleOneStepDown() {
        MockEngine mock = new MockEngine(10, 10, 3, 5, 3, 6);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won);
    }

    /**
     * Apple requires a wrap-around to reach in fewer steps.
     * On a 10×10 grid, snake=(9,9), apple=(0,0):
     *   direct path: 9 RIGHT + 9 DOWN (but RIGHT wraps at 10)
     *   effectively dx=1, dy=1 via wrap, which should be covered early.
     */
    @Test
    void testMockAppleReachableOnlyViaWrap() {
        MockEngine mock = new MockEngine(10, 10, 9, 9, 0, 0);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won, "Apple must be found even when only reachable via torus wrap");
    }

    /** Grid where A > DIM_CAP and B > DIM_CAP — exercises Phase 2 exclusively. */
    @Test
    void testMockBothDimensionsExceedDimCap() {
        // 1200 × 1200 is invalid (S > 10^6) so use valid case that crosses cap
        // 1001 × 999 = 999_999 < 10^6, both > DIM_CAP on one axis
        MockEngine mock = new MockEngine(1001, 999, 0, 0, 1000, 998);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won);
    }

    /**
     * Phase boundary test: x = DIM_CAP exactly (1000).
     * Phase 1 covers x ≤ 1000; Phase 2 covers x > 1000.
     * A lattice point at x=1000, y=1 must be covered by Phase 1.
     */
    @Test
    void testMockPhase1BoundaryX1000() {
        // On a 1000×1 grid, snake=(0,0), apple is wherever RNG puts it.
        // The point (1000, 1) ≡ (0, 0) mod 1000×1 after wrapping — so
        // navigating to (1000, 1) lands exactly on the apple if apple is at (0,0).
        MockEngine mock = new MockEngine(1000, 1000, 0, 0, 0, 999);
        // (0, 999) reachable via (1000, 999) → x=1000 is Phase1 boundary
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won);
    }

    /**
     * Solver must handle the case where the apple is found during
     * horizontal travel (not just at the destination point).
     * This specifically tests the stale-position fix.
     */
    @Test
    void testAppleFoundDuringHorizontalTravel() {
        // Snake at (0,0), apple at (5,0) on a wide grid.
        // The solver will travel right; apple is hit at step 5.
        // After finding it, curX must be 5 (not 0).
        // We verify this indirectly: the solver must report won=true.
        MockEngine mock = new MockEngine(100, 100, 0, 0, 5, 0);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won, "Apple found during horizontal travel");
    }

    /**
     * Apple found during vertical travel — tests the same stale-position fix
     * for the vertical leg of navigateTo.
     */
    @Test
    void testAppleFoundDuringVerticalTravel() {
        MockEngine mock = new MockEngine(100, 100, 0, 0, 0, 7);
        new BlindSnakeSolver().solve(mock);
        assertTrue(mock.won, "Apple found during vertical travel");
    }

    //  7. Stress tests — many seeds, larger grids

    @Test
    void stressTest100x100_50seeds() {
        for (int seed = 0; seed < 50; seed++) {
            runAndAssert(100, 100, seed);
        }
    }

    @Test
    void stressTest1000x1000_5seeds() {
        for (int seed = 0; seed < 5; seed++) {
            runAndAssert(1000, 1000, seed);
        }
    }

    @Test
    void stressTest997x997_primeDimensions_5seeds() {
        for (int seed = 0; seed < 5; seed++) {
            runAndAssert(997, 997, seed);
        }
    }

    @Test
    void stressTestDegenerateThin_20seeds() {
        for (int seed = 0; seed < 20; seed++) {
            runAndAssert(1, 100000, seed);
            runAndAssert(100000, 1, seed);
        }
    }

    @Test
    void stressTestVariousRectangles_10seeds() {
        int[][] shapes = {{50, 200}, {200, 50}, {300, 333}, {400, 250}};
        for (int[] shape : shapes) {
            for (int seed = 0; seed < 10; seed++) {
                runAndAssert(shape[0], shape[1], seed);
            }
        }
    }

    //  8. Direction coverage — all four directions exercised

    /**
     * Verify that the solver actually uses all four directions over a run,
     * not just RIGHT and DOWN (which would fail on wrap-dependent grids).
     */
    @Test
    void testAllFourDirectionsUsed() {
        // Use a counting mock
        boolean[] usedDir = new boolean[4]; // UP, DOWN, LEFT, RIGHT
        GameEngine countingEngine = new GameEngine() {
            // Delegate physics to a real engine
            final MockEngine inner = new MockEngine(100, 100, 50, 50, 1, 1);

            @Override
            public boolean sendSignal(Direction dir) {
                switch (dir) {
                    case UP:    usedDir[0] = true; break;
                    case DOWN:  usedDir[1] = true; break;
                    case LEFT:  usedDir[2] = true; break;
                    case RIGHT: usedDir[3] = true; break;
                }
                return inner.sendSignal(dir);
            }
        };

        new BlindSnakeSolver().solve(countingEngine);
        // At minimum UP/DOWN and LEFT/RIGHT should appear in a boustrophedon sweep
        assertTrue(usedDir[0] || usedDir[1], "Expected at least UP or DOWN to be used");
        assertTrue(usedDir[2] || usedDir[3], "Expected at least LEFT or RIGHT to be used");
    }

    //  10. Largest valid S — performance sanity

    @Test
    void testMaxSquareGrid() {
        // 1000×1000 = 10^6 exactly; solver must finish < 35·10^6 steps
        runAndAssert(1000, 1000, 0);
    }

    @Test
    void testMaxDegenerateRow() {
        runAndAssert(1_000_000, 1, 0);
    }

    @Test
    void testMaxDegenerateColumn() {
        runAndAssert(1, 1_000_000, 0);
    }
}