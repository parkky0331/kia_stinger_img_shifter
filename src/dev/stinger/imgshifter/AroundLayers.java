package dev.stinger.imgshifter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Surround-view overlay drawn on its own surfaces instead of image views in the stock screen's
 * window (experimental, enabled in the settings).
 *
 * A view only changes on screen when the app's main thread redraws the window, and the stock
 * screen blocks that thread for about a quarter of a second while it closes its camera. Measured
 * on the car: camera unit reports "off" at 0 ms, video stops at 100 to 160 ms, main thread is
 * free again at 240 to 330 ms. So our car stayed on screen after the video under it was gone. A
 * surface is drawn by whoever holds it, here a thread of the mod's own, which can post a new
 * frame right away no matter what the main thread is doing.
 *
 * Two SurfaceViews are inserted directly above the video and below the rest of the window (the
 * stock door icon, buttons and texts stay on top): one at the top view's car and one down the
 * left-hand picture where the large car's rear end or front is. They always stay visible; only
 * their content changes, and with nothing to show they are transparent. A picture fades in over
 * the dissolve time, drawn frame by frame on the same thread.
 */
final class AroundLayers {
    private static final int PAINT = 1;
    private static final long FRAME_MS = 16;

    /** One picture on a surface: its current opacity (0 clear, 1 full) and the opacity it is heading for */
    private static final class Sprite {
        Bitmap picture;
        float now, to;
        /** fade out when hiding (otherwise hide immediately) */
        boolean softly;

        /** Advances the fade by the given fraction. Returns true while it is still in progress. */
        boolean step(float part) {
            if (now == to) return false;
            if (part >= 1 || (to < now && !softly)) now = to;
            else now = to > now ? Math.min(to, now + part) : Math.max(to, now - part);
            if (now == 0 && to == 0) picture = null;
            return now != to;
        }
    }

    /** One surface. ready is true between surfaceChanged and surfaceDestroyed; made counts how often the surface was created, drawn is the creation last drawn on. */
    private final class Sheet implements SurfaceHolder.Callback {
        final SurfaceView view;
        volatile boolean ready;
        volatile int made;
        int drawn;

        Sheet(FrameLayout frame, View video, int x, int y, int w, int h) {
            view = new SurfaceView(video.getContext());
            view.setZOrderMediaOverlay(true);
            view.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            view.getHolder().addCallback(this);
            FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(w, h, Gravity.LEFT | Gravity.TOP);
            at.leftMargin = x;
            at.topMargin = y;
            frame.addView(view, frame.indexOfChild(video) + 1, at);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            made++;
            ready = true;
            again();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            ready = false;
        }
    }

    private final Swap.Log log;
    private final int dissolve;
    private final Handler draw;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private volatile Sheet top, side;
    /** The requested state. Set from any thread and picked up by the drawing thread; the sprites themselves are only touched on that thread. */
    private final Object asked = new Object();
    private Bitmap askedCar, askedTail, askedNose;
    private Rect askedBox;
    private int askedTailTop, askedNoseTop;
    private boolean askedSoftly, news;
    private final Sprite car = new Sprite(), tail = new Sprite(), nose = new Sprite();
    private Rect box;
    private int tailTop, noseTop;
    private long last;
    private volatile boolean broken, wantCar, wantTail, wantNose;

    AroundLayers(Swap.Log log, int dissolve) {
        this.log = log;
        this.dissolve = dissolve;
        HandlerThread t = new HandlerThread("imgshifter-layers");
        t.start();
        draw = new Handler(t.getLooper()) {
            @Override
            public void handleMessage(Message m) {
                painted();
            }
        };
    }

    /** On the main thread: adds the two SurfaceViews to the stock screen, right above its video. top and side are {left, top, width, height}. */
    void attach(FrameLayout frame, View video, int[] top, int[] side) {
        this.side = new Sheet(frame, video, side[0], side[1], side[2], side[3]);
        this.top = new Sheet(frame, video, top[0], top[1], top[2], top[3]);
    }

    boolean attached() {
        return top != null;
    }

    /**
     * Sets what the surfaces should show from now on. Can be called from any thread. car and box:
     * the top view car and its box relative to the top surface (null for none; softly: fade out
     * when it goes). tail and nose: the large car's rear end and front with their top edges on the
     * side surface (null for none; they are hidden immediately). The drawing thread does the rest,
     * including the fade-in.
     */
    void show(Bitmap car, int[] box, boolean softly, Bitmap tail, int tailTop, Bitmap nose, int noseTop) {
        synchronized (asked) {
            askedBox = car == null || box == null ? null : new Rect(box[0], box[1], box[0] + box[2], box[1] + box[3]);
            askedCar = askedBox == null ? null : car;
            askedSoftly = softly;
            askedTail = tail;
            askedTailTop = tailTop;
            askedNose = nose;
            askedNoseTop = noseTop;
            news = true;
            wantCar = askedCar != null;
            wantTail = tail != null;
            wantNose = nose != null;
        }
        again();
    }

    private void again() {
        if (!draw.hasMessages(PAINT)) draw.sendEmptyMessage(PAINT);
    }

    /** Whether the car / the large rear end / the large front is on its surface or fading in (surface ready and picture requested) */
    boolean carOn() {
        Sheet s = top;
        return s != null && s.ready && wantCar;
    }

    boolean tailOn() {
        Sheet s = side;
        return s != null && s.ready && wantTail;
    }

    boolean noseOn() {
        Sheet s = side;
        return s != null && s.ready && wantNose;
    }

    /** For the log: the top surface's view, e.g. "171x423 at 1519,147, its surface there" */
    String where() {
        Sheet s = top;
        if (s == null) return "none";
        int[] at = new int[2];
        s.view.getLocationOnScreen(at);
        return s.view.getWidth() + "x" + s.view.getHeight() + " at " + at[0] + "," + at[1] + (s.ready ? ", its surface there" : ", no surface yet")
                + (s.view.isShown() ? "" : ", hidden with a view it lies in");
    }

    /** On the drawing thread: picks up the requested state, advances every fade and redraws each surface that changed */
    private void painted() {
        if (broken) return;
        try {
            boolean topNew = false, sideNew = false;
            synchronized (asked) {
                if (news) {
                    news = false;
                    topNew = aim(car, askedCar, askedSoftly) | (askedBox != null && !askedBox.equals(box));
                    if (askedBox != null) box = askedBox;
                    sideNew = aim(tail, askedTail, false) | aim(nose, askedNose, false) | tailTop != askedTailTop | noseTop != askedNoseTop;
                    tailTop = askedTailTop;
                    noseTop = askedNoseTop;
                }
            }
            long at = SystemClock.uptimeMillis();
            float part = dissolve <= 0 ? 1 : Math.min(1, (at - last) / (float) dissolve);
            last = at;
            boolean carMoves = car.now != car.to, sideMoves = tail.now != tail.to || nose.now != nose.to;
            boolean more = car.step(part) | tail.step(part) | nose.step(part);
            drawn(top, true, topNew || carMoves);
            drawn(side, false, sideNew || sideMoves);
            if (more) draw.sendEmptyMessageDelayed(PAINT, FRAME_MS);
        } catch (Throwable e) {
            broken = true;
            log.fail("drawing the surround view's car on its own surface: left as it is from here on", e);
        }
    }

    /** Redraws a surface if its content changed or the surface was recreated */
    private void drawn(Sheet sheet, boolean isTop, boolean changed) {
        if (sheet == null || !sheet.ready) return;
        int made = sheet.made;
        if ((changed || made != sheet.drawn) && on(sheet, isTop)) sheet.drawn = made;
    }

    /** Sets the target of a sprite. Returns true if that changed anything. */
    private boolean aim(Sprite s, Bitmap picture, boolean softly) {
        float to = picture == null ? 0 : 1;
        boolean changed = to != s.to || (picture != null && picture != s.picture);
        if (picture != null) s.picture = picture;
        s.to = to;
        s.softly = softly;
        // don't count the time since the last frame towards this fade
        if (changed && s.now != s.to) last = SystemClock.uptimeMillis();
        return changed;
    }

    /** Clears and draws one surface. Returns false if the canvas wasn't available (it is drawn when the surface comes back). */
    private boolean on(Sheet sheet, boolean isTop) {
        SurfaceHolder holder = sheet.view.getHolder();
        Canvas c = holder.lockCanvas();
        if (c == null) return false;
        try {
            c.drawColor(0, PorterDuff.Mode.CLEAR);
            if (isTop) {
                if (car.picture != null && car.now > 0 && box != null) {
                    paint.setAlpha(Math.round(255 * car.now));
                    c.drawBitmap(car.picture, null, box, paint);
                }
            } else {
                if (tail.picture != null && tail.now > 0) {
                    paint.setAlpha(Math.round(255 * tail.now));
                    c.drawBitmap(tail.picture, 0, tailTop, paint);
                }
                if (nose.picture != null && nose.now > 0) {
                    paint.setAlpha(Math.round(255 * nose.now));
                    c.drawBitmap(nose.picture, 0, noseTop, paint);
                }
            }
        } finally {
            holder.unlockCanvasAndPost(c);
        }
        return true;
    }
}
