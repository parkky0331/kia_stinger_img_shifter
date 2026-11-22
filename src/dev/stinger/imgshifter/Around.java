package dev.stinger.imgshifter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/**
 * Overlay that covers the stock car on the surround-view screen with our own car in the chosen
 * body colour (experimental). The camera unit draws its car into the video, so we add a view
 * above the video, at the level of the stock door icon and buttons, showing a bed of the same
 * size and grey as the stock one with our car on it. Positions and rules are in AroundPlan. In
 * the two layouts that look down at the car, where the camera unit draws a second, large car, a
 * separate view covers that one the same way: the rear end in the view behind the car, the front
 * in the view ahead.
 *
 * Visibility follows what the app itself is told. The overlay goes up when the camera unit
 * reports a layout that has the top view, no matter how that layout was reached, and comes down
 * when it reports anything else. It stays up between those layouts: the top view is in the same
 * place in all of them, so the car doesn't flicker when reverse is released and the camera unit
 * switches to a front view. When the top view is zoomed out the overlay shrinks with it (by
 * AroundPlan's estimate). The large car hasn't been checked zoomed, so it is left to the camera
 * unit then.
 *
 * The video changes with the gear before the camera unit reports it, by up to 0.6 s when reverse
 * is released. So a reverse change is acted on immediately, using the layout the camera unit is
 * expected to switch to (AroundPlan.expected): the large car of the old layout is hidden and the
 * one of the new layout is shown. The next report confirms or corrects this. If none arrives for
 * a while, the last reported layout applies again.
 *
 * The camera app never waits for any of this: the bitmap is built off the main thread and the
 * view is a single small image. If anything unexpected happens the overlay is removed and stays
 * off.
 *
 * The overlay fades in over AroundPlan.DISSOLVE_MS. It is hidden immediately when the picture
 * under it is gone or has changed (screen closing, or leaving the large car's layout), because
 * nothing may cover the new camera picture. It only fades out when the screen stays up and just
 * the top view goes away.
 *
 * Optionally (experimental) the overlay is drawn on its own surfaces instead of views in the
 * window, see AroundLayers. It then disappears the moment the stock video surface does, which a
 * view drawn by the main thread can't do.
 */
final class Around {
    private static final String SCREEN = ".AvmMode", VIDEO = "camera_preview";
    /** How long a reverse change stands in for the camera unit's report of the new layout */
    private static final long LEFT_MS = 1500;
    /** Delay after the car is shown before its view is inspected for the log */
    private static final long LOOK_MS = 400;

    private final Swap.Log log;
    private final int paint;
    /** Box of the car at default zoom, and with the top view zoomed out by one and two steps */
    private final int[] place;
    private final int[][] places;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Overlay on its own surfaces (null: views in the window), and whether the stock video surface has been destroyed since it was last created */
    private final AroundLayers layers;
    private boolean closed;
    private final int dissolve = AroundPlan.DISSOLVE_MS;
    /** Tag of a view that is fading out */
    private static final Object GOING = new Object();
    private volatile Bitmap picture, tailPicture, nosePicture;
    private WeakReference<ImageView> view = new WeakReference<ImageView>(null), tail = new WeakReference<ImageView>(null),
            nose = new WeakReference<ImageView>(null);
    private int viewMode = -1, zoom = -1;
    /** Reverse gear: 1 engaged, 0 released, -1 not reported yet */
    private int reverse = -1;
    /** A reverse change in a large car's layout has removed that car from the video and the camera unit hasn't reported since */
    private boolean left;
    /** Front layout last shown by the camera unit, and the layout expected since the last reverse change (-1: none, use the reported one) */
    private int lastFront = -1, expect = -1;
    private String said = "", looked = "";
    private boolean broken, wasOn;
    private int wasStep;

    /** paint: body colour index. layered: draw on separate surfaces. */
    Around(Swap.Log log, int paint, boolean layered) {
        this.log = log;
        this.paint = paint;
        this.layers = layered ? new AroundLayers(log, dissolve) : null;
        this.places = new int[][] { AroundPlan.place(0), AroundPlan.place(1), AroundPlan.place(2) };
        this.place = places[0];
    }

    /** Builds the bitmaps off the main thread, afterMs after the app started */
    void warm(final long afterMs) {
        Thread t = new Thread("imgshifter-around") {
            @Override
            public void run() {
                try {
                    Thread.sleep(afterMs);
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    long t0 = SystemClock.uptimeMillis();
                    picture = made(paint);
                    if (picture == null) {
                        log.say("no picture for the surround view's car: the mod's own picture can't be read");
                        return;
                    }
                    log.say("made the surround view's car in " + (SystemClock.uptimeMillis() - t0) + " ms, for x " + place[0] + " y " + place[1] + " "
                            + place[2] + "x" + place[3]);
                    main.post(again);
                    tailPicture = madeTail(paint);
                    nosePicture = madeNose(paint);
                    if (tailPicture == null || nosePicture == null) log.say("no picture for a large car of the views down at the car: that one is left as it is");
                    main.post(again);
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable e) {
                    log.fail("making the surround view's car", e);
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /**
     * The bed with its soft edge and the car on it in colour index paint, at the size of
     * AroundPlan's box. Returns null if the embedded picture can't be decoded.
     */
    static Bitmap made(int paint) {
        Bitmap car = painted(TopCar.CAR, TopCar.PAINT, TopCar.W, TopCar.H, paint);
        if (car == null) return null;
        int w = AroundPlan.W, h = AroundPlan.H;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) px[y * w + x] = AroundPlan.bedAt(x + 0.5f, y + 0.5f) << 24 | AroundPlan.BED_COLOUR;
        }
        return on(px, w, h, car, AroundPlan.CAR_X - AroundPlan.X, AroundPlan.CAR_Y - AroundPlan.Y);
    }

    /** Same for the large car in the view behind the car: bed from the top edge down, with the car's rear end on it */
    static Bitmap madeTail(int paint) {
        Bitmap car = painted(TopCar.TAIL, TopCar.TAIL_PAINT, TopCar.TAIL_W, TopCar.TAIL_H, paint);
        return car == null ? null : large(car, AroundPlan.tailPlace()[3], true, AroundPlan.TAIL_END - TopCar.TAIL_H);
    }

    /** Same for the view ahead of the car: bed up from the bottom edge, with the car's front on it */
    static Bitmap madeNose(int paint) {
        Bitmap car = painted(TopCar.NOSE, TopCar.NOSE_PAINT, TopCar.NOSE_W, TopCar.NOSE_H, paint);
        int[] at = AroundPlan.nosePlace();
        return car == null ? null : large(car, at[3], false, AroundPlan.NOSE_TOP - at[1]);
    }

    /** Box of a large car, h high: its bed (from the top edge down, or from the bottom edge up) and the car with its first row at carTop */
    private static Bitmap large(Bitmap car, int h, boolean fromTop, int carTop) {
        int w = AroundPlan.LARGE_W;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) px[y * w + x] = AroundPlan.largeBedAt(x + 0.5f, y + 0.5f, h, fromTop) << 24 | AroundPlan.BED_COLOUR;
        }
        return on(px, w, h, car, (w - car.getWidth()) / 2, carTop);
    }

    /** Turns the bed's pixels into a w x h bitmap and draws the car on it at x, y */
    private static Bitmap on(int[] bed, int w, int h, Bitmap car, int x, int y) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setDensity(Bitmap.DENSITY_NONE);
        out.setPixels(bed, 0, w, 0, 0, w, h);
        new Canvas(out).drawBitmap(car, x, y, null);
        return out;
    }

    /** One of the embedded pictures (w x h) with its body in colour index paint, or null if it can't be decoded */
    private static Bitmap painted(String picture, String paintMap, int w, int h, int paint) {
        Bitmap car = decoded(picture), map = decoded(paintMap);
        if (car == null || map == null || car.getWidth() != w || car.getHeight() != h || map.getWidth() != w || map.getHeight() != h) return null;
        int[] px = new int[w * h], where = new int[px.length];
        car.getPixels(px, 0, w, 0, 0, w, h);
        map.getPixels(where, 0, w, 0, 0, w, h);
        for (int i = 0; i < where.length; i++) where[i] &= 255;
        BodyPaint.paint(px, paint, where);
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        out.setDensity(Bitmap.DENSITY_NONE);
        return out;
    }

    private static Bitmap decoded(String base64) {
        byte[] file = Base64.decode(base64, Base64.DEFAULT);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(file, 0, file.length, o);
    }

    /**
     * Called when a video view of the app gets its renderer. If it belongs to the surround-view
     * screen, that screen is being built and the overlay views are added right above the video.
     * Returns true in that case.
     */
    boolean screen(View video) {
        if (broken || !log.on()) return false;
        try {
            Context c = video.getContext();
            if (c == null || !c.getClass().getName().endsWith(SCREEN)) return false;
            if (video.getId() == View.NO_ID || !VIDEO.equals(video.getResources().getResourceEntryName(video.getId()))) return false;
            ViewParent parent = video.getParent();
            if (!(parent instanceof FrameLayout)) {
                log.say("the surround view's screen is not built as expected: its car is left as it is");
                broken = true;
                return false;
            }
            FrameLayout frame = (FrameLayout) parent;
            if (layers != null) {
                // top view car at its measured box; the large car gets a column down the whole left-hand picture
                layers.attach(frame, video, place, new int[] { AroundPlan.LARGE_X, 0, AroundPlan.LARGE_W, AroundPlan.SCREEN_H });
                log.say("the surround view's screen is up: the overlay is in place, on surfaces of its own");
                update();
                return true;
            }
            view = new WeakReference<ImageView>(added(frame, video, place[0], place[1], place[2], place[3]));
            int[] rear = AroundPlan.tailPlace(), front = AroundPlan.nosePlace();
            tail = new WeakReference<ImageView>(added(frame, video, rear[0], rear[1], rear[2], rear[3]));
            nose = new WeakReference<ImageView>(added(frame, video, front[0], front[1], front[2], front[3]));
            log.say("the surround view's screen is up: the overlay is in place");
            update();
            return true;
        } catch (Throwable t) {
            gone("building the overlay", t);
            return false;
        }
    }

    /** Moves the car's view to a new box (top view zoomed) unless it is already there */
    private static void at(ImageView v, int[] p) {
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (l.leftMargin == p[0] && l.topMargin == p[1] && l.width == p[2] && l.height == p[3]) return;
        l.leftMargin = p[0];
        l.topMargin = p[1];
        l.width = p[2];
        l.height = p[3];
        v.setLayoutParams(l);
    }

    private static ImageView added(FrameLayout frame, View video, int x, int y, int w, int h) {
        ImageView v = new ImageView(video.getContext());
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setVisibility(View.GONE);
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(w, h, Gravity.LEFT | Gravity.TOP);
        at.leftMargin = x;
        at.topMargin = y;
        frame.addView(v, frame.indexOfChild(video) + 1, at);
        return v;
    }

    /** View mode reported by the camera unit, as the app receives it */
    void status(final int mode) {
        now(new Runnable() {
            @Override
            public void run() {
                if (mode != viewMode) reported();
                viewMode = mode;
                if (AroundPlan.front(mode)) lastFront = mode;
                update();
            }
        });
    }

    /** Zoom button state, as the app receives it */
    void zoom(final int state) {
        now(new Runnable() {
            @Override
            public void run() {
                if (state != zoom) reported();
                zoom = state;
                update();
            }
        });
    }

    /**
     * Reverse engaged or released, as the app receives it. If the change removes the layout's large
     * car from the video, its overlay is hidden here without waiting for the camera unit's report,
     * and shown again if the gear changes back. The first report and repeated values change nothing.
     */
    void reverse(final boolean engaged) {
        now(new Runnable() {
            @Override
            public void run() {
                boolean changed = reverse == (engaged ? 0 : 1);
                reverse = engaged ? 1 : 0;
                if (changed) {
                    reported();
                    expect = AroundPlan.expected(viewMode, engaged, lastFront);
                    if (expect < 0) left = AroundPlan.leaves(viewMode, zoom, engaged);
                    if (expect >= 0 || left) main.postDelayed(back, LEFT_MS);
                }
                update();
            }
        });
    }

    boolean layered() {
        return layers != null;
    }

    /**
     * The stock video surface is being destroyed. This runs on the main thread, which closes the
     * camera next and is busy with that for a while. In layer mode the overlay is cleared now,
     * together with the video. It comes back when the video surface is created again.
     */
    void closing() {
        if (layers == null) return;
        closed = true;
        update();
    }

    void opening() {
        if (layers == null || !closed) return;
        closed = false;
        update();
    }

    /** A new report from the camera unit, or another reverse change: drop the layout predicted from the last change */
    private void reported() {
        left = false;
        expect = -1;
        main.removeCallbacks(back);
    }

    /** The camera unit still reports the same layout a while after reverse changed, so that layout applies again */
    private final Runnable back = new Runnable() {
        @Override
        public void run() {
            if (!left && expect < 0) return;
            left = false;
            expect = -1;
            update();
        }
    };

    private final Runnable again = new Runnable() {
        @Override
        public void run() {
            update();
        }
    };

    /** Runs r on the main thread, right away if the caller is already on it (the app's listeners are) */
    private void now(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else main.post(r);
    }

    /** On the main thread: shows the car while a layout with the top view is reported, and the large car in its layout except right after a reverse change */
    private void update() {
        if (broken) return;
        try {
            int mode = expect >= 0 ? expect : viewMode;
            boolean layout = AroundPlan.shows(mode, zoom), want = layout && log.on();
            boolean rear = want && AroundPlan.tails(mode, zoom), front = want && AroundPlan.noses(mode, zoom), large = rear || front;
            ImageView v = view.get();
            int step = AroundPlan.zoomStep(zoom);
            boolean built = layers != null ? layers.attached() : v != null;
            // the camera screen is still up with another picture, so if the car has to go it fades out
            boolean stays = mode != AroundPlan.OFF && mode >= 0;
            if (layers != null) {
                int[] p = places[Math.max(0, step)];
                boolean there = want && !closed;
                layers.show(there ? picture : null, new int[] { p[0] - place[0], p[1] - place[1], p[2], p[3] }, stays && !closed,
                        there && rear && !left ? tailPicture : null, AroundPlan.tailPlace()[1], there && front && !left ? nosePicture : null, AroundPlan.nosePlace()[1]);
            } else {
                if (want && v != null) at(v, places[step]);
                set(v, picture, want, stays);
                set(tail.get(), tailPicture, rear && !left, false);
                set(nose.get(), nosePicture, front && !left, false);
            }
            boolean is = on();
            if (is && (!wasOn || step != wasStep)) main.postDelayed(look, LOOK_MS);
            wasOn = is;
            wasStep = step;
            String state = AroundPlan.mode(viewMode) + " (" + viewMode + ")" + (expect >= 0 ? ", expecting " + AroundPlan.mode(expect) + " (" + expect + ")" : "")
                    + ", zoom state " + zoom + ", reverse " + (reverse < 0 ? "not heard" : reverse == 1 ? "in" : "out")
                    + ": " + (!layout ? "not a measured layout, no car of ours" : !want ? "the mod is switched off" : !built ? "our car is wanted, but the screen is not built"
                    : closed ? "the stock screen's video surface is gone, our car with it"
                    : !is ? (layers != null && picture != null ? "our car is wanted, its surface is not there yet" : "our car is wanted, its picture is not made yet") : step > 0 ? "our car is on, " + AroundPlan.ZOOM_STEP_PERCENT * step + " per cent smaller for the zoom"
                    : !large ? "our car is on" : left ? "our car is on, the large one is off since reverse changed"
                    : (rear ? tailOn() : noseOn()) ? "our car is on, and the large one" : "our car is on, the large one is not made");
            if (!state.equals(said)) {
                said = state;
                log.say("surround view: " + state);
            }
        } catch (Throwable t) {
            gone("showing the overlay", t);
        }
    }

    /** Logs one line shortly after the car is shown: whether its view is really on screen, and where */
    private final Runnable look = new Runnable() {
        @Override
        public void run() {
            try {
                if (layers != null) {
                    String now = layers.where();
                    if (!now.equals(looked)) {
                        looked = now;
                        log.say("surround view: our car's surface is " + now);
                    }
                    return;
                }
                ImageView v = view.get();
                if (v == null || v.getVisibility() != View.VISIBLE) return;
                int[] at = new int[2];
                v.getLocationOnScreen(at);
                String now = (v.isShown() ? "on the screen" : "hidden with a view it lies in") + ", " + v.getWidth() + "x" + v.getHeight() + " at " + at[0] + "," + at[1]
                        + (v.getWindowVisibility() == View.VISIBLE ? "" : ", its window is not shown");
                if (!now.equals(looked)) {
                    looked = now;
                    log.say("surround view: our car's view is " + now);
                }
            } catch (Throwable t) {
                log.fail("looking at the overlay", t);
            }
        }
    };

    /** Shows one view (fading in) or hides it (immediately, or fading out from its current alpha) */
    private void set(final ImageView v, Bitmap b, boolean want, boolean softly) {
        if (v == null) return;
        if (want && b != null) {
            if (v.getVisibility() == View.VISIBLE && v.getTag() != GOING) return;
            if (v.getDrawable() == null) v.setImageBitmap(b);
            v.animate().cancel();
            v.setTag(null);
            if (v.getVisibility() != View.VISIBLE) {
                v.setAlpha(dissolve > 0 ? 0f : 1f);
                v.setVisibility(View.VISIBLE);
            }
            if (dissolve > 0) v.animate().alpha(1f).setDuration((long) (dissolve * (1 - v.getAlpha()))).withEndAction(null);
            else v.setAlpha(1f);
        } else if (v.getVisibility() != View.GONE) {
            if (softly && dissolve > 0) {
                if (v.getTag() == GOING) return;
                v.animate().cancel();
                v.setTag(GOING);
                v.animate().alpha(0f).setDuration((long) (dissolve * v.getAlpha())).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.setVisibility(View.GONE);
                        v.setTag(null);
                    }
                });
            } else {
                v.animate().cancel();
                v.setTag(null);
                v.setVisibility(View.GONE);
            }
        }
    }

    /** Something went wrong: remove the overlay and keep it off until the app restarts */
    private void gone(String what, Throwable t) {
        broken = true;
        log.fail(what + ": the surround view's car is left as it is from here on", t);
        try {
            if (layers != null) layers.show(null, null, false, null, 0, null, 0);
            set(view.get(), null, false, false);
            set(tail.get(), null, false, false);
            set(nose.get(), null, false, false);
        } catch (Throwable ignored) {
            // nothing left to remove
        }
    }

    /** For the test harness: whether the car (and below, the large rear end and the large front) is on screen right now */
    boolean on() {
        return layers != null ? layers.carOn() : shown(view.get());
    }

    boolean tailOn() {
        return layers != null ? layers.tailOn() : shown(tail.get());
    }

    boolean noseOn() {
        return layers != null ? layers.noseOn() : shown(nose.get());
    }

    private static boolean shown(ImageView v) {
        return v != null && v.getVisibility() == View.VISIBLE && v.getDrawable() != null && v.getTag() != GOING;
    }
}
