package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.PriorityQueue;

/**
 * D8 flow-direction hydrology for river detection from elevation maps.
 * Port of {@code postprocessing.py} D8 flow + flow accumulation algorithms.
 * Depression filling uses priority-flood (Barnes et al., optional).
 */
public final class RiverDetector {

    private static final int[] DR = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DC = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final float SQRT2 = (float) Math.sqrt(2.0);
    private static final float[] DIST = {1, SQRT2, 1, SQRT2, 1, SQRT2, 1, SQRT2};

    private RiverDetector() {}

    /**
     * Compute D8 flow directions, sinks and ocean masks.
     *
     * @param elev   flat elevation in meters, length H*W
     * @param H      height
     * @param W      width
     * @param rr     output: row index of downstream cell (clamped to [0,H-1])
     * @param cc     output: col index of downstream cell (clamped to [0,W-1])
     * @param isSink output: true if cell is a sink (ocean or no downhill exit)
     * @param kmax   output: chosen D8 direction 0-7
     */
    public static void d8Flow(float[] elev, int H, int W,
                               int[] rr, int[] cc, boolean[] isSink, int[] kmax) {
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float z = elev[idx];

                float bestSlope = -Float.MAX_VALUE;
                int   bestDir   = 0;
                int   bestR     = r;
                int   bestC     = c;

                boolean isOcean = Float.isNaN(z) || z <= 0f;

                if (isOcean) {
                    rr[idx] = r; cc[idx] = c;
                    isSink[idx] = true;
                    kmax[idx] = 0;
                    continue;
                }

                boolean hasOceanNeighbor = false;

                for (int d = 0; d < 8; d++) {
                    int nr = r + DR[d];
                    int nc = c + DC[d];
                    if (nr < 0 || nr >= H || nc < 0 || nc >= W) continue;

                    float nz = elev[nr * W + nc];
                    float slope = (z - nz) / DIST[d];

                    boolean nzOcean = Float.isNaN(nz) || nz <= 0f;
                    if (nzOcean) {
                        hasOceanNeighbor = true;
                        slope = Float.MAX_VALUE - 1f; // prefer draining into ocean
                    } else if (slope < 1e-3f) {
                        slope = -Float.MAX_VALUE;
                    }

                    if (slope > bestSlope) {
                        bestSlope = slope;
                        bestDir   = d;
                        bestR     = nr;
                        bestC     = nc;
                    }
                }

                boolean sink;
                if (hasOceanNeighbor) {
                    sink = false; // drains to ocean
                } else {
                    sink = bestSlope < 0f; // no downhill route (and no ocean)
                }

                rr[idx]    = Math.max(0, Math.min(H - 1, bestR));
                cc[idx]    = Math.max(0, Math.min(W - 1, bestC));
                isSink[idx] = sink;
                kmax[idx]   = bestDir;
            }
        }
    }

    /**
     * Compute upstream contributing area via D8 flow routing.
     * Cells are processed from high to low elevation.
     *
     * @param elev   elevation, length H*W
     * @param H      height
     * @param W      width
     * @param rr     downstream row indices from {@link #d8Flow}
     * @param cc     downstream col indices from {@link #d8Flow}
     * @param isSink sink mask from {@link #d8Flow}
     * @return flow accumulation array, length H*W (ocean cells = 0)
     */
    public static float[] flowAccumulation(float[] elev, int H, int W,
                                            int[] rr, int[] cc, boolean[] isSink) {
        int N = H * W;
        float[] A = new float[N];
        boolean[] invalid = new boolean[N];
        int validCount = 0;

        for (int i = 0; i < N; i++) {
            invalid[i] = Float.isNaN(elev[i]) || elev[i] <= 0f;
            if (!invalid[i]) {
                A[i] = 1f;
                validCount++;
            }
        }

        int[] sortedIdx = new int[validCount];
        int si = 0;
        for (int i = 0; i < N; i++) {
            if (!invalid[i]) sortedIdx[si++] = i;
        }

        Integer[] order = new Integer[validCount];
        for (int i = 0; i < validCount; i++) order[i] = sortedIdx[i];
        java.util.Arrays.sort(order, (a, b) -> Float.compare(elev[b], elev[a]));

        for (int i = 0; i < validCount; i++) {
            int idx = order[i];
            if (isSink[idx]) continue;

            int ti = Math.max(0, Math.min(H - 1, rr[idx]));
            int tj = Math.max(0, Math.min(W - 1, cc[idx]));
            int target = ti * W + tj;

            if (!invalid[target]) {
                A[target] += A[idx];
            }
        }

        return A;
    }

    /**
     * Priority-flood depression filling. Fills pits so water can drain.
     * Ocean cells (elev &lt;= 0 or NaN) are treated as barriers/outlets.
     *
     * @param elev         elevation, length H*W (modified in place)
     * @param H            height
     * @param W            width
     * @param epsilon      tiny gradient across flats for drainage (e.g. 1e-3)
     * @param maxRaise     maximum basin fill depth (null = no limit)
     */
    public static void fillDepressions(float[] elev, int H, int W,
                                        float epsilon, Float maxRaise) {
        int N = H * W;
        boolean[] ocean = new boolean[N];
        for (int i = 0; i < N; i++) {
            ocean[i] = Float.isNaN(elev[i]) || elev[i] <= 0f;
        }

        boolean[] visited = new boolean[N];
        float[] basinMin = new float[N];
        java.util.Arrays.fill(basinMin, Float.POSITIVE_INFINITY);

        PriorityQueue<Cell> heap = new PriorityQueue<>();

        int[][] nbrs8 = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1}};
        int[][] nbrs4 = {{-1,0},{1,0},{0,-1},{0,1}};

        // Seed: border cells
        for (int r = 0; r < H; r++) {
            for (int c : new int[]{0, W - 1}) {
                int idx = r * W + c;
                if (!ocean[idx] && !visited[idx]) {
                    heap.offer(new Cell(elev[idx], r, c));
                    visited[idx] = true;
                    basinMin[idx] = elev[idx];
                }
            }
        }
        for (int c = 0; c < W; c++) {
            for (int r : new int[]{0, H - 1}) {
                int idx = r * W + c;
                if (!ocean[idx] && !visited[idx]) {
                    heap.offer(new Cell(elev[idx], r, c));
                    visited[idx] = true;
                    basinMin[idx] = elev[idx];
                }
            }
        }

        // Seed: coastal cells (adjacent to ocean)
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                if (ocean[idx] || visited[idx]) continue;

                boolean coastal = false;
                for (int[] nb : nbrs8) {
                    int nr = r + nb[0], nc = c + nb[1];
                    if (nr < 0 || nr >= H || nc < 0 || nc >= W) continue;
                    if (ocean[nr * W + nc]) { coastal = true; break; }
                }
                if (coastal) {
                    float seedE = Math.max(elev[idx], 0f);
                    heap.offer(new Cell(seedE, r, c));
                    visited[idx] = true;
                    basinMin[idx] = elev[idx];
                }
            }
        }

        while (!heap.isEmpty()) {
            Cell cell = heap.poll();
            float zCurr = cell.z;
            int r = cell.r, c = cell.c;
            float bmCurr = basinMin[r * W + c];

            for (int[] nb : nbrs8) {
                int nr = r + nb[0], nc = c + nb[1];
                if (nr < 0 || nr >= H || nc < 0 || nc >= W) continue;
                int nidx = nr * W + nc;
                if (visited[nidx] || ocean[nidx]) continue;

                float zNei = elev[nidx];
                float bmNext = Math.min(bmCurr, zNei);

                if (zNei <= zCurr) {
                    float newZ = zCurr + epsilon;
                    if (maxRaise != null) {
                        float maxLevel = bmCurr + maxRaise;
                        if (newZ > maxLevel) newZ = maxLevel;
                    }
                    if (newZ > zNei) elev[nidx] = newZ;
                    heap.offer(new Cell(elev[nidx], nr, nc));
                } else {
                    heap.offer(new Cell(zNei, nr, nc));
                }
                visited[nidx] = true;
                basinMin[nidx] = bmNext;
            }
        }
    }

    /**
     * Detect river pixels from elevation data.
     *
     * @param elev           flat elevation in meters, length H*W
     * @param H              height
     * @param W              width
     * @param flowThreshold  minimum contributing area for a river (in pixel units)
     * @param fillFirst      if true, run depression filling before flow routing
     * @return boolean[H*W], true = river pixel
     */
    public static boolean[] detectRivers(float[] elev, int H, int W,
                                          float flowThreshold, boolean fillFirst) {
        int N = H * W;
        float[] work = new float[N];
        System.arraycopy(elev, 0, work, 0, N);

        if (fillFirst) {
            fillDepressions(work, H, W, 1e-3f, null);
        }

        int[] rr = new int[N];
        int[] cc = new int[N];
        boolean[] isSink = new boolean[N];
        int[] kmax = new int[N];

        d8Flow(work, H, W, rr, cc, isSink, kmax);

        float[] flowAcc = flowAccumulation(work, H, W, rr, cc, isSink);

        boolean[] rivers = new boolean[N];
        for (int i = 0; i < N; i++) {
            if (!Float.isNaN(elev[i]) && elev[i] > 0f) {
                rivers[i] = flowAcc[i] > flowThreshold;
            }
        }
        return rivers;
    }

    /**
     * Upsample a boolean 2D river mask via bilinear interpolation + threshold.
     *
     * @param src    flat boolean array, length srcH*srcW
     * @param srcW   source width
     * @param srcH   source height
     * @param dstW   destination width
     * @param dstH   destination height
     * @param scale  integer scale factor (dstW = srcW * scale or close)
     * @return flat boolean array, length dstH*dstW
     */
    public static boolean[] upsampleRiverMask(boolean[] src, int srcW, int srcH,
                                               int dstW, int dstH, int scale) {
        boolean[] dst = new boolean[dstH * dstW];
        float scaleX = (float)(srcW - 1) / (float)(Math.max(1, dstW - 1));
        float scaleY = (float)(srcH - 1) / (float)(Math.max(1, dstH - 1));

        for (int r = 0; r < dstH; r++) {
            for (int c = 0; c < dstW; c++) {
                float sx = c * scaleX;
                float sy = r * scaleY;
                int sx0 = (int) sx, sy0 = (int) sy;
                int sx1 = Math.min(sx0 + 1, srcW - 1);
                int sy1 = Math.min(sy0 + 1, srcH - 1);
                float fx = sx - sx0, fy = sy - sy0;

                float v00 = src[sy0 * srcW + sx0] ? 1f : 0f;
                float v10 = src[sy0 * srcW + sx1] ? 1f : 0f;
                float v01 = src[sy1 * srcW + sx0] ? 1f : 0f;
                float v11 = src[sy1 * srcW + sx1] ? 1f : 0f;

                float val = (v00 * (1f - fx) * (1f - fy))
                          + (v10 *        fx  * (1f - fy))
                          + (v01 * (1f - fx) *        fy)
                          + (v11 *        fx  *        fy);

                dst[r * dstW + c] = val >= 0.5f;
            }
        }
        return dst;
    }

    /** Priority-flood heap cell ordering. */
    private static final class Cell implements Comparable<Cell> {
        final float z;
        final int r, c;
        Cell(float z, int r, int c) { this.z = z; this.r = r; this.c = c; }
        @Override public int compareTo(Cell o) { return Float.compare(this.z, o.z); }
    }
}
