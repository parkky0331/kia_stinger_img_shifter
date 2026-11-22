package harness;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import dev.stinger.imgshifter.Harness;

/**
 * Emulator harness activity. It stands in for a stock app (this APK is built under the stock
 * app's package name with the stock pictures as its drawables, see run.sh) and sets drawables on
 * views the way the stock code does, going through the mod's swap like the hook would.
 *
 *   --es cells "name,how,x,y,w,h,ours[,blue];..."
 *                                            how: image (ImageView), imagexy (ImageView, fitXY), bg (View background),
 *                                                 imagesel (ImageView, selected), imageoff (ImageView, disabled)
 *                                            ours: 1 through the swap, 0 the stock call only,
 *                                                  2 through the swap, which must leave it alone this time,
 *                                                  3 same, and 1.5 s later it must be ours
 *                                            blue: b the view must then show blue paint, n it must not
 *   --es never "name;name"                   drawables the swap must leave alone (checked and logged)
 *   --ez warm true                           build the 600 x 330 pictures ahead of time first, as in the right-hand panel
 *   --es paint <colour>                      the body colour setting (default "stock")
 *   --es warmcar <name>                      build that car picture ahead of time first, as in the camera app
 *   --ez warmsigns true                      build the camera app's icons ahead of time first
 *   --ez around true                         instead of cells: a surround-view screen that receives the car's reports
 *                                            one after another; the overlay must follow each one right away
 */
public final class Show extends Activity {
    static final String TAG = "imgshifter";
    private Harness swap;
    private FrameLayout root;
    private int bad;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xff0c0e12);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(root);
        String paint = getIntent().getStringExtra("paint"), car = getIntent().getStringExtra("warmcar");
        swap = new Harness(getApplicationContext(), getIntent().getIntExtra("keep", 4), TAG, paint == null ? "stock" : paint);
        if (getIntent().getBooleanExtra("around", false)) {
            around();
            return;
        }
        boolean warm = getIntent().getBooleanExtra("warm", false);
        if (warm) swap.warm(600, 330);
        if (car != null) swap.warmCar(car);
        boolean signs = getIntent().getBooleanExtra("warmsigns", false);
        if (signs) swap.warmSigns();
        if (warm || car != null || signs) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    fill();
                }
            }, warm ? 6000 : 2000);
        } else {
            fill();
        }
    }

    private int id(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    /** Surround view: builds the screen the way the stock app does, then feeds it the reports, checking the overlay after each one */
    private void around() {
        final Handler h = new Handler();
        final GLSurfaceView video = new GLSurfaceView(new AvmMode(this));
        video.setId(getResources().getIdentifier("camera_preview", "id", getPackageName()));
        final boolean layered = getIntent().getBooleanExtra("layered", false);
        swap.aroundStart(layered);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                root.addView(video, new FrameLayout.LayoutParams(1920, 720, Gravity.LEFT | Gravity.TOP));
                video.setRenderer(new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig config) {
                        gl.glClearColor(0.30f, 0.31f, 0.33f, 1f);
                    }

                    @Override
                    public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int w, int h) {
                    }

                    @Override
                    public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
                        gl.glClear(javax.microedition.khronos.opengles.GL10.GL_COLOR_BUFFER_BIT);
                    }
                });
                swap.aroundScreen(video);
                look("the screen is up, nothing reported yet", false, false, false);
            }
        }, 1200);
        // the car's signals reach the app on a separate thread; at start-up the app is told reverse is out
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread() {
                    @Override
                    public void run() {
                        swap.aroundReverse(false);
                    }
                }.start();
            }
        }, 1250);
        report(h, 1300, 2, 1, "the rear view reported, reverse out since the start", true, false);
        // it fades in over half a second: partly there after 0.1 s, fully after 0.7 s
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (layered) return;
                float a = root.getChildAt(root.indexOfChild(video) + 3).getAlpha();
                if (a <= 0f || a >= 0.6f) fail("a tenth of a second on, the car is " + a + " of the way on");
                else Log.i(TAG, "the car is coming on softly: " + a + " after a tenth of a second");
            }
        }, 1400);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (layered) return;
                float a = root.getChildAt(root.indexOfChild(video) + 3).getAlpha();
                if (a != 1f) fail("the car is not fully on after its dissolve: " + a);
                else Log.i(TAG, "the car is fully on after its dissolve");
            }
        }, 2000);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (layered) {
                    // two SurfaceViews right above the video: the top view's car (added last) and the large car's column
                    View top = root.getChildAt(root.indexOfChild(video) + 1), side = root.getChildAt(root.indexOfChild(video) + 2);
                    if (!(top instanceof android.view.SurfaceView) || top.getLeft() != 1519 || top.getTop() != 147 || top.getWidth() != 171 || top.getHeight() != 423) {
                        fail("the car's surface is not at its place: " + (top == null ? "none" : top.getLeft() + "," + top.getTop() + " " + top.getWidth() + "x" + top.getHeight()));
                    } else if (!(side instanceof android.view.SurfaceView) || side.getLeft() != 544 || side.getTop() != 0 || side.getWidth() != 340 || side.getHeight() != 720) {
                        fail("the large car's surface is not at its place: " + (side == null ? "none" : side.getLeft() + "," + side.getTop() + " " + side.getWidth() + "x" + side.getHeight()));
                    } else {
                        Log.i(TAG, "the overlay's two surfaces are right above the video: 1519,147 171x423 and 544,0 340x720");
                    }
                    return;
                }
                // the three overlay views sit right above the video: the large car's front (added last), its rear end, and the car
                View ahead = root.getChildAt(root.indexOfChild(video) + 1), large = root.getChildAt(root.indexOfChild(video) + 2);
                View over = root.getChildAt(root.indexOfChild(video) + 3);
                if (!(over instanceof ImageView) || over.getLeft() != 1519 || over.getTop() != 147 || over.getWidth() != 171 || over.getHeight() != 423) {
                    fail("the overlay is not at its place: " + (over == null ? "none" : over.getLeft() + "," + over.getTop() + " " + over.getWidth() + "x" + over.getHeight()));
                } else {
                    Log.i(TAG, "the overlay is right above the video at 1519,147 171x423 (the bed with its fading edge, and the car)");
                    painted("the surround view's car", over, true);
                }
                if (!(large instanceof ImageView) || large.getVisibility() != View.GONE) fail("the large car's view is not there, hidden");
                if (!(ahead instanceof ImageView) || ahead.getVisibility() != View.GONE) fail("the view of the large car's front is not there, hidden");
            }
        }, 1500);
        report(h, 2300, 20, 1, "the views of both sides: the same top view, the car stays", true, false);
        report(h, 2350, 16, 1, "the view down behind the car: the car stays and the large one goes on", true, true);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (layered) return;
                View large = root.getChildAt(root.indexOfChild(video) + 2);
                if (large.getLeft() != 544 || large.getTop() != 0 || large.getWidth() != 340 || large.getHeight() != 307) {
                    fail("the large car is not at its place: " + large.getLeft() + "," + large.getTop() + " " + large.getWidth() + "x" + large.getHeight());
                } else {
                    Log.i(TAG, "the large car is at 544,0 340x307 (the bed down from the top edge, and the car's rear end)");
                    painted("the large car", large, true);
                }
            }
        }, 2400);
        report(h, 3200, 21, 1, "the front views of both sides: the same top view, the car stays", true, false);
        report(h, 3250, 17, 1, "the view down ahead of the car: the car stays and the large one's front goes on", true, false, true);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (layered) return;
                View ahead = root.getChildAt(root.indexOfChild(video) + 1);
                if (ahead.getLeft() != 544 || ahead.getTop() != 403 || ahead.getWidth() != 340 || ahead.getHeight() != 317) {
                    fail("the large car's front is not at its place: " + ahead.getLeft() + "," + ahead.getTop() + " " + ahead.getWidth() + "x" + ahead.getHeight());
                } else {
                    Log.i(TAG, "the large car's front is at 544,403 340x317 (the bed up from the bottom edge, and the car's front)");
                    painted("the large car's front", ahead, true);
                }
            }
        }, 3270);
        report(h, 3300, 16, 1, "the view down behind the car again", true, true);
        report(h, 3400, 2, 1, "the rear view: the large car comes off", true, false);
        // zoomed out, the top view's car is smaller: the overlay follows at 5 % per step and the large car is left alone
        report(h, 3500, 2, 0, "zoomed out a step: the car stays, smaller", true, false);
        sized(h, 3510, video, 1523, 157, 162, 402);
        report(h, 3520, 2, 2, "zoomed out two steps", true, false);
        sized(h, 3530, video, 1527, 168, 154, 381);
        report(h, 3540, 16, 0, "the view down behind the car, zoomed: the car, not the large one", true, false);
        report(h, 3560, 2, 1, "back at the measured zoom", true, false);
        sized(h, 3570, video, 1519, 147, 171, 423);
        report(h, 3600, 1, 1, "off", false, false);
        // Timing as seen on the car: reverse in and the rear view arrive within 0.1 s of each other, in
        // either order; reverse out comes about 0.5 s before the front views, and "off" about 3 s later.
        // The video changes with the gear and the report comes later, so reverse itself is acted on,
        // using the expected layout.
        reverse(h, 3700, true, "reverse engaged, the camera unit has not reported yet: the rear view is expected, the car is there", true, false);
        report(h, 3750, 16, 1, "the view down behind the car reported", true, true);
        reverse(h, 3900, false, "reverse let go: the front layout shown last (the view ahead) at once, its large front for the large rear end", true, false, true);
        report(h, 4000, 16, 1, "the old layout reported once more, nothing new", true, false, true);
        report(h, 4100, 21, 1, "the front layout the car goes to: the car has stayed", true, false);
        report(h, 4200, 3, 1, "the front view", true, false);
        report(h, 4250, 1, 1, "off", false, false);
        report(h, 4300, 2, 1, "the rear view reported before reverse is", true, false);
        reverse(h, 4350, true, "and reverse engaged", true, false);
        reverse(h, 4450, false, "let go in the rear view: nothing to take off", true, false);
        report(h, 4500, 16, 1, "the view down behind the car, picked with reverse out", true, true);
        reverse(h, 4600, true, "reverse engaged in it", true, true);
        reverse(h, 4700, false, "let go, and the camera unit keeps the view", true, false);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                look("the same view still reported a while after: the large car is back", true, true, false);
            }
        }, 6400);
        // the other way round: the view ahead is up with reverse out, and engaging reverse removes its large car from the video
        report(h, 6450, 17, 1, "the view down ahead of the car, reverse out", true, false, true);
        reverse(h, 6500, true, "reverse engaged: the large front off at once, the car stays", true, false, false);
        report(h, 6550, 16, 1, "the view down behind the car reported", true, true, false);
        reverse(h, 6600, false, "reverse let go again: the large front at once", true, false, true);
        report(h, 6650, 17, 1, "the view down ahead reported", true, false, true);
        reverse(h, 6700, true, "reverse engaged, and the camera unit keeps the view", true, false, false);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                look("the same view still reported a while after: the large front is back", true, false, true);
            }
        }, 8400);
        // the stock screen closes its video while the camera unit's "off" is still being handled. In layer
        // mode the overlay goes with the video right away; as views in the window it can't, and goes with the report
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                swap.aroundClosing();
                look("the video surface is going", !layered, false, !layered);
                swap.aroundOpening();
                look("the video surface is made again", true, false, true);
            }
        }, 8450);
        final int last = getIntent().getIntExtra("last", 0);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, bad == 0 ? "CHECK ok" : "CHECK FAILED: " + bad);
                // for the screenshot taken at the end: the layout to leave on screen
                if (last != 0) {
                    swap.aroundReverse(last == 16);
                    swap.aroundStatus(last);
                }
            }
        }, 8600);
    }

    /** Sends a camera unit report and checks in the same turn: the cars must follow immediately */
    private void report(Handler h, long at, int viewMode, int zoom, String what, boolean on, boolean large) {
        report(h, at, viewMode, zoom, what, on, large, false);
    }

    /** Checks that the car's view (third above the video) is laid out at x, y with size w x h */
    private void sized(Handler h, long at, final View video, final int x, final int y, final int w, final int ht) {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                // in layer mode the car is drawn smaller inside its surface, so there is no new layout to check
                if (getIntent().getBooleanExtra("layered", false)) return;
                View over = root.getChildAt(root.indexOfChild(video) + 3);
                FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) over.getLayoutParams();
                if (l.leftMargin != x || l.topMargin != y || l.width != w || l.height != ht) {
                    fail("the car is laid out at " + l.leftMargin + "," + l.topMargin + " " + l.width + "x" + l.height + ", not " + x + "," + y + " " + w + "x" + ht);
                } else {
                    Log.i(TAG, "the car is laid out at " + x + "," + y + " " + w + "x" + ht);
                }
            }
        }, at);
    }

    private void report(Handler h, long at, final int viewMode, final int zoom, final String what, final boolean on, final boolean large, final boolean ahead) {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                swap.aroundZoom(zoom);
                swap.aroundStatus(viewMode);
                look(what, on, large, ahead);
            }
        }, at);
    }

    private void reverse(Handler h, long at, boolean engaged, String what, boolean on, boolean large) {
        reverse(h, at, engaged, what, on, large, false);
    }

    private void reverse(Handler h, long at, final boolean engaged, final String what, final boolean on, final boolean large, final boolean ahead) {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                swap.aroundReverse(engaged);
                look(what, on, large, ahead);
            }
        }, at);
    }

    /** on: the car; large: the large car's rear end; ahead: its front */
    private void look(String what, boolean on, boolean large, boolean ahead) {
        boolean is = swap.aroundOn(), tail = swap.aroundTailOn(), nose = swap.aroundNoseOn();
        Log.i(TAG, "around: " + what + ": car " + (is ? "on" : "off") + ", large rear end " + (tail ? "on" : "off") + ", large front " + (nose ? "on" : "off"));
        if (is != on) fail("around: " + what + ": the car is " + (is ? "on" : "off"));
        if (tail != large) fail("around: " + what + ": the large car's rear end is " + (tail ? "on" : "off"));
        if (nose != ahead) fail("around: " + what + ": the large car's front is " + (nose ? "on" : "off"));
    }

    private void fill() {
        String cells = getIntent().getStringExtra("cells");
        if (cells != null) {
            for (String cell : cells.split(";")) {
                String[] c = cell.split(",");
                String name = c[0], how = c[1];
                int x = Integer.parseInt(c[2]), y = Integer.parseInt(c[3]), w = Integer.parseInt(c[4]), h = Integer.parseInt(c[5]);
                boolean ours = "1".equals(c[6]), later = "3".equals(c[6]), early = "2".equals(c[6]) || later;
                final Boolean blue = c.length < 8 ? null : Boolean.valueOf("b".equals(c[7]));
                int id = id(name);
                if (id == 0) {
                    fail("no drawable " + name);
                    continue;
                }
                final boolean bg = "bg".equals(how);
                final View v = bg ? new View(this) : new ImageView(this);
                if ("imagexy".equals(how)) ((ImageView) v).setScaleType(ImageView.ScaleType.FIT_XY);
                if ("imagesel".equals(how)) v.setSelected(true);
                if ("imageoff".equals(how)) v.setEnabled(false);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.LEFT | Gravity.TOP);
                lp.leftMargin = x;
                lp.topMargin = y;
                root.addView(v, lp);
                boolean swapped = set(v, id, bg, ours || early);
                Log.i(TAG, "cell " + name + " " + how + " ours=" + ours + " swapped=" + swapped);
                if (early && swapped) fail(name + " was swapped before its picture could be ready");
                if (later) {
                    final String what = name;
                    final int again = id;
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!set(v, again, bg, true)) fail(what + " was not swapped at the second asking");
                            if (blue != null) painted(what, v, blue.booleanValue());
                        }
                    }, 1500);
                } else if (blue != null) {
                    painted(name, v, blue.booleanValue());
                }
                if (ours) {
                    settle(name, v, bg);
                    if (!swapped) fail(name + " was not swapped");
                    // the stock code sets the same drawable over and over: ours must stay, and stay the same object
                    Object before = bg ? v.getBackground() : ((ImageView) v).getDrawable();
                    boolean again = set(v, id, bg, true);
                    Object after = bg ? v.getBackground() : ((ImageView) v).getDrawable();
                    if (!again || before != after) fail(name + ": set again, swapped=" + again + ", same drawable=" + (before == after));
                }
            }
        }
        String never = getIntent().getStringExtra("never");
        if (never != null) {
            for (String name : never.split(";")) {
                int id = id(name);
                if (id == 0) {
                    fail("no drawable " + name);
                    continue;
                }
                ImageView v = new ImageView(this);
                if (swap.image(v, id) || swap.background(v, id)) fail(name + " was swapped but must be left alone");
                else Log.i(TAG, "left alone: " + name);
            }
            // ids that belong to nobody, and framework ids
            ImageView v = new ImageView(this);
            if (swap.image(v, 0) || swap.image(v, 0x7f7f7f7f) || swap.image(v, android.R.drawable.ic_delete)) fail("a foreign id was swapped");
        }
        Log.i(TAG, bad == 0 ? "CHECK ok" : "CHECK FAILED: " + bad);
        if (bad != 0) {
            TextView t = new TextView(this);
            t.setText("CHECK FAILED: " + bad);
            t.setTextColor(0xffff4040);
            t.setTextSize(30);
            root.addView(t);
        }
    }

    /** 2.5 s later: has the view been given another picture (the sharp one, for an enlarged picture)? */
    private void settle(final String name, final View v, final boolean bg) {
        final Object first = bg ? v.getBackground() : ((ImageView) v).getDrawable();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Object now = bg ? v.getBackground() : ((ImageView) v).getDrawable();
                Log.i(TAG, "settled " + name + " exchanged=" + (now != first));
            }
        }, 2500);
    }

    /** Shortly after, once the view has been drawn: does it show blue paint (the test's body colour)? */
    private void painted(final String name, final View v, final boolean want) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                int w = v.getWidth(), h = v.getHeight(), n = 0;
                if (w > 0 && h > 0) {
                    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    v.draw(new Canvas(b));
                    int[] px = new int[w * h];
                    b.getPixels(px, 0, w, 0, 0, w, h);
                    for (int p : px) {
                        int red = (p >> 16) & 255, green = (p >> 8) & 255, bl = p & 255;
                        if ((p >>> 24) > 200 && bl > red + 50 && bl > green + 30) n++;
                    }
                }
                Log.i(TAG, "paint on " + name + ": " + n + " blue pixels of " + w * h);
                if (want ? n * 100 < w * h * 3 : n != 0) fail(name + (want ? " shows no blue paint" : " shows blue paint"));
            }
        }, 600);
    }

    /** What the hook does: ours if the swap takes it, otherwise the stock call */
    private boolean set(View v, int id, boolean bg, boolean ours) {
        boolean swapped = ours && (bg ? swap.background(v, id) : swap.image((ImageView) v, id));
        if (!swapped) {
            if (bg) v.setBackgroundResource(id);
            else ((ImageView) v).setImageResource(id);
        }
        return swapped;
    }

    private void fail(String what) {
        bad++;
        Log.e(TAG, "CHECK FAIL " + what);
    }
}
