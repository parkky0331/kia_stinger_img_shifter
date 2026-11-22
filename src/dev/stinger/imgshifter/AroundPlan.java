package dev.stinger.imgshifter;

/**
 * Android-free part of the surround-view overlay: where the car is on screen and when it is
 * shown. The stock car is drawn into the video by the camera unit, not by the head unit, so it
 * can't be recoloured. Around covers it with our own car instead. The camera unit draws its car
 * on a dark grey bed with a soft edge, and the overlay is the same bed again with our car on it:
 * same size, same grey, so it hides nothing of the live picture that the stock bed doesn't hide.
 *
 * The coordinates were measured from photos of a Stinger's screen, rectified to the known screen
 * size and checked against an overlay at a known position, so they are good to a few pixels. The
 * grey comes from the same photos. The top view sits in the same place in every layout that has
 * it, rear or front, so the overlay stays up between them and the car doesn't change colour when
 * reverse is released.
 *
 * In the two layouts that look down at the car, the camera unit draws its car a second time,
 * large, in the left-hand picture: the rear end hanging from the top edge (view behind the car)
 * or the front rising from the bottom edge (view ahead). Those get covered too, each with its own
 * bed, otherwise the same car would show in two colours.
 */
final class AroundPlan {
    private AroundPlan() {}

    /** Grey of the bed: about 57 of 255 in the photos, slightly warm */
    static final int BED_COLOUR = 0x393937;

    /** Box of the overlay on the 1920 x 720 screen: the bed including its soft edge, with the car on it */
    static final int X = 1519, Y = 147, W = 171, H = 423;
    /**
     * Solid part of the bed. The stock bed's edge is soft over about ten pixels and this is the
     * inner end of that. From here our bed fades out over FADE pixels, ending inside the stock edge.
     */
    static final int BED_X = 1530, BED_Y = 153, BED_W = 149, BED_H = 411, CORNER = 23, FADE = 6;
    /** Position of our car (TopCar.W x TopCar.H). It spans the stock car from nose to tail and mirror to mirror. */
    static final int CAR_X = 1523, CAR_Y = 152;
    /**
     * The stock car's mirrors sit further forward than ours and their tips stick out past the solid
     * part of the bed. A small extra patch of bed on each side covers them.
     */
    static final int EAR_LEFT = 1522, EAR_RIGHT = 1677, EAR_Y = 298, EAR_W = 10, EAR_H = 17, EAR_CORNER = 4, EAR_FADE = 3;

    /** Horizontal extent of the large car's box in the left-hand picture (TopCar.TAIL_W and NOSE_W wide) */
    static final int LARGE_X = 544, LARGE_W = 340;
    /** Solid part of its bed, and how it fades out */
    static final int LARGE_BED_X = 559, LARGE_BED_W = 310, LARGE_CORNER = 46, LARGE_FADE = 11;
    /** View behind the car: the bed runs from the top edge of the screen down to TAIL_BED_END, and the car's tail ends at TAIL_END */
    static final int TAIL_BED_END = 296, TAIL_END = 284;
    /** View ahead of the car: the bed runs from NOSE_BED_TOP down to the bottom edge, and the car's nose starts at NOSE_TOP */
    static final int NOSE_BED_TOP = 414, NOSE_TOP = 423;
    static final int SCREEN_H = 720;
    /** View modes reported by the camera unit (com.hkmc.pgs.AvmConstants.WideViewMode) */
    static final int OFF = 1, REAR_VIEW = 2, FRONT_VIEW = 3, REAR_AROUND_VIEW = 16, FRONT_AROUND_VIEW = 17, REAR_SIDE_VIEW = 20, FRONT_SIDE_VIEW = 21;
    /** State of the zoom buttons: both enabled, or one of them at its limit */
    static final int ZOOM_BOTH = 0, ZOOM_NO_PLUS = 1, ZOOM_NO_MINUS = 2;
    /**
     * Zoomed out, the top view shows more ground and its car gets smaller. The exact amount hasn't
     * been measured yet, so for now it is 5 % per step around the car's centre.
     */
    static final int ZOOM_STEP_PERCENT = 5;

    /**
     * Fade-in time of the overlay in milliseconds, so the stock car blends into ours instead of
     * being swapped in one frame.
     */
    static final int DISSOLVE_MS = 500;

    /** How far the top view is zoomed out: 0 = default, 1 and 2 = steps of the minus button, -1 = not reported yet or unknown */
    static int zoomStep(int zoom) {
        return zoom == ZOOM_NO_PLUS ? 0 : zoom == ZOOM_BOTH ? 1 : zoom == ZOOM_NO_MINUS ? 2 : -1;
    }

    /** Whether the current layout has the top view, at a known zoom level */
    static boolean shows(int viewMode, int zoom) {
        if (zoomStep(zoom) < 0) return false;
        return viewMode == REAR_VIEW || viewMode == REAR_AROUND_VIEW || viewMode == REAR_SIDE_VIEW || viewMode == FRONT_VIEW || viewMode == FRONT_AROUND_VIEW
                || viewMode == FRONT_SIDE_VIEW;
    }

    /** Whether the large car's rear end is on screen as well: the view down behind the car (measured at default zoom only) */
    static boolean tails(int viewMode, int zoom) {
        return viewMode == REAR_AROUND_VIEW && zoom == ZOOM_NO_PLUS;
    }

    /** Whether the large car's front is on screen as well: the view down ahead of the car */
    static boolean noses(int viewMode, int zoom) {
        return viewMode == FRONT_AROUND_VIEW && zoom == ZOOM_NO_PLUS;
    }

    static boolean rear(int viewMode) {
        return viewMode == REAR_VIEW || viewMode == REAR_AROUND_VIEW || viewMode == REAR_SIDE_VIEW;
    }

    static boolean front(int viewMode) {
        return viewMode == FRONT_VIEW || viewMode == FRONT_AROUND_VIEW || viewMode == FRONT_SIDE_VIEW;
    }

    /**
     * Layout the camera unit is about to switch to after a reverse gear change, as far as we can
     * tell before it reports. The video changes with the gear, but the report reaches the app 0.3
     * to 0.6 s after reverse is released and up to 0.25 s around engaging it (measured on the
     * car). Released in a rear layout: the front layout shown last (lastFront, -1 if none yet).
     * Engaged from a front layout or from nothing: the rear view. Returns -1 if there is nothing
     * to predict.
     */
    static int expected(int viewMode, boolean engaged, int lastFront) {
        if (engaged) return front(viewMode) || viewMode == OFF || viewMode < 0 ? REAR_VIEW : -1;
        return rear(viewMode) && front(lastFront) ? lastFront : -1;
    }

    /**
     * Whether a reverse gear change removes this layout's large car from the video right away,
     * before the camera unit reports the next view: reverse released in the view behind the car,
     * or engaged in the view ahead.
     */
    static boolean leaves(int viewMode, int zoom, boolean engaged) {
        return engaged ? noses(viewMode, zoom) : tails(viewMode, zoom);
    }

    /**
     * Box of the overlay with the top view zoomed out by the given number of steps (0 = default):
     * shrunk by ZOOM_STEP_PERCENT per step around its centre. Returns {left, top, width, height}.
     */
    static int[] place(int step) {
        int percent = 100 - ZOOM_STEP_PERCENT * Math.max(0, step);
        int w = Math.round(W * percent / 100f), h = Math.round(H * percent / 100f);
        return new int[] { X + (W - w) / 2, Y + (H - h) / 2, w, h };
    }

    /** Box of the large rear end's overlay: from the top edge of the screen down to where its bed has faded out */
    static int[] tailPlace() {
        return new int[] { LARGE_X, 0, LARGE_W, TAIL_BED_END + LARGE_FADE };
    }

    /** Box of the large front's overlay: from where its bed starts to fade in down to the bottom edge of the screen */
    static int[] nosePlace() {
        int top = NOSE_BED_TOP - LARGE_FADE;
        return new int[] { LARGE_X, top, LARGE_W, SCREEN_H - top };
    }

    /** Opacity of the top view's bed at a point of the overlay box, 0 to 255: the bed itself plus the two mirror patches */
    static int bedAt(float x, float y) {
        int a = bed(x - (BED_X - X), y - (BED_Y - Y), BED_W, BED_H, CORNER, FADE);
        a = Math.max(a, bed(x - (EAR_LEFT - X), y - (EAR_Y - Y), EAR_W, EAR_H, EAR_CORNER, EAR_FADE));
        return Math.max(a, bed(x - (EAR_RIGHT - X), y - (EAR_Y - Y), EAR_W, EAR_H, EAR_CORNER, EAR_FADE));
    }

    /**
     * Same for the bed of a large car, at a point of its box (boxH high). The bed either hangs
     * from the top edge of the screen (rear end) or runs to the bottom edge (front). At that end
     * it is extended past the box by more than its corner radius, so only the corners at the
     * other end are visible.
     */
    static int largeBedAt(float x, float y, int boxH, boolean fromTop) {
        int off = LARGE_CORNER + LARGE_FADE;
        int from = fromTop ? -off : LARGE_FADE, to = fromTop ? boxH - LARGE_FADE : boxH + off;
        return bed(x - (LARGE_BED_X - LARGE_X), y - from, LARGE_BED_W, to - from, LARGE_CORNER, LARGE_FADE);
    }

    /**
     * Opacity of a bed at a point, 0 (clear) to 255. The bed is a w x h rounded rectangle with its
     * top-left corner at 0,0. It is solid inside and fades out smoothly over fade pixels past its
     * edge.
     */
    static int bed(float x, float y, int w, int h, int corner, int fade) {
        float qx = Math.abs(x - w / 2f) - (w / 2f - corner), qy = Math.abs(y - h / 2f) - (h / 2f - corner);
        float beyond = (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - corner;
        if (beyond <= 0) return 255;
        if (beyond >= fade) return 0;
        float t = 1 - beyond / fade;
        return Math.round(255 * t * t * (3 - 2 * t));
    }

    static String mode(int viewMode) {
        switch (viewMode) {
            case OFF:
                return "off";
            case REAR_VIEW:
                return "rear view";
            case FRONT_VIEW:
                return "front view";
            case 4:
                return "rear left view";
            case 5:
                return "front left view";
            case 6:
                return "rear right view";
            case 7:
                return "front right view";
            case 16:
                return "rear around view";
            case 17:
                return "front around view";
            case 20:
                return "rear side view";
            case 21:
                return "front side view";
            case 22:
            case 23:
                return "driving rear view";
            case 28:
                return "display setting";
            case 30:
                return "view ready";
            default:
                return "view " + viewMode;
        }
    }
}
