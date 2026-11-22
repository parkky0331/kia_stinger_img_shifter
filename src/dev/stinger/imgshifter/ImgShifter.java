package dev.stinger.imgshifter;

import android.opengl.GLSurfaceView;
import android.view.View;
import android.widget.ImageView;

import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;

/**
 * Stinger Image Shifter: an HMG-API mod that swaps a few stock pictures on the Kia Stinger's
 * head unit.
 *
 * Drive mode: the stock apps show a generic saloon for every drive mode. This shows the Stinger's
 * see-through drawing instead, tinted in the mode's colour on that mode's floor picture, the way
 * the older infotainment did. It applies in three places:
 *   - the right-hand panel and its mode-change pop-up (com.mobis.splitscreen.right)
 *   - the mode-change pop-up of the vehicle settings app
 *   - the small car in the drive mode help picture of the vehicle settings app
 *
 * Body colour (experimental): shows the car on the camera screen in the colour picked in the
 * mod's settings. The surround-view car is part of the camera unit's video and can't be
 * recoloured, so Around draws a car in that colour on top of it. The silver top-down car icons of
 * the camera app (view buttons, parking sensor car, door icons) are repainted to match. With the
 * setting on "stock" the camera app isn't hooked at all.
 *
 * No files are modified. The mod hooks ImageView.setImageResource and View.setBackgroundResource
 * in those apps and, for the drawables listed in Art, hands over its own bitmap built from
 * pictures already on the car. If the mod is off or a source picture is missing, the stock
 * picture is shown as usual.
 */
public final class ImgShifter extends ModEntry {
    static final String SPLIT_APP = "com.mobis.splitscreen.right", CAMERA_APP = "com.hkmc.system.app.rearcamera";
    /** Mode-change pop-up of the settings app. Logged when it opens because not every car shows it. */
    static final String POPUP_ACTIVITY = "com.android.vehiclesettings.drivemode.DriveModePopupActivity";
    /** Camera app screens. Which one starts tells us where that car's top-view picture comes from. */
    static final String[] CAMERA_SCREENS = { "RearMode", "AvmMode", "AvmModeLowCam" };

    @Override
    public void appLoaded(final ModContext mod, final LoadedApp app) {
        final boolean split = SPLIT_APP.equals(app.packageName), settings = Maker.SETTINGS.equals(app.packageName),
                camera = CAMERA_APP.equals(app.packageName);
        if (!split && !settings && !camera) return;
        if (app.context == null) {
            mod.log("no application in " + app.processName + ": nothing changed there");
            return;
        }
        // The camera app is hooked (or not) when it starts, so a changed setting applies from its next start.
        final int paint = BodyPaint.of(mod.settingString("body_colour", "stock"));
        if (camera && paint < 0) {
            mod.log("body colour is stock: the camera app is left alone");
            return;
        }
        final Swap.Log log = new Swap.Log() {
            @Override
            public void say(String what) {
                mod.log(what);
            }

            @Override
            public void fail(String what, Throwable t) {
                mod.error(what, t);
            }

            @Override
            public boolean on() {
                return mod.enabled();
            }
        };
        final Swap swap = new Swap(log, app.context, split ? 6 : camera ? 2 : 4, paint);
        try {
            Hooks.findAndHookMethod(ImageView.class, "setImageResource", int.class, new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    if (swap.image((ImageView) p.thisObject, ((Integer) p.args[0]).intValue())) p.setResult(null);
                }
            });
            if (!split) {
                Hooks.findAndHookMethod(View.class, "setBackgroundResource", int.class, new MethodHook() {
                    @Override
                    protected void beforeHookedMethod(Param p) {
                        if (swap.background((View) p.thisObject, ((Integer) p.args[0]).intValue())) p.setResult(null);
                    }
                });
            }
        } catch (Throwable t) {
            mod.error("hooks in " + app.processName, t);
            return;
        }
        if (settings) {
            watch(mod, app, POPUP_ACTIVITY, android.os.Bundle.class, "the settings app's mode pop-up opened");
        }
        if (camera) {
            for (String screen : CAMERA_SCREENS) {
                watch(mod, app, CAMERA_APP + ".services." + screen, null, "camera screen started: " + screen);
            }
            swap.warmCar(Art.CAR_NAME, 8000);
            swap.warmSigns(Art.SIGNS_AHEAD, 9000);
            try {
                around(mod, app, new Around(log, paint, mod.settingBoolean("around_view_layer", false)));
            } catch (Throwable t) {
                mod.error("the surround view's car is left as it is", t);
            }
        }
        // The panel needs its pictures the moment the mode changes, so build them shortly after the app is up.
        if (split) swap.warm(Art.SPLIT, Maker.SPLIT_W, Maker.SPLIT_H, 20000);
        mod.log("ready in " + app.processName + " (version " + mod.version + ")");
    }

    /**
     * Wires up the surround-view overlay. It needs three things from the camera app:
     *   - the video view (GLSurfaceView.setRenderer is hooked; Around picks the surround-view screen)
     *   - the camera unit's status reports (the listener the app registers with AvmManager)
     *   - reverse gear changes (the vehicle signal listener held by the screen)
     */
    private static void around(final ModContext mod, final LoadedApp app, final Around over) {
        try {
            Hooks.findAndHookMethod(GLSurfaceView.class, "setRenderer", GLSurfaceView.Renderer.class, new MethodHook() {
                private final java.util.HashSet<Class<?>> heard = new java.util.HashSet<Class<?>>();

                @Override
                protected void afterHookedMethod(Param p) {
                    View video = (View) p.thisObject;
                    if (!over.screen(video)) return;
                    if (over.layered()) surface(mod, video.getContext().getClass(), over);
                    Object signals = signals(app, video.getContext());
                    if (signals == null) {
                        mod.log("the screen has no listener for the car's signals: the overlay goes by the camera unit's reports alone");
                        return;
                    }
                    synchronized (heard) {
                        if (!heard.add(signals.getClass())) return;
                    }
                    try {
                        Hooks.findAndHookMethod(signals.getClass(), "onReverseStatus", boolean.class, new MethodHook() {
                            @Override
                            protected void afterHookedMethod(Param p) {
                                over.reverse(((Boolean) p.args[0]).booleanValue());
                            }
                        });
                        mod.log("listening for reverse with " + signals.getClass().getName());
                    } catch (Throwable t) {
                        mod.log("reverse can't be heard: " + t);
                    }
                }
            });
            final Class<?> listener = app.classLoader.loadClass("com.hkmc.pgs.AvmStatusListener");
            Hooks.findAndHookMethod(app.classLoader.loadClass("com.hkmc.pgs.AvmManager"), "addListener", listener, new MethodHook() {
                private final java.util.HashSet<Class<?>> heard = new java.util.HashSet<Class<?>>();

                @Override
                protected void beforeHookedMethod(Param p) {
                    Object l = p.args[0];
                    if (l == null) return;
                    synchronized (heard) {
                        if (!heard.add(l.getClass())) return;
                    }
                    listen(mod, l.getClass(), listener, over);
                }
            });
        } catch (Throwable t) {
            mod.log("the surround view's car is left as it is: " + t);
            return;
        }
        over.warm(10000);
    }

    /**
     * Hooks the stock screen's SurfaceHolder callbacks for its video surface. surfaceDestroyed means
     * the screen is about to close the camera, which blocks the main thread for a while;
     * surfaceCreated means the video is back.
     */
    private static void surface(ModContext mod, Class<?> screen, final Around over) {
        synchronized (SURFACES) {
            if (!SURFACES.add(screen)) return;
        }
        try {
            Hooks.findAndHookMethod(screen, "surfaceDestroyed", android.view.SurfaceHolder.class, new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    over.closing();
                }
            });
            Hooks.findAndHookMethod(screen, "surfaceCreated", android.view.SurfaceHolder.class, new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    over.opening();
                }
            });
            mod.log("listening for the video surface with " + screen.getName());
        } catch (Throwable t) {
            mod.log("the video surface can't be heard, the overlay goes by the camera unit's reports alone: " + t);
        }
    }

    private static final java.util.HashSet<Class<?>> SURFACES = new java.util.HashSet<Class<?>>();

    /** Returns the vehicle signal listener (com.hkmc.micom.SystemListener) the screen holds in one of its fields, or null. */
    private static Object signals(LoadedApp app, Object screen) {
        try {
            Class<?> base = app.classLoader.loadClass("com.hkmc.micom.SystemListener");
            for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !base.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object value = f.get(screen);
                    if (value != null) return value;
                }
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    /** Hooks the camera unit's status callbacks on this listener class, or on the base class where it doesn't override them. */
    private static void listen(ModContext mod, Class<?> own, Class<?> base, final Around over) {
        // A new layout is handled before the stock screen processes it. "Off" is handled after, because
        // that is when the stock screen closes its video. (In layer mode the overlay is already gone
        // by then, see Around.closing.)
        MethodHook mode = new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                int m = ((Integer) p.args[0]).intValue();
                if (m != AroundPlan.OFF) over.status(m);
            }

            @Override
            protected void afterHookedMethod(Param p) {
                int m = ((Integer) p.args[0]).intValue();
                if (m == AroundPlan.OFF) over.status(m);
            }
        };
        MethodHook zoom = new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                over.zoom(((Integer) p.args[0]).intValue());
            }
        };
        try {
            hook(own, base, "onAvmStatus", mode, int.class, int.class, int.class, int.class);
            hook(own, base, "onZoomBtnState", zoom, int.class);
            mod.log("listening to the camera unit with " + own.getName());
        } catch (Throwable t) {
            mod.log("the camera unit's reports can't be heard: " + t);
        }
    }

    private static void hook(Class<?> own, Class<?> base, String name, MethodHook hook, Class<?>... arguments) throws Throwable {
        Class<?> where = base;
        for (Class<?> c = own; c != null && c != base; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod(name, arguments);
                where = c;
                break;
            } catch (NoSuchMethodException e) {
                // not declared here, try the superclass
            }
        }
        Object[] all = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, all, 0, arguments.length);
        all[arguments.length] = hook;
        Hooks.findAndHookMethod(where, name, all);
    }

    /** Logs one line each time the given activity is created (onCreate with this argument type, or with none). */
    private static void watch(final ModContext mod, LoadedApp app, String className, Class<?> argument, final String line) {
        MethodHook hook = new MethodHook() {
            @Override
            protected void afterHookedMethod(Param p) {
                if (mod.enabled()) mod.log(line);
            }
        };
        try {
            if (argument == null) Hooks.findAndHookMethod(className, app.classLoader, "onCreate", hook);
            else Hooks.findAndHookMethod(className, app.classLoader, "onCreate", argument, hook);
        } catch (Throwable t) {
            mod.log(className + " isn't watched: " + t);
        }
    }
}
