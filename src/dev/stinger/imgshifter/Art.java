package dev.stinger.imgshifter;

/**
 * Android-free logic: which stock picture a drawable name stands for, which drive mode it shows,
 * and how the drive mode car drawing is tinted. (Body colour for the camera app is in BodyPaint.)
 * The tint colours were sampled from the older drive mode pictures still shipped in the stock app
 * (set_etc_mode_&lt;mode&gt;.png), which use one car drawing per mode.
 */
final class Art {
    private Art() {}

    static final int ECO = 0, COMFORT = 1, SMART = 2, SPORT = 3, SPORT_PLUS = 4, CUSTOM = 5;
    static final String[] MODES = { "eco", "comfort", "smart", "sport", "sportplus", "custom" };
    /** Floor picture of each mode in the vehicle settings app: set_etc_drive_bg_&lt;name&gt; */
    static final String[] FLOORS = { "eco", "comfort", "smart", "sport", "sport_plus", "custom" };
    /** Tint colour of the car drawing for each mode (RGB) */
    static final int[][] TONES = { { 30, 255, 231 }, { 253, 254, 255 }, { 85, 197, 255 }, { 255, 46, 40 }, { 255, 46, 40 },
            { 214, 114, 255 } };

    /** Sizes of the floor picture and the car drawing. The drawing sits CAR_Y below the floor's top edge. */
    static final int BASE_W = 649, BASE_H = 340, CAR_W = 649, CAR_H = 302, CAR_Y = 19;
    /** The part of that picture that isn't empty margin. Same size as the right-hand panel's picture. */
    static final int INNER_X = 24, INNER_Y = 5, INNER_W = 600, INNER_H = 330;

    // Kinds of stock pictures we replace:
    //   SPLIT  right-hand panel and its pop-up (split_bg_mode_<mode>_..., 600 x 330)
    //   POPUP  mode-change pop-up of the settings app (set_etc_mode_<mode>_..., 1260 x 531)
    //   GUIDE  help picture of the settings app: a small drawing of the screen with the car in it
    //          (usm_dm_008_01_n_..._<mode>, 640 x 540)
    //   CAR    the camera app's top-down car (cam_pdw_car_<model>, 219 x 459 for the Stinger).
    //          Not a drive mode picture; it gets the body colour.
    //   SIGN   small camera app icons that contain a silver top-down car and also get the body
    //          colour: door icons (cam_ico_door_<nn>, 96 x 128), view buttons (cam_ico_front_... and
    //          cam_ico_rear_..., 69 x 87; normal and selected states, disabled ones are left alone)
    //          and the parking view's car (cam_ico_assist). Buttons are set as state lists
    //          (..._selector) of these icons.
    static final int NONE = 0, SPLIT = 1, POPUP = 2, GUIDE = 3, CAR = 4, SIGN = 5;
    static final String[] FAMILIES = { "-", "split", "popup", "guide", "car", "sign" };
    /** The camera app's picture of the Stinger */
    static final String CAR_NAME = "cam_pdw_car_ckpe";
    /** Icons the camera app sets by name from code. These are built ahead of first use. */
    static final String[] SIGNS_AHEAD = signsAhead();

    private static final String SPLIT_NAME = "split_bg_mode_", POPUP_NAME = "set_etc_mode_";
    private static final String[] GUIDE_NAMES = { "usm_dm_008_01_n_", "usm_ev_dm_008_01_n_" };

    static int family(String name) {
        if (name == null) return NONE;
        if (name.startsWith(SPLIT_NAME)) return SPLIT;
        if (name.startsWith(POPUP_NAME)) return POPUP;
        for (String g : GUIDE_NAMES) if (name.startsWith(g)) return GUIDE;
        if (name.equals(CAR_NAME)) return CAR;
        if (sign(name)) return SIGN;
        return NONE;
    }

    private static final String DOOR_NAME = "cam_ico_door_", ASSIST_NAME = "cam_ico_assist";
    private static final String[] VIEW_NAMES = { "cam_ico_front_", "cam_ico_rear_" };

    private static boolean sign(String name) {
        if (name.equals(ASSIST_NAME)) return true;
        if (name.startsWith(DOOR_NAME)) {
            // cam_ico_door_<nn> is the saloon; cam_ico_door_<nn>_suv belongs to another body type
            for (int i = DOOR_NAME.length(); i < name.length(); i++) if (!Character.isDigit(name.charAt(i))) return false;
            return name.length() > DOOR_NAME.length();
        }
        for (String v : VIEW_NAMES) {
            if (!name.startsWith(v) || name.contains("trailer")) continue;
            return name.endsWith("_n") || name.endsWith("_s") || name.endsWith("_selector");
        }
        return false;
    }

    private static String[] signsAhead() {
        String[] views = { "front_01_selector", "front_02_selector", "front_03_selector", "front_wide_svm_selector",
                "front_leftcorner_selector", "front_rightcorner_selector", "front_surround_selector", "front_l_03_n", "front_r_03_n",
                "front_sur_n", "rear_01_selector", "rear_02_selector", "rear_03_selector", "rear_wide_svm_selector",
                "rear_ass_selector", "rear_ass_top_view_selector", "rear_leftcorner_selector", "rear_rightcorner_selector",
                "rear_surround_selector", "rear_l_03_n", "rear_r_03_n", "rear_sur_n" };
        String[] out = new String[15 + 1 + views.length];
        int n = 0;
        for (int door = 1; door <= 15; door++) out[n++] = DOOR_NAME + (door < 10 ? "0" : "") + door;
        out[n++] = ASSIST_NAME;
        for (String v : views) out[n++] = "cam_ico_" + v;
        return out;
    }

    /** Drive mode shown by a stock picture (ECO .. CUSTOM), or -1 if we have no picture for it (snow, terrain, N mode ...) */
    static int mode(String name) {
        switch (family(name)) {
            case SPLIT:
                return first(name.substring(SPLIT_NAME.length()));
            case POPUP:
                return first(name.substring(POPUP_NAME.length()));
            case GUIDE: {
                String rest = null;
                for (String g : GUIDE_NAMES) if (name.startsWith(g)) rest = name.substring(g.length());
                // <body>_<mode>, with "ce_" before the body on some cars. Anything longer is a different picture (N mode).
                if (rest.startsWith("ce_")) rest = rest.substring(3);
                int cut = rest.indexOf('_');
                if (cut <= 0 || rest.indexOf('_', cut + 1) >= 0) return -1;
                String word = rest.substring(cut + 1);
                // the stock code falls back from "normal" to "comfort" on cars without a "normal" picture
                return "normal".equals(word) ? COMFORT : word(word);
            }
            default:
                return -1;
        }
    }

    private static int first(String rest) {
        int cut = rest.indexOf('_');
        return word(cut < 0 ? rest : rest.substring(0, cut));
    }

    /** Stock names aren't spelled consistently: "sports", "sportsplus" and "comport" all occur in the stock app. */
    private static int word(String w) {
        if (w.equals("eco")) return ECO;
        if (w.equals("comfort") || w.equals("comport")) return COMFORT;
        if (w.equals("smart")) return SMART;
        if (w.equals("sport") || w.equals("sports")) return SPORT;
        if (w.equals("sportplus") || w.equals("sportsplus")) return SPORT_PLUS;
        if (w.equals("custom")) return CUSTOM;
        return -1;
    }

    /** Modes that produce the same picture share one (sport and sport plus: same floor file, same colour). */
    static int twin(int mode) {
        return mode == SPORT_PLUS ? SPORT : mode;
    }

    /**
     * Tints the car drawing (grey on transparent, plain ARGB). Each pixel's brightness is multiplied
     * by the mode colour, keeping a bit of white in the brightest parts so the metal still looks
     * like metal.
     */
    static void tint(int[] px, int[] tone) {
        float tr = tone[0] / 255f, tg = tone[1] / 255f, tb = tone[2] / 255f;
        for (int i = 0; i < px.length; i++) {
            int p = px[i], a = p >>> 24;
            if (a == 0) {
                px[i] = 0;
                continue;
            }
            float l = (0.30f * ((p >> 16) & 255) + 0.59f * ((p >> 8) & 255) + 0.11f * (p & 255)) / 255f;
            float white = l * l * 0.35f, k = l * (1 - white);
            int r = Math.min(255, Math.round(255 * (k * tr + white)));
            int g = Math.min(255, Math.round(255 * (k * tg + white)));
            int b = Math.min(255, Math.round(255 * (k * tb + white)));
            px[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}
