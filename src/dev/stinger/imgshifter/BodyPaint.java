package dev.stinger.imgshifter;

/**
 * Repaints the head unit's top-down car pictures in a body colour. The stock pictures are silver;
 * this recolours the body in one of the Stinger's factory colours. Only the paint changes: glass,
 * tyres and the shadow are too dark to count as paint, and lamps and the red of an open door are
 * too saturated, so they are left alone.
 *
 * The names are Kia's for the facelifted Stinger (2021 on) plus the earlier blue. The RGB values
 * were picked by eye, since metallic and pearl paints have no exact screen colour.
 */
final class BodyPaint {
    private BodyPaint() {}

    /** Values of the body_colour setting. "stock" has no entry here and leaves the picture as it is. */
    static final String[] KEYS = { "snow_white_pearl", "aurora_black_pearl", "panthera_metal", "ceramic_silver", "hichroma_red",
            "deep_chroma_blue", "micro_blue", "neon_orange", "ascot_green", "moonscape_matte_gray" };
    static final int[] RGB = { 0xf3f3ef, 0x1b1c20, 0x54575c, 0xb4b7bb, 0xb1121f, 0x16254f, 0x2552a6, 0xe5591b, 0x163233, 0x73777b };
    /** Matt paint: hardly any shine */
    static final boolean[] MATT = { false, false, false, false, false, false, false, false, false, true };

    /** Brightness of the picture's silver on a flat panel. At that level a paint shows its plain colour. */
    private static final float FLAT = 0.74f;

    /** Index of a setting value, or -1 for "stock" or anything unknown */
    static int of(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }

    /** Repaints the body in these pixels (plain ARGB, the silver picture) with colour index which. */
    static void paint(int[] px, int which) {
        if (which < 0 || which >= RGB.length) return;
        for (int i = 0; i < px.length; i++) {
            if ((px[i] >>> 24) == 0) continue;
            float body = body(px[i]);
            if (body > 0) px[i] = mixed(px[i], body, which);
        }
    }

    /**
     * Same, for a picture that comes with a paint mask (where: 0 = no paint .. 255 = all paint, one
     * value per pixel). Each pixel is repainted only by that amount, so glass keeps its reflections.
     */
    static void paint(int[] px, int which, int[] where) {
        if (which < 0 || which >= RGB.length || where == null || where.length != px.length) return;
        for (int i = 0; i < px.length; i++) {
            if ((px[i] >>> 24) == 0 || where[i] <= 0) continue;
            float body = body(px[i]) * Math.min(255, where[i]) / 255f;
            if (body > 0) px[i] = mixed(px[i], body, which);
        }
    }

    /**
     * Same, for a small icon of width w that shows more than the car: the camera's field of view
     * drawn as a translucent fan with a thin outline, or a wider shape under the car. Only the car
     * is repainted. A pixel counts as car if its silver is opaque (the fan isn't), part of an area
     * wider than a line (the outline isn't), and that area is as narrow as a car is in these icons,
     * about a third of their width (shapes under the car are wider). Bonnet, roof and boot are
     * separate areas split by the glass, and each is checked by that rule.
     */
    static void paintCar(int[] px, int w, int which) {
        if (which < 0 || which >= RGB.length || w <= 0 || px.length % w != 0) return;
        int h = px.length / w;
        float[] body = new float[px.length];
        boolean[] firm = new boolean[px.length];
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) continue;
            body[i] = body(px[i]);
            firm[i] = body[i] > 0.5f && a >= 248;
        }
        boolean[] car = grown(grown(panels(grown(shrunk(firm, w, h), w, h), w, h), w, h), w, h);
        for (int i = 0; i < px.length; i++) {
            if (!car[i] || body[i] <= 0) continue;
            float part = body[i] * step(0.60f, 0.92f, (px[i] >>> 24) / 255f);
            if (part > 0) px[i] = mixed(px[i], part, which);
        }
    }

    /** How much of a pixel is paint: bright enough, and grey */
    private static float body(int p) {
        float r = ((p >> 16) & 255) / 255f, g = ((p >> 8) & 255) / 255f, b = (p & 255) / 255f;
        float most = Math.max(r, Math.max(g, b)), least = Math.min(r, Math.min(g, b));
        float coloured = most == 0 ? 0 : (most - least) / most, light = 0.30f * r + 0.59f * g + 0.11f * b;
        return step(0.22f, 0.40f, light) * (1 - step(0.12f, 0.28f, coloured));
    }

    /** A pixel with that share of it repainted. The paint is lit like the silver was, and the brightest glints stay as white shine. */
    private static int mixed(int p, float body, int which) {
        int rgb = RGB[which];
        float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
        float r = ((p >> 16) & 255) / 255f, g = ((p >> 8) & 255) / 255f, b = (p & 255) / 255f;
        float light = 0.30f * r + 0.59f * g + 0.11f * b;
        float shade = light / FLAT, glint = Math.max(0, light - 0.80f) * (MATT[which] ? 0.5f : 2.2f);
        float nr = Math.min(1, cr * shade + glint), ng = Math.min(1, cg * shade + glint), nb = Math.min(1, cb * shade + glint);
        int R = Math.round(255 * (r + (nr - r) * body)), G = Math.round(255 * (g + (ng - g) * body)), B = Math.round(255 * (b + (nb - b) * body));
        return (p & 0xff000000) | (R << 16) | (G << 8) | B;
    }

    /** Erodes a mask by one pixel: a pixel stays only if it and its eight neighbours are all set */
    private static boolean[] shrunk(boolean[] m, int w, int h) {
        boolean[] out = new boolean[m.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                out[i] = m[i] && m[i - 1] && m[i + 1] && m[i - w] && m[i + w] && m[i - w - 1] && m[i - w + 1] && m[i + w - 1] && m[i + w + 1];
            }
        }
        return out;
    }

    /** Dilates a mask by one pixel */
    private static boolean[] grown(boolean[] m, int w, int h) {
        boolean[] out = new boolean[m.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y * w + x]) continue;
                for (int ny = Math.max(0, y - 1); ny <= Math.min(h - 1, y + 1); ny++) {
                    for (int nx = Math.max(0, x - 1); nx <= Math.min(w - 1, x + 1); nx++) out[ny * w + nx] = true;
                }
            }
        }
        return out;
    }

    /**
     * Picks the car's panels out of the connected areas of a mask. Areas wider than 45 % of the
     * picture are dropped. Of the rest, the largest is kept along with every area no wider than it
     * (with a small allowance for the mirrors).
     */
    private static boolean[] panels(boolean[] m, int w, int h) {
        int[] piece = new int[m.length], stack = new int[m.length];
        int pieces = 0, most = 0, mostLeft = 0, mostRight = 0;
        int[] left = new int[16], right = new int[16];
        for (int start = 0; start < m.length; start++) {
            if (!m[start] || piece[start] != 0) continue;
            pieces++;
            if (pieces >= left.length) {
                left = java.util.Arrays.copyOf(left, left.length * 2);
                right = java.util.Arrays.copyOf(right, right.length * 2);
            }
            int top = 0, count = 0, x0 = w, x1 = -1;
            stack[top++] = start;
            piece[start] = pieces;
            while (top > 0) {
                int i = stack[--top], x = i % w;
                count++;
                if (x < x0) x0 = x;
                if (x > x1) x1 = x;
                if (x > 0 && m[i - 1] && piece[i - 1] == 0) {
                    piece[i - 1] = pieces;
                    stack[top++] = i - 1;
                }
                if (x < w - 1 && m[i + 1] && piece[i + 1] == 0) {
                    piece[i + 1] = pieces;
                    stack[top++] = i + 1;
                }
                if (i >= w && m[i - w] && piece[i - w] == 0) {
                    piece[i - w] = pieces;
                    stack[top++] = i - w;
                }
                if (i < m.length - w && m[i + w] && piece[i + w] == 0) {
                    piece[i + w] = pieces;
                    stack[top++] = i + w;
                }
            }
            left[pieces] = x0;
            right[pieces] = x1;
            if (count > most && (x1 - x0 + 1) * 100 <= w * 45) {
                most = count;
                mostLeft = x0;
                mostRight = x1;
            }
        }
        boolean[] out = new boolean[m.length];
        if (most == 0) return out;
        int allowed = Math.max(3, Math.round(0.18f * (mostRight - mostLeft + 1)));
        for (int i = 0; i < m.length; i++) {
            int p = piece[i];
            out[i] = p != 0 && left[p] >= mostLeft - allowed && right[p] <= mostRight + allowed;
        }
        return out;
    }

    /** Smooth step: 0 below lo, 1 above hi */
    private static float step(float lo, float hi, float v) {
        float t = Math.max(0, Math.min(1, (v - lo) / (hi - lo)));
        return t * t * (3 - 2 * t);
    }
}
