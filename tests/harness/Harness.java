package dev.stinger.imgshifter;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

/** Entry point for the emulator harness into the mod's package-private classes: makes the same calls the hooks make and logs to logcat. */
public final class Harness {
    private final Swap swap;
    private final Swap.Log log;
    private final int paint;
    private Around around;

    /** paint: a value of the body_colour setting ("stock" for none) */
    public Harness(Context context, int keep, final String tag, String paint) {
        this.paint = BodyPaint.of(paint);
        log = new Swap.Log() {
            @Override
            public void say(String what) {
                Log.i(tag, what);
            }

            @Override
            public void fail(String what, Throwable t) {
                Log.e(tag, what, t);
            }

            @Override
            public boolean on() {
                return true;
            }
        };
        swap = new Swap(log, context, keep, this.paint);
    }

    /** Starts the surround-view overlay the way the mod does, with the bitmap built right away */
    public void aroundStart(boolean layered) {
        around = new Around(log, paint, layered);
        around.warm(0);
    }

    /** The stock video surface being destroyed, and created again */
    public void aroundClosing() {
        around.closing();
    }

    public void aroundOpening() {
        around.opening();
    }

    public void aroundScreen(View video) {
        around.screen(video);
    }

    public void aroundStatus(int viewMode) {
        around.status(viewMode);
    }

    public void aroundZoom(int state) {
        around.zoom(state);
    }

    public void aroundReverse(boolean engaged) {
        around.reverse(engaged);
    }

    public boolean aroundOn() {
        return around.on();
    }

    public boolean aroundTailOn() {
        return around.tailOn();
    }

    public boolean aroundNoseOn() {
        return around.noseOn();
    }

    public boolean image(ImageView v, int id) {
        return swap.image(v, id);
    }

    public boolean background(View v, int id) {
        return swap.background(v, id);
    }

    public void warm(int w, int h) {
        swap.warm(Art.SPLIT, w, h, 0);
    }

    public void warmCar(String name) {
        swap.warmCar(name, 0);
    }

    public void warmSigns() {
        swap.warmSigns(Art.SIGNS_AHEAD, 0);
    }
}
