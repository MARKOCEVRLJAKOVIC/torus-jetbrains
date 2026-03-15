package dev.marko.solution;

import dev.marko.engine.Direction;
import dev.marko.engine.GameEngine;
import dev.marko.engine.Solver;

/**
 * Blind Snake solver Two-Phase Capped Hyperbolic Sweep.
 *
 * <h3>Key theorem</h3>
 * <p>For any displacement (dx, dy) on torus ℤ_A × ℤ_B, there exists (x, y)
 * with x ≥ 1, y ≥ 1, x·y ≤ S s.t. x ≡ dx (mod A), y ≡ dy (mod B).</p>
 *
 * <h3>O(S) insight</h3>
 * <p>Since S &lt; 10⁶, any lattice point (x,y) with x·y ≤ S has either
 * x ≤ 1000 or y ≤ 1000 (because 1001² &gt; 10⁶). So we split into:</p>
 * <ul>
 *   <li><b>Phase 1</b>: sweep points with x ≤ 1000 (covers cases where A ≤ 1000)</li>
 *   <li><b>Phase 2</b>: sweep points with y ≤ 1000, x &gt; 1000 (covers B ≤ 1000)</li>
 * </ul>
 * <p>Both phases are <b>interleaved within each doubling shell</b> so the total
 * cost is bounded by the geometric series sum = O(S), not O(S·log S).</p>
 */
public class BlindSnakeSolver implements Solver {

    private static final long MAX_BOUND = 1L << 20;
    private static final int DIM_CAP = 1000;

    private long curX, curY;
    private boolean found;
    private GameEngine engine;

    @Override
    public void solve(GameEngine engine) {
        this.engine = engine;
        this.found = false;
        this.curX = 0;
        this.curY = 0;

        // Interleaved two-phase sweep across doubling shells.
        // Within each shell, do Phase 1 then Phase 2 so that
        // we stop as soon as the apple is found.
        boolean p1LeftToRight = true;
        boolean p2BottomToTop = true;

        for (long E = 1; E <= MAX_BOUND && !found; E *= 2) {
            long ePrev = E / 2;

            // Phase 1: columns x = 1..min(DIM_CAP, E)
            sweepShellPhase1(E, ePrev, p1LeftToRight);
            p1LeftToRight = !p1LeftToRight;

            if (found) return;

            // Phase 2: rows y = 1..min(DIM_CAP, E), only x > DIM_CAP
            sweepShellPhase2(E, ePrev, p2BottomToTop);
            p2BottomToTop = !p2BottomToTop;
        }
    }

    //  Phase 1: sweep columns x ≤ DIM_CAP
    private void sweepShellPhase1(long E, long ePrev, boolean leftToRight) {
        int xMax = (int) Math.min(DIM_CAP, E);
        boolean sweepDown = true;
        if (leftToRight) {
            for (int x = 1; x <= xMax && !found; x++)
                sweepDown = sweepColumn(x, E, ePrev, sweepDown);
        } else {
            for (int x = xMax; x >= 1 && !found; x--)
                sweepDown = sweepColumn(x, E, ePrev, sweepDown);
        }
    }

    private boolean sweepColumn(int x, long E, long ePrev, boolean sweepDown) {
        int yMin = (int) (ePrev / x) + 1;
        int yMax = (int) (E / x);
        if (yMin > yMax) return sweepDown;
        if (sweepDown) {
            for (int y = yMin; y <= yMax && !found; y++) navigateTo(x, y);
        } else {
            for (int y = yMax; y >= yMin && !found; y--) navigateTo(x, y);
        }
        return !sweepDown;
    }

    //  Phase 2: sweep rows y ≤ DIM_CAP, only x > DIM_CAP
    private void sweepShellPhase2(long E, long ePrev, boolean bottomToTop) {
        int yMax = (int) Math.min(DIM_CAP, E);
        boolean sweepRight = true;
        if (bottomToTop) {
            for (int y = 1; y <= yMax && !found; y++)
                sweepRight = sweepRow(y, E, ePrev, sweepRight);
        } else {
            for (int y = yMax; y >= 1 && !found; y--)
                sweepRight = sweepRow(y, E, ePrev, sweepRight);
        }
    }

    private boolean sweepRow(int y, long E, long ePrev, boolean sweepRight) {
        int xMinRaw = (int) (ePrev / y) + 1;
        int xMaxRaw = (int) (E / y);
        int xMin = Math.max(xMinRaw, DIM_CAP + 1);
        int xMax = xMaxRaw;
        if (xMin > xMax) return sweepRight;
        if (sweepRight) {
            for (int x = xMin; x <= xMax && !found; x++) navigateTo(x, y);
        } else {
            for (int x = xMax; x >= xMin && !found; x--) navigateTo(x, y);
        }
        return !sweepRight;
    }

    //  Navigation
    private void navigateTo(long tx, long ty) {
        if (found) return;
        long dx = tx - curX;
        long dy = ty - curY;

        if (dx > 0) {
            for (long i = 0; i < dx && !found; i++)
                if (sendStep(Direction.RIGHT)) return;
        } else if (dx < 0) {
            for (long i = 0; i < -dx && !found; i++)
                if (sendStep(Direction.LEFT)) return;
        }
        if (dy > 0) {
            for (long i = 0; i < dy && !found; i++)
                if (sendStep(Direction.DOWN)) return;
        } else if (dy < 0) {
            for (long i = 0; i < -dy && !found; i++)
                if (sendStep(Direction.UP)) return;
        }
        curX = tx;
        curY = ty;
    }

    private boolean sendStep(Direction dir) {
        if (engine.sendSignal(dir)) { found = true; return true; }
        return false;
    }
}