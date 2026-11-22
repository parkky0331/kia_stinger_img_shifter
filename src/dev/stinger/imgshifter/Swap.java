package dev.stinger.imgshifter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Swaps pictures for one app process. Called on every setImageResource / setBackgroundResource
 * in the app: if the drawable is one of the stock pictures this mod replaces, the view gets ours
 * instead. Everything else passes through untouched, and so does a picture we fail to build (the
 * stock one is shown as before).
 *
 * Only the app's own views and drawables are considered (a widget hosted from another app has
 * its own ids). The answer for each drawable id is cached, and so are the pictures built.
 *
 * A picture that needs enlarging is first built the quick way so the view has something right
 * away, then rebuilt with the sharper filter in the background. Views still showing the quick
 * one are given the sharp one when it is ready.
 *
 * The camera app's car pictures (the top-down car and the small icons with a car in them,
 * repainted in the body colour) are never built on the main thread, because that app must not be
 * kept waiting. They are built ahead of time, or in the background once a view has asked for
 * one. A view that asks before its picture is ready keeps the stock one that time.
 */
final class Swap {
    /** The mod's log and on/off switch, behind an interface so this also runs without HMG-API (in the emulator harness) */
    interface Log {
        void say(String what);

        void fail(String what, Throwable t);

        boolean on();
    }

    /** What a drawable id turned out to be */
    private static final class Seen {
        final String name;
        final int family, mode, w, h;

        Seen(String name, int family, int mode, int w, int h) {
            this.name = name;
            this.family = family;
            this.mode = mode;
            this.w = w;
            this.h = h;
        }
    }

    private static final Seen NOT_OURS = new Seen(null, Art.NONE, -1, 0, 0);

    /** A view currently showing a quick picture, as its image or as its background */
    private static final class Target {
        final WeakReference<View> view;
        final boolean background;

        Target(View view, boolean background) {
            this.view = new WeakReference<View>(view);
            this.background = background;
        }
    }

    private final Log log;
    private final String own;
    private final Context context;
    private final Maker maker;
    private final int keep;
    private final SparseArray<Seen> seen = new SparseArray<Seen>();
    /** Pictures built so far by key, least recently used first, and the keys that failed */
    private final LinkedHashMap<String, Bitmap> made = new LinkedHashMap<String, Bitmap>(8, 0.75f, true);
    private final HashSet<String> failed = new HashSet<String>();
    private final LinkedHashMap<Integer, Bitmap> bases = new LinkedHashMap<Integer, Bitmap>(4, 0.75f, true);
    /** Quick pictures whose sharp version is being built, and the views showing them */
    private final HashMap<Bitmap, ArrayList<Target>> waiting = new HashMap<Bitmap, ArrayList<Target>>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String shown;
    private long shownAt;
    private int troubles;
    private boolean early;
    /** Body colour for the camera app's car pictures (index into BodyPaint's list, or -1 to leave them alone) */
    private final int paint;
    /** Icons built so far by drawable id (each view gets its own drawable from the shared constant state), and the ids requested */
    private final SparseArray<Drawable.ConstantState> signs = new SparseArray<Drawable.ConstantState>();
    private final SparseBooleanArray asked = new SparseBooleanArray(), signShown = new SparseBooleanArray();
    private final LinkedBlockingQueue<Integer> wanted = new LinkedBlockingQueue<Integer>();
    /** Icon drawables handed to views, each with its icon's id */
    private final WeakHashMap<Drawable, Integer> given = new WeakHashMap<Drawable, Integer>();
    private Thread signMaker;

    /** keep: how many built pictures stay in memory. paint: body colour index, or -1 for none. */
    Swap(Log log, Context context, int keep, int paint) {
        this.log = log;
        this.context = context;
        this.own = context.getPackageName();
        this.maker = new Maker(context);
        this.keep = keep;
        this.paint = paint;
    }

    /** setImageResource(id) on v. Returns true if our picture was set and the stock call should be skipped. */
    boolean image(ImageView v, int id) {
        Seen s = ours(v, id);
        if (s == null) return false;
        if (s.family == Art.SIGN) {
            Drawable sign = sign(v, id, s, v.getDrawable());
            if (sign != null && sign != v.getDrawable()) v.setImageDrawable(sign);
            return sign != null;
        }
        Bitmap b = picture(v, id, s);
        if (b == null) return false;
        Drawable d = v.getDrawable();
        if (!(d instanceof BitmapDrawable) || ((BitmapDrawable) d).getBitmap() != b) {
            v.setImageDrawable(new BitmapDrawable(v.getResources(), b));
            shows(id);
            waits(v, b, false);
        }
        return true;
    }

    /** setBackgroundResource(id) on v, handled the same way */
    @SuppressWarnings("deprecation")
    boolean background(View v, int id) {
        Seen s = ours(v, id);
        if (s == null) return false;
        if (s.family == Art.SIGN) {
            Drawable sign = sign(v, id, s, v.getBackground());
            if (sign != null && sign != v.getBackground()) v.setBackgroundDrawable(sign);
            return sign != null;
        }
        Bitmap b = picture(v, id, s);
        if (b == null) return false;
        Drawable d = v.getBackground();
        if (!(d instanceof BitmapDrawable) || ((BitmapDrawable) d).getBitmap() != b) {
            v.setBackgroundDrawable(new BitmapDrawable(v.getResources(), b));
            shows(id);
            waits(v, b, true);
        }
        return true;
    }

    /** What the drawable is to us, if both view and drawable belong to the app and it is one we replace. Otherwise null. */
    private Seen ours(View v, int id) {
        if ((id >>> 24) != 0x7f || !log.on()) return null;
        try {
            Context c = v.getContext();
            if (c == null || !own.equals(c.getPackageName())) return null;
            Seen s = whatIs(v.getResources(), id);
            return s == NOT_OURS ? null : s;
        } catch (Throwable t) {
            if (troubles++ < 5) log.fail("drawable " + Integer.toHexString(id), t);
            return null;
        }
    }

    private Bitmap picture(View v, int id, Seen s) {
        try {
            return ready(v.getResources(), id, s);
        } catch (Throwable t) {
            if (troubles++ < 5) log.fail("drawable " + Integer.toHexString(id), t);
            return null;
        }
    }

    /**
     * Drawable of an icon for this view. If the view already shows this icon it is reused (the app
     * sets the same one over and over), otherwise a new one is created. Returns null if it isn't
     * built yet (it gets queued and the stock one is shown this time) or can't be built.
     */
    private Drawable sign(View v, int id, Seen s, Drawable now) {
        Drawable.ConstantState state;
        boolean first, show;
        synchronized (signs) {
            Integer has = now == null ? null : given.get(now);
            if (has != null && has.intValue() == id) return now;
            state = signs.get(id);
            first = state == null && !asked.get(id);
            if (first) asked.put(id, true);
            show = state != null && !signShown.get(id);
            if (show) signShown.put(id, true);
        }
        if (state == null) {
            if (first) {
                wanted.add(Integer.valueOf(id));
                warmSigns(new String[0], 0);
                log.say(s.name + " was asked for before its picture was ready: the stock one this time");
            }
            return null;
        }
        try {
            Drawable d = state.newDrawable(v.getResources());
            synchronized (signs) {
                given.put(d, Integer.valueOf(id));
            }
            if (show) log.say("shows ours for " + s.name);
            return d;
        } catch (Throwable t) {
            if (troubles++ < 5) log.fail("sign " + s.name, t);
            return null;
        }
    }

    /** Works out what a drawable id is from its name (done once per id) */
    private Seen whatIs(Resources res, int id) {
        synchronized (seen) {
            Seen s = seen.get(id);
            if (s != null) return s;
        }
        String name;
        try {
            name = res.getResourceEntryName(id);
        } catch (Resources.NotFoundException e) {
            name = null;
        }
        Seen s = NOT_OURS;
        int family = Art.family(name);
        if (family == Art.CAR) {
            int[] size = Maker.size(res, id);
            if (paint >= 0) s = new Seen(name, family, -1, size[0], size[1]);
            log.say("sees " + name + " " + size[0] + "x" + size[1] + ": " + (s == NOT_OURS ? "left as it is" : "car in " + BodyPaint.KEYS[paint]));
        } else if (family == Art.SIGN) {
            if (paint >= 0) s = new Seen(name, family, -1, 0, 0);
            log.say("sees " + name + ": " + (s == NOT_OURS ? "left as it is" : "sign in " + BodyPaint.KEYS[paint]));
        } else if (family != Art.NONE) {
            int mode = Art.mode(name);
            int[] size = Maker.size(res, id);
            // the settings app also has small labels under the same names; only the full-size pictures are replaced
            if (mode >= 0 && size[0] >= 400 && size[1] >= 200) s = new Seen(name, family, mode, size[0], size[1]);
            log.say("sees " + name + " " + size[0] + "x" + size[1] + ": "
                    + (s == NOT_OURS ? "left as it is" : Art.FAMILIES[family] + " " + Art.MODES[mode]));
        }
        synchronized (seen) {
            seen.put(id, s);
        }
        return s;
    }

    private static String key(int family, int mode, int w, int h) {
        return Art.FAMILIES[family] + " " + Art.MODES[Art.twin(mode)] + " " + w + "x" + h;
    }

    private Bitmap ready(Resources res, int id, Seen s) {
        if (s.family == Art.CAR) {
            // use it only if it was built ahead of time: no work for us on the camera app's main thread
            Bitmap b;
            synchronized (made) {
                b = made.get("car " + s.name);
            }
            if (b == null && !early) {
                early = true;
                log.say(s.name + " was asked for before its picture was ready: the stock one this time");
            }
            return b;
        }
        // a help picture is the stock drawing with one part painted over, so there is one per drawable
        String key = s.family == Art.GUIDE ? "guide " + s.name : key(s.family, s.mode, s.w, s.h);
        synchronized (made) {
            Bitmap b = made.get(key);
            if (b != null || failed.contains(key)) return b;
        }
        long t0 = SystemClock.uptimeMillis();
        Bitmap b = null, base = null;
        try {
            base = base(s.mode);
            if (base != null) b = s.family == Art.GUIDE ? maker.guide(base, res, id) : maker.fitted(base, s.w, s.h, false);
            if (b == null) log.say("no picture for " + key + ": " + maker.why);
        } catch (Throwable t) {
            log.fail("making " + key, t);
            b = null;
        }
        Bitmap kept = store(key, b, t0);
        if (kept != null && kept == b && s.family != Art.GUIDE && Maker.enlarges(s.w, s.h)) sharpen(key, kept, base, s.w, s.h);
        return kept;
    }

    /**
     * Called after an enlarged picture was built the quick way. Builds the sharp one in the
     * background (takes seconds on a head unit), stores it in place of the quick one and hands it
     * to the views still showing the quick one.
     */
    private void sharpen(final String key, final Bitmap quick, final Bitmap base, final int w, final int h) {
        synchronized (made) {
            waiting.put(quick, new ArrayList<Target>());
        }
        Thread t = new Thread("imgshifter-sharp") {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                long t0 = SystemClock.uptimeMillis();
                Bitmap fine = null;
                try {
                    fine = new Maker(context).fitted(base, w, h, true);
                } catch (Throwable e) {
                    log.fail("sharpening " + key, e);
                }
                final Bitmap sharp = fine;
                if (sharp != null) {
                    synchronized (made) {
                        if (made.get(key) == quick) made.put(key, sharp);
                    }
                    log.say("sharpened " + key + " in " + (SystemClock.uptimeMillis() - t0) + " ms");
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        hand(quick, sharp);
                    }
                });
            }
        };
        t.setDaemon(true);
        t.start();
    }

    private void waits(View v, Bitmap b, boolean background) {
        synchronized (made) {
            ArrayList<Target> list = waiting.get(b);
            if (list != null) list.add(new Target(v, background));
        }
    }

    /** On the main thread: gives the sharp picture to the views still showing the quick one (null: it failed and they keep theirs) */
    @SuppressWarnings("deprecation")
    private void hand(Bitmap quick, Bitmap sharp) {
        ArrayList<Target> list;
        synchronized (made) {
            list = waiting.remove(quick);
        }
        if (list == null || sharp == null) return;
        for (Target t : list) {
            View v = t.view.get();
            if (v == null) continue;
            Drawable d = t.background ? v.getBackground() : ((ImageView) v).getDrawable();
            if (!(d instanceof BitmapDrawable) || ((BitmapDrawable) d).getBitmap() != quick) continue;
            BitmapDrawable now = new BitmapDrawable(v.getResources(), sharp);
            if (t.background) v.setBackgroundDrawable(now);
            else ((ImageView) v).setImageDrawable(now);
        }
    }

    /** Stores a built picture (null records that it can't be built) and evicts the least recently used ones over the limit */
    private Bitmap store(String key, Bitmap b, long t0) {
        synchronized (made) {
            if (b == null) {
                failed.add(key);
                return null;
            }
            Bitmap before = made.get(key);
            if (before != null) return before;
            made.put(key, b);
            Iterator<String> it = made.keySet().iterator();
            while (made.size() > keep && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        log.say("made " + key + " in " + (SystemClock.uptimeMillis() - t0) + " ms");
        return b;
    }

    /** Floor-and-car picture of a mode. The last two are kept because a mode's pop-up and help picture are requested one after the other. */
    private Bitmap base(int mode) throws Exception {
        Integer k = Integer.valueOf(Art.twin(mode));
        synchronized (bases) {
            Bitmap b = bases.get(k);
            if (b != null) return b;
        }
        Bitmap b = maker.base(mode);
        if (b == null) return null;
        synchronized (bases) {
            bases.put(k, b);
            Iterator<Integer> it = bases.keySet().iterator();
            while (bases.size() > 2 && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        return b;
    }

    /** Logs one line when the picture on screen changes (the panel sets the same one over and over) */
    private void shows(int id) {
        String name;
        synchronized (seen) {
            name = seen.get(id).name;
        }
        long now = SystemClock.uptimeMillis();
        if (!name.equals(shown) || now - shownAt > 60000) {
            shown = name;
            shownAt = now;
            log.say("shows ours for " + name);
        }
    }

    /**
     * Builds the camera app's car picture in the body colour ahead of time, off the main thread.
     * name is the drawable's name in this app; the work starts afterMs after the app started.
     */
    void warmCar(final String name, final long afterMs) {
        if (paint < 0) return;
        Thread t = new Thread("imgshifter-car") {
            @Override
            public void run() {
                try {
                    Thread.sleep(afterMs);
                } catch (InterruptedException e) {
                    return;
                }
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                String key = "car " + name;
                long t0 = SystemClock.uptimeMillis();
                try {
                    Resources res = context.getResources();
                    int id = res.getIdentifier(name, "drawable", own);
                    if (id == 0) {
                        log.say("this app has no " + name + ": its car picture stays as it is");
                        return;
                    }
                    Maker mine = new Maker(context);
                    Bitmap b = mine.painted(res, id, paint);
                    if (b == null) log.say("no picture for " + key + ": " + mine.why);
                    else store(key, b, t0);
                } catch (Throwable e) {
                    log.fail("making " + key, e);
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /**
     * Starts the single background thread that builds the camera app's icons. It first builds
     * those of the given names that the app has (afterMs after the app started), then whatever
     * views ask for.
     */
    void warmSigns(final String[] names, final long afterMs) {
        if (paint < 0) return;
        synchronized (signs) {
            if (signMaker != null) return;
            signMaker = new Thread("imgshifter-signs") {
                @Override
                public void run() {
                    try {
                        Thread.sleep(afterMs);
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        Resources res = context.getResources();
                        Maker mine = new Maker(context);
                        long t0 = SystemClock.uptimeMillis();
                        int made = 0, absent = 0;
                        for (String name : names) {
                            int id = res.getIdentifier(name, "drawable", own);
                            if (id == 0) {
                                absent++;
                                continue;
                            }
                            synchronized (signs) {
                                if (asked.get(id)) continue;
                                asked.put(id, true);
                            }
                            if (makeSign(mine, res, id, name)) made++;
                        }
                        if (names.length > 0) {
                            log.say("made " + made + " signs ahead in " + (SystemClock.uptimeMillis() - t0) + " ms (" + absent + " of "
                                    + names.length + " names are not in this app)");
                        }
                        while (true) {
                            int id = wanted.take().intValue();
                            t0 = SystemClock.uptimeMillis();
                            String name = res.getResourceEntryName(id);
                            if (makeSign(mine, res, id, name)) log.say("made sign " + name + " in " + (SystemClock.uptimeMillis() - t0) + " ms");
                        }
                    } catch (InterruptedException e) {
                        return;
                    } catch (Throwable e) {
                        log.fail("the signs' thread", e);
                    }
                }
            };
            signMaker.setDaemon(true);
            signMaker.start();
        }
    }

    private boolean makeSign(Maker mine, Resources res, int id, String name) {
        if (!log.on()) return false;
        try {
            Drawable d = mine.sign(res, id, paint);
            Drawable.ConstantState state = d == null ? null : d.getConstantState();
            if (state == null) {
                log.say("no picture for sign " + name + ": " + (d == null ? mine.why : "it can't be shared between views"));
                return false;
            }
            synchronized (signs) {
                signs.put(id, state);
            }
            return true;
        } catch (Throwable e) {
            log.fail("making sign " + name, e);
            return false;
        }
    }

    /**
     * Builds the pictures of one size for every mode ahead of time, off the main thread and a
     * while after the app started, so the first mode change doesn't have to wait.
     */
    void warm(final int family, final int w, final int h, final long afterMs) {
        Thread t = new Thread("imgshifter-warm") {
            @Override
            public void run() {
                try {
                    Thread.sleep(afterMs);
                } catch (InterruptedException e) {
                    return;
                }
                // low priority so the app's own work goes first. Nothing waits on this thread (no lock is held while a picture is built).
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                Maker mine = new Maker(context); // separate instance: the main thread uses the other one
                for (int mode = 0; mode < Art.MODES.length; mode++) {
                    String key = key(family, mode, w, h);
                    synchronized (made) {
                        if (made.containsKey(key) || failed.contains(key)) continue;
                    }
                    if (!log.on()) return;
                    long t0 = SystemClock.uptimeMillis();
                    Bitmap b = null;
                    try {
                        Bitmap base = mine.base(mode);
                        if (base != null) b = mine.fitted(base, w, h, true);
                        if (b == null) log.say("no picture for " + key + ": " + mine.why);
                    } catch (Throwable e) {
                        log.fail("making " + key + " ahead", e);
                    }
                    if (b == null) return; // a missing source picture is missing for every mode
                    store(key, b, t0);
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }
}
