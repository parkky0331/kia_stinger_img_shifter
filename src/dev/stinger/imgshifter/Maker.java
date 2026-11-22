package dev.stinger.imgshifter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.SparseArray;
import android.util.TypedValue;

import org.xmlpull.v1.XmlPullParser;

/**
 * Builds the replacement pictures. All source material is read at run time from the car's own
 * vehicle settings app (the floor picture of each mode and its drawing of this car), so the mod
 * ships no pictures of its own:
 *   base   floor with the tinted car drawing on it, at the floor picture's size
 *   split  the base at its native size, for the right-hand panel
 *   popup  the base enlarged for the settings pop-up
 *   guide  the stock help picture with its small car painted over by a small base
 *   car    the camera app's top-down car with its body repainted (BodyPaint)
 *   sign   a small camera app icon with a car in it, the car repainted. A button's state list is
 *          rebuilt from its own drawables with the repainted ones swapped in.
 * All bitmaps are created with "no density" so Android never rescales them behind our back.
 */
final class Maker {
    static final String SETTINGS = "com.android.vehiclesettings";
    static final String CAR = "set_etc_drivemode_comfort_ck", FLOOR = "set_etc_drive_bg_";
    /** The car in the base picture (Art.BASE_W x Art.BASE_H): its centre and its length in pixels */
    static final float CAR_CX = 313.5f, CAR_CY = 176f, CAR_LEN = 341f;
    /** Size of the stock right-hand panel picture, used to build ours ahead of first use */
    static final int SPLIT_W = 600, SPLIT_H = 330;

    /** The help picture (640 x 540) and the pane its small car sits in, without the pane's border and the glow above it */
    static final int GUIDE_W = 640, GUIDE_H = 540;
    static final Rect GUIDE_PANE = new Rect(387, 286, 543, 389);
    /** Centre and length of the small stock car in that pane */
    static final float GUIDE_CX = 455f, GUIDE_CY = 331f, GUIDE_LEN = 123f;
    /** Pixels used to recognise the drawing: the pane's border on the right and below, the line on its left, and black inside */
    private static final int[][] GUIDE_MARKS = { { 544, 300, 0xff252525 }, { 470, 390, 0xff252525 }, { 386, 300, 0xff1d1d1d },
            { 541, 387, 0xff000000 }, { 470, 287, 0xff000000 } };
    private static final int BLACK = 0xff000000;

    private final Context context;
    private Resources stock;
    private final Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG);
    /** Why the last picture could not be built (for the log) */
    String why;

    Maker(Context context) {
        this.context = context;
    }

    /** Resources of the vehicle settings app (our own when running inside that app) */
    Resources stock() throws Exception {
        if (stock == null) {
            stock = SETTINGS.equals(context.getPackageName()) ? context.getResources()
                    : context.createPackageContext(SETTINGS, 0).getResources();
        }
        return stock;
    }

    private static BitmapFactory.Options plain(boolean mutable) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inMutable = mutable;
        return o;
    }

    private Bitmap piece(String name) throws Exception {
        Resources r = stock();
        int id = r.getIdentifier(name, "drawable", SETTINGS);
        if (id == 0) {
            why = "the settings app has no " + name;
            return null;
        }
        Bitmap b = BitmapFactory.decodeResource(r, id, plain(false));
        if (b == null) why = name + " can't be read";
        else b.setDensity(Bitmap.DENSITY_NONE);
        return b;
    }

    private static Bitmap blank(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setDensity(Bitmap.DENSITY_NONE);
        return b;
    }

    private static Canvas on(Bitmap b) {
        Canvas c = new Canvas(b);
        c.setDensity(Bitmap.DENSITY_NONE);
        return c;
    }

    /** Size of a stock picture without decoding it: {width, height} */
    static int[] size(Resources r, int id) {
        BitmapFactory.Options o = plain(false);
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(r, id, o);
        return new int[] { o.outWidth, o.outHeight };
    }

    /** Floor of a mode with the car drawing on it in the mode's colour. Returns null (see why) if a source picture isn't as expected. */
    Bitmap base(int mode) throws Exception {
        Bitmap floor = piece(FLOOR + Art.FLOORS[mode]);
        if (floor == null) return null;
        Bitmap car = piece(CAR);
        if (car == null) return null;
        if (floor.getWidth() != Art.BASE_W || floor.getHeight() != Art.BASE_H || car.getWidth() != Art.CAR_W || car.getHeight() != Art.CAR_H) {
            why = "pieces of another size: floor " + floor.getWidth() + "x" + floor.getHeight() + ", car " + car.getWidth() + "x"
                    + car.getHeight();
            return null;
        }
        int[] px = new int[Art.CAR_W * Art.CAR_H];
        car.getPixels(px, 0, Art.CAR_W, 0, 0, Art.CAR_W, Art.CAR_H);
        Art.tint(px, Art.TONES[mode]);
        Bitmap tinted = Bitmap.createBitmap(px, Art.CAR_W, Art.CAR_H, Bitmap.Config.ARGB_8888);
        tinted.setDensity(Bitmap.DENSITY_NONE);
        Bitmap out = blank(Art.BASE_W, Art.BASE_H);
        Canvas c = on(out);
        c.drawBitmap(floor, 0, 0, null);
        c.drawBitmap(tinted, 0, Art.CAR_Y, null);
        return out;
    }

    /**
     * Where the base's non-empty part goes in a picture of w x h, and at what size: {x, y, width, height}.
     * As large as fits, with the car slightly left of centre like in the stock pictures.
     */
    static int[] place(int w, int h) {
        float s = Math.min(w / (float) Art.INNER_W, h / (float) Art.INNER_H);
        int dw = Math.round(Art.INNER_W * s), dh = Math.round(Art.INNER_H * s);
        int x = Math.round(0.496f * w - (CAR_CX - Art.INNER_X) * s);
        return new int[] { Math.max(0, Math.min(w - dw, x)), (h - dh) / 2, dw, dh };
    }

    /** Whether the base has to be enlarged for a picture of this size. If so there is a quick way to build it and a sharp one. */
    static boolean enlarges(int w, int h) {
        return place(w, h)[2] > Art.INNER_W;
    }

    /**
     * The base placed in a picture of w x h. At its native size (right-hand panel, 600 x 330) it
     * is copied pixel for pixel. Enlarged (pop-up, 1260 x 531) it is either drawn scaled, which is
     * quick but a little soft, or (fine) resized with a sharper filter. The sharp version takes
     * the head unit a few seconds, so call it off the main thread.
     */
    Bitmap fitted(Bitmap base, int w, int h, boolean fine) {
        int[] at = place(w, h);
        Bitmap out = blank(w, h);
        boolean same = at[2] == Art.INNER_W && at[3] == Art.INNER_H;
        if (same || (fine && at[2] > Art.INNER_W)) {
            int[] px = new int[Art.INNER_W * Art.INNER_H];
            base.getPixels(px, 0, Art.INNER_W, Art.INNER_X, Art.INNER_Y, Art.INNER_W, Art.INNER_H);
            if (!same) px = Resample.lanczos(px, Art.INNER_W, Art.INNER_H, at[2], at[3]);
            out.setPixels(px, 0, at[2], at[0], at[1], at[2], at[3]);
        } else {
            Rect inner = new Rect(Art.INNER_X, Art.INNER_Y, Art.INNER_X + Art.INNER_W, Art.INNER_Y + Art.INNER_H);
            on(out).drawBitmap(base, inner, new RectF(at[0], at[1], at[0] + at[2], at[1] + at[3]), smooth);
        }
        return out;
    }

    /**
     * The app's top-down car (drawable id of res) with its body in colour index paint. Same size
     * and shadow, only the paint differs.
     */
    Bitmap painted(Resources res, int id, int paint) {
        Bitmap stockCar = BitmapFactory.decodeResource(res, id, plain(false));
        if (stockCar == null) {
            why = "the car picture can't be read";
            return null;
        }
        int w = stockCar.getWidth(), h = stockCar.getHeight();
        int[] px = new int[w * h];
        stockCar.getPixels(px, 0, w, 0, 0, w, h);
        BodyPaint.paint(px, paint);
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        out.setDensity(Bitmap.DENSITY_NONE);
        return out;
    }

    /**
     * A small icon of the app with a car in it (drawable id of res), repainted in colour index
     * paint. A bitmap comes back repainted. A state list (a button with one drawable per state)
     * comes back as a new state list of its own drawables, with those Art classes as SIGN
     * repainted and the others unchanged. Returns null (see why) for anything else.
     */
    Drawable sign(Resources res, int id, int paint) throws Exception {
        TypedValue value = new TypedValue();
        res.getValue(id, value, true);
        String file = value.string == null ? "" : value.string.toString();
        if (!file.endsWith(".xml")) {
            Bitmap b = signPicture(res, id, paint);
            return b == null ? null : new BitmapDrawable(res, b);
        }
        StateListDrawable list = new StateListDrawable();
        SparseArray<Bitmap> done = new SparseArray<Bitmap>();
        int states = 0;
        android.content.res.XmlResourceParser xml = res.getXml(id);
        try {
            int event;
            while ((event = xml.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                if (xml.getDepth() == 1) {
                    if (!"selector".equals(xml.getName())) {
                        why = "it is a <" + xml.getName() + ">, not a state list";
                        return null;
                    }
                    continue;
                }
                if (xml.getDepth() != 2 || !"item".equals(xml.getName())) continue;
                int child = 0, n = 0;
                int[] when = new int[xml.getAttributeCount()];
                for (int i = 0; i < xml.getAttributeCount(); i++) {
                    int attribute = xml.getAttributeNameResource(i);
                    if (attribute == android.R.attr.drawable) child = xml.getAttributeResourceValue(i, 0);
                    else if (attribute != 0 && attribute != android.R.attr.id) when[n++] = xml.getAttributeBooleanValue(i, false) ? attribute : -attribute;
                }
                if (child == 0) {
                    why = "a state of it has no picture of its own";
                    return null;
                }
                Drawable d = null;
                if (Art.family(res.getResourceEntryName(child)) == Art.SIGN) {
                    Bitmap b = done.get(child);
                    if (b == null) {
                        b = signPicture(res, child, paint);
                        if (b != null) done.put(child, b);
                    }
                    if (b != null) d = new BitmapDrawable(res, b);
                }
                if (d == null) d = res.getDrawable(child);
                list.addState(java.util.Arrays.copyOf(when, n), d);
                states++;
            }
        } finally {
            xml.close();
        }
        if (states == 0 || done.size() == 0) {
            why = states == 0 ? "the state list is empty" : "none of its pictures is a sign";
            return null;
        }
        return list;
    }

    private Bitmap signPicture(Resources res, int id, int paint) {
        Bitmap stockSign = BitmapFactory.decodeResource(res, id, plain(false));
        if (stockSign == null) {
            why = "the picture can't be read";
            return null;
        }
        int w = stockSign.getWidth(), h = stockSign.getHeight();
        int[] px = new int[w * h];
        stockSign.getPixels(px, 0, w, 0, 0, w, h);
        BodyPaint.paintCar(px, w, paint);
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        out.setDensity(Bitmap.DENSITY_NONE);
        return out;
    }

    /** Same colour, allowing for what image compression loses */
    private static boolean near(int pixel, int want) {
        for (int shift = 0; shift <= 24; shift += 8) {
            if (Math.abs(((pixel >> shift) & 255) - ((want >> shift) & 255)) > 8) return false;
        }
        return true;
    }

    /**
     * The stock help picture with its small car painted over. Takes a copy of it (it has to be
     * the 640 x 540 drawing with the car's pane at GUIDE_PANE), blacks out the lower part of the
     * pane the way it is around the stock car, and draws a small base there. Returns null (see
     * why) if the picture isn't that drawing.
     */
    Bitmap guide(Bitmap base, Resources res, int id) {
        Bitmap out = BitmapFactory.decodeResource(res, id, plain(true));
        if (out == null) {
            why = "the help picture can't be read";
            return null;
        }
        if (!out.isMutable() || out.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap copy = out.copy(Bitmap.Config.ARGB_8888, true);
            if (copy == null) {
                why = "the help picture can't be copied";
                return null;
            }
            out = copy;
        }
        out.setDensity(Bitmap.DENSITY_NONE);
        boolean same = out.getWidth() == GUIDE_W && out.getHeight() == GUIDE_H;
        for (int i = 0; same && i < GUIDE_MARKS.length; i++) same = near(out.getPixel(GUIDE_MARKS[i][0], GUIDE_MARKS[i][1]), GUIDE_MARKS[i][2]);
        if (!same) {
            why = "the help picture is another drawing (" + out.getWidth() + "x" + out.getHeight() + ")";
            return null;
        }
        Rect p = GUIDE_PANE;
        Canvas c = on(out);
        c.clipRect(p);
        c.drawColor(BLACK);
        // shrink in two steps: halve first (each pixel the mean of four) so thin lines don't break up
        Bitmap half = blank((Art.BASE_W + 1) / 2, Art.BASE_H / 2);
        on(half).drawBitmap(base, null, new RectF(0, 0, Art.BASE_W / 2f, Art.BASE_H / 2f), smooth);
        float s = GUIDE_LEN / CAR_LEN, x = GUIDE_CX - CAR_CX * s, y = GUIDE_CY - CAR_CY * s;
        c.drawBitmap(half, new Rect(0, 0, half.getWidth(), half.getHeight()),
                new RectF(x, y, x + half.getWidth() * 2 * s, y + half.getHeight() * 2 * s), smooth);
        return out;
    }
}
