package dev.stinger.imgshifter;

/**
 * Lanczos-3 resize, used to enlarge the car drawing. The bilinear filter of a scaled draw leaves
 * its thin lines soft. Plain Java on pixel arrays with integer maths (the head unit gets no help
 * from the GPU here), and in premultiplied colour so edges next to transparency keep their colour.
 */
final class Resample {
    private Resample() {}

    private static final int LOBES = 3, ONE = 1 << 14;

    /** Resizes plain (non-premultiplied) ARGB pixels from sw x sh to dw x dh. */
    static int[] lanczos(int[] src, int sw, int sh, int dw, int dh) {
        if (sw == dw && sh == dh) return src.clone();
        // horizontal pass: alpha and the three premultiplied channels of each source row at the new width, with 6 extra bits
        int[][] wx = weights(sw, dw), wy = weights(sh, dh);
        int[] a = new int[dw * sh], r = new int[dw * sh], g = new int[dw * sh], b = new int[dw * sh];
        for (int y = 0; y < sh; y++) {
            int row = y * sw, first = -1, last = -1;
            for (int x = 0; x < sw; x++) {
                if ((src[row + x] >>> 24) != 0) {
                    if (first < 0) first = x;
                    last = x;
                }
            }
            if (first < 0) continue;   // an empty row stays empty
            for (int x = 0; x < dw; x++) {
                int[] w = wx[x];
                int from = w[0], n = w.length - 1;
                // only transparent pixels under the filter here (past the ends it repeats the edge pixels)
                if (Math.max(from, 0) > last || Math.min(from + n - 1, sw - 1) < first) continue;
                int sa = 0, sr = 0, sg = 0, sb = 0;
                for (int k = 0; k < n; k++) {
                    int i = from + k;
                    int p = src[row + (i < 0 ? 0 : i >= sw ? sw - 1 : i)], pa = p >>> 24;
                    if (pa == 0) continue;
                    int wk = w[k + 1];
                    sa += wk * pa;
                    sr += wk * (((p >> 16) & 255) * pa / 255);
                    sg += wk * (((p >> 8) & 255) * pa / 255);
                    sb += wk * ((p & 255) * pa / 255);
                }
                int o = y * dw + x;
                a[o] = sa >> 8;
                r[o] = sr >> 8;
                g[o] = sg >> 8;
                b[o] = sb >> 8;
            }
        }
        // vertical pass: the same along each column, then back to plain colour
        int[] out = new int[dw * dh];
        final int half = 1 << 19;
        for (int y = 0; y < dh; y++) {
            int[] w = wy[y];
            int from = w[0], n = w.length - 1;
            for (int x = 0; x < dw; x++) {
                int sa = 0, sr = 0, sg = 0, sb = 0;
                for (int k = 0; k < n; k++) {
                    int i = from + k;
                    int o = (i < 0 ? 0 : i >= sh ? sh - 1 : i) * dw + x;
                    int pa = a[o];
                    if (pa == 0) continue;
                    int wk = w[k + 1];
                    sa += wk * pa;
                    sr += wk * r[o];
                    sg += wk * g[o];
                    sb += wk * b[o];
                }
                int pa = clamp((sa + half) >> 20);
                if (pa == 0) continue;
                int pr = clamp((sr + half) >> 20), pg = clamp((sg + half) >> 20), pb = clamp((sb + half) >> 20);
                out[y * dw + x] = (pa << 24) | (Math.min(255, pr * 255 / pa) << 16) | (Math.min(255, pg * 255 / pa) << 8)
                        | Math.min(255, pb * 255 / pa);
            }
        }
        return out;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }

    /**
     * For each of the d output pixels along one axis: {first source pixel, weight, weight, ...},
     * with the weights summing to ONE. When shrinking, the filter is widened so every source pixel
     * still contributes.
     */
    private static int[][] weights(int s, int d) {
        double scale = d / (double) s, wide = Math.max(1, 1 / scale), reach = LOBES * wide;
        int[][] out = new int[d][];
        for (int i = 0; i < d; i++) {
            double centre = (i + 0.5) / scale - 0.5;
            int from = (int) Math.ceil(centre - reach), to = (int) Math.floor(centre + reach), n = to - from + 1;
            double[] w = new double[n];
            double sum = 0;
            for (int k = 0; k < n; k++) sum += w[k] = lanczos((from + k - centre) / wide);
            int[] row = new int[n + 1];
            row[0] = from;
            int total = 0, most = 1;
            for (int k = 0; k < n; k++) {
                total += row[k + 1] = (int) Math.round(w[k] / sum * ONE);
                if (Math.abs(row[k + 1]) > Math.abs(row[most])) most = k + 1;
            }
            row[most] += ONE - total; // give the rounding remainder to the largest weight so a flat colour stays flat
            out[i] = row;
        }
        return out;
    }

    private static double lanczos(double x) {
        if (x == 0) return 1;
        if (x <= -LOBES || x >= LOBES) return 0;
        double px = Math.PI * x;
        return LOBES * Math.sin(px) * Math.sin(px / LOBES) / (px * px);
    }
}
