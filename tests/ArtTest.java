package dev.stinger.imgshifter;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Offline tests for everything that doesn't need Android: stock drawable names (which picture,
 * which mode) and the tinting. Given a folder with the stock source pictures as its argument
 * (never kept in this repository), it also tints the real car drawing and compares each mode's
 * floor-and-car picture with a reference (rebuild/rebuild_&lt;mode&gt;.png in that folder).
 */
public final class ArtTest {
    static int checks, failures;

    static void same(String what, int want, int have) {
        checks++;
        if (want != have) {
            failures++;
            System.out.println("FAIL " + what + ": want " + want + ", have " + have);
        }
    }

    static void yes(String what, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    static void name(String name, int family, int mode) {
        same("family of " + name, family, Art.family(name));
        same("mode of " + name, mode, Art.mode(name));
    }

    public static void main(String[] a) throws Exception {
        // right-hand panel: names as the vehicle settings app sends them, odd spellings included
        name("split_bg_mode_sport_n_sedan", Art.SPLIT, Art.SPORT);
        name("split_bg_mode_sport_sedan", Art.SPLIT, Art.SPORT);
        name("split_bg_mode_sports_n_sedan_ev", Art.SPLIT, Art.SPORT);
        name("split_bg_mode_sports_sedan", Art.SPLIT, Art.SPORT);
        name("split_bg_mode_sportplus_n_sedan", Art.SPLIT, Art.SPORT_PLUS);
        name("split_bg_mode_sportsplus_n_sedan_ev", Art.SPLIT, Art.SPORT_PLUS);
        name("split_bg_mode_eco_n_sedan", Art.SPLIT, Art.ECO);
        name("split_bg_mode_comfort_n_sedan", Art.SPLIT, Art.COMFORT);
        name("split_bg_mode_comport_sedan", Art.SPLIT, Art.COMFORT);
        name("split_bg_mode_smart_sedan_ev", Art.SPLIT, Art.SMART);
        name("split_bg_mode_custom_n_suv", Art.SPLIT, Art.CUSTOM);
        name("split_bg_mode_snow_sedan", Art.SPLIT, -1);
        name("split_bg_mode_nmode_n_sedan", Art.SPLIT, -1);
        name("split_bg_mode_terrain_mud", Art.SPLIT, -1);
        name("split_bg_trmode_snow_sedan", Art.NONE, -1);
        name("split_bg_gtmode_suv", Art.NONE, -1);
        name("split_bg_sports", Art.NONE, -1);
        // settings pop-up
        name("set_etc_mode_sport_sedan", Art.POPUP, Art.SPORT);
        name("set_etc_mode_sport_ce_sedan", Art.POPUP, Art.SPORT);
        name("set_etc_mode_sport_sedan_ikpe2", Art.POPUP, Art.SPORT); // a small label, skipped by the size check
        name("set_etc_mode_sportplus_sedan_ev", Art.POPUP, Art.SPORT_PLUS);
        name("set_etc_mode_sport", Art.POPUP, Art.SPORT);
        name("set_etc_mode_eco_wagon", Art.POPUP, Art.ECO);
        name("set_etc_mode_comfort_sedan", Art.POPUP, Art.COMFORT);
        name("set_etc_mode_smart_sedan", Art.POPUP, Art.SMART);
        name("set_etc_mode_custom_sedan", Art.POPUP, Art.CUSTOM);
        name("set_etc_mode_deepsnow_sedan", Art.POPUP, -1);
        name("set_etc_mode_nmode_nstandard_sedan", Art.POPUP, -1);
        name("set_etc_mode_title_nmode", Art.POPUP, -1);
        name("set_etc_drive_bg_sport", Art.NONE, -1);
        name("set_etc_drivemode_comfort_ck", Art.NONE, -1);
        // help picture: only the newer screen's drawing, and not N mode's
        name("usm_dm_008_01_n_sedan_sport", Art.GUIDE, Art.SPORT);
        name("usm_dm_008_01_n_sedan_sportplus", Art.GUIDE, Art.SPORT_PLUS);
        name("usm_dm_008_01_n_sedan_eco", Art.GUIDE, Art.ECO);
        name("usm_dm_008_01_n_sedan_comfort", Art.GUIDE, Art.COMFORT);
        name("usm_dm_008_01_n_sedan_normal", Art.GUIDE, Art.COMFORT);
        name("usm_dm_008_01_n_sedan_smart", Art.GUIDE, Art.SMART);
        name("usm_dm_008_01_n_sedan_custom", Art.GUIDE, Art.CUSTOM);
        name("usm_ev_dm_008_01_n_ce_sedan_sport", Art.GUIDE, Art.SPORT);
        name("usm_ev_dm_008_01_n_sedan_eco", Art.GUIDE, Art.ECO);
        name("usm_dm_008_01_n_sedan_auto", Art.GUIDE, -1);
        name("usm_dm_008_01_n_sedan_snow", Art.GUIDE, -1);
        name("usm_dm_008_01_n_sedan_nmode_custom", Art.GUIDE, -1);
        name("usm_dm_008_01_n_sedan_nmode_nstandard", Art.GUIDE, -1);
        name("usm_dm_008_01_sedan_sport", Art.NONE, -1);
        name("usm_dm_008_03_n_sedan", Art.NONE, -1);
        name("usm_ev_dm_008_01_gt_suv_standard", Art.NONE, -1);
        // the camera app's car picture: Stinger only
        name("cam_pdw_car_ckpe", Art.CAR, -1);
        name("cam_pdw_car_sedan", Art.NONE, -1);
        name("cam_pdw_car", Art.NONE, -1);
        name(null, Art.NONE, -1);
        name("", Art.NONE, -1);
        same("twin of sport plus", Art.SPORT, Art.twin(Art.SPORT_PLUS));
        same("twin of eco", Art.ECO, Art.twin(Art.ECO));
        yes("a floor and a colour for every mode", Art.FLOORS.length == Art.MODES.length && Art.TONES.length == Art.MODES.length);

        // tinting: transparent stays transparent, black stays black, grey takes the colour,
        // white keeps some white, alpha is preserved
        int[] px = { 0x00ffffff, 0xff000000, 0xff808080, 0xffffffff, 0x80808080 };
        Art.tint(px, new int[] { 255, 46, 40 });
        same("transparent", 0, px[0]);
        same("black", 0xff000000, px[1]);
        // grey 128 in sport red: brightness 0.502, white 0.088 -> 139 / 43.5 / 41
        same("grey red", 139, (px[2] >> 16) & 255);
        yes("grey green", Math.abs(((px[2] >> 8) & 255) - 43) <= 1);
        same("grey blue", 41, px[2] & 255);
        // white: 65 % colour + 35 % white -> 255 / 119 / 115
        same("white red", 255, (px[3] >> 16) & 255);
        same("white green", 119, (px[3] >> 8) & 255);
        same("white blue", 115, px[3] & 255);
        same("alpha kept", 0x80, px[4] >>> 24);
        same("alpha does not change the colour", px[2] & 0xffffff, px[4] & 0xffffff);
        int[] white = { 0xffffffff };
        Art.tint(white, new int[] { 253, 254, 255 });
        yes("comfort white", (white[0] & 0xff) == 255 && ((white[0] >> 16) & 255) >= 252);

        resize();
        paint();
        signs();
        around();
        if (a.length > 0) pieces(new File(a[0]));
        System.out.println((failures == 0 ? "ok: " : "FAILED: ") + checks + " checks" + (failures == 0 ? "" : ", " + failures + " failed")
                + (a.length > 0 ? " (with the stock pieces)" : ""));
        if (failures != 0) System.exit(1);
    }

    /** Body colour: only the paint changes, and "stock" or an unknown name means no colour */
    static void paint() {
        yes("a colour and a finish for every name", BodyPaint.RGB.length == BodyPaint.KEYS.length && BodyPaint.MATT.length == BodyPaint.KEYS.length);
        same("stock is no colour", -1, BodyPaint.of("stock"));
        same("an unknown name is no colour", -1, BodyPaint.of("purple"));
        same("no name is no colour", -1, BodyPaint.of(null));
        same("hichroma_red", 4, BodyPaint.of("hichroma_red"));
        // transparent, shadow black, dark glass, tail lamp red, flat-panel silver, a glint
        int[] px = { 0x00000000, 0x80000000, 0xff1c1e22, 0xffb01825, 0xffbcbdc2, 0xfff4f5f8, 0x80bcbdc2 };
        int[] red = px.clone();
        BodyPaint.paint(red, BodyPaint.of("hichroma_red"));
        yes("nothing, the shadow, glass and a lamp stay as they are", red[0] == px[0] && red[1] == px[1] && red[2] == px[2] && red[3] == px[3]);
        int r = (red[4] >> 16) & 255, g = (red[4] >> 8) & 255, b = red[4] & 255;
        yes("silver becomes the red: " + r + "/" + g + "/" + b, r > 150 && g < 60 && b < 70);
        yes("a glint stays a glint", ((red[5] >> 8) & 255) > ((red[4] >> 8) & 255) + 40);
        yes("alpha is kept and does not change the colour", (red[6] >>> 24) == 0x80 && (red[6] & 0xffffff) == (red[4] & 0xffffff));
        int[] none = px.clone();
        BodyPaint.paint(none, -1);
        BodyPaint.paint(none, BodyPaint.KEYS.length);
        yes("no colour changes nothing", java.util.Arrays.equals(none, px));
        int[] green = px.clone();
        BodyPaint.paint(green, BodyPaint.of("ascot_green"));
        r = (green[4] >> 16) & 255;
        g = (green[4] >> 8) & 255;
        b = green[4] & 255;
        yes("Ascot Green is a dark teal: " + r + "/" + g + "/" + b, g > r && g - r < 40 && Math.abs(g - b) < 16 && g < 110);
    }

    /**
     * The camera app's small icons with a car in them: which names count, and that only the car
     * gets repainted. The test icon is a top-down car made of bonnet, roof and boot (silver,
     * separated by glass) with mirrors, an open door in red, a translucent fan with a one-pixel
     * outline in front of it, and a solid shape wider than the car underneath.
     */
    static void signs() {
        for (String name : new String[] { "cam_ico_door_01", "cam_ico_door_15", "cam_ico_assist", "cam_ico_front_01_n", "cam_ico_front_01_s",
                "cam_ico_rear_ass_top_n", "cam_ico_front_l_03_n", "cam_ico_front_01_selector", "cam_ico_rear_wide_svm_selector" }) {
            name(name, Art.SIGN, -1);
        }
        // another body type's door icon, a disabled button, the plain round button, towing, the turning view, other icons
        for (String name : new String[] { "cam_ico_door_01_suv", "cam_ico_door_", "cam_ico_front_01_d", "cam_ico_front_01", "cam_ico_rear_trailer_selector",
                "cam_ico_trailer_view_n", "cam_ico_3d_view_n", "cam_ico_assist_bus", "cam_ico_set_n", "cam_pdw_car_sedan" }) {
            name(name, Art.NONE, -1);
        }
        yes("the signs made ahead are signs, each once", aheadOk());

        int w = 48, h = 70, silver = 0xffbcbdc2, glass = 0xff1c1e22, red = 0xffe01018;
        int[] px = new int[w * h];
        fill(px, w, 18, 6, 30, 20, silver);     // bonnet
        fill(px, w, 18, 20, 30, 26, glass);
        fill(px, w, 18, 26, 30, 40, silver);    // roof
        fill(px, w, 18, 40, 30, 46, glass);
        fill(px, w, 18, 46, 30, 58, silver);    // boot
        fill(px, w, 15, 22, 18, 25, silver);    // mirrors
        fill(px, w, 30, 22, 33, 25, silver);
        fill(px, w, 8, 28, 18, 31, red);        // an open door
        fill(px, w, 6, 0, 42, 6, 0x60d0d0d0);   // the fan, see-through
        fill(px, w, 6, 0, 42, 1, 0xffd0d0d0);   // its outline
        fill(px, w, 6, 0, 7, 6, 0xffd0d0d0);
        fill(px, w, 41, 0, 42, 6, 0xffd0d0d0);
        fill(px, w, 4, 62, 44, 68, 0xffc8c8c8); // a shape under the car, wider than it
        int[] out = px.clone();
        BodyPaint.paintCar(out, w, BodyPaint.of("hichroma_red"));
        yes("the bonnet, the roof and the boot are red", reddish(out[12 * w + 24]) && reddish(out[33 * w + 24]) && reddish(out[52 * w + 24]));
        yes("so are the panels' edges and the mirrors", reddish(out[6 * w + 18]) && reddish(out[57 * w + 29]) && reddish(out[23 * w + 16]) && reddish(out[23 * w + 31]));
        yes("glass and the open door stay", out[23 * w + 24] == glass && out[43 * w + 24] == glass && out[29 * w + 10] == red);
        yes("the fan and its outline stay", out[3 * w + 24] == px[3 * w + 24] && out[24] == px[24] && out[3 * w + 6] == px[3 * w + 6]);
        yes("the shape under the car stays", out[65 * w + 24] == px[65 * w + 24] && out[65 * w + 6] == px[65 * w + 6]);
        int changed = 0;
        for (int i = 0; i < px.length; i++) if (out[i] != px[i]) changed++;
        same("nothing but the car's silver changed", 12 * 14 + 12 * 14 + 12 * 12 + 2 * 9, changed);
        int[] none = px.clone();
        BodyPaint.paintCar(none, w, -1);
        BodyPaint.paintCar(none, 0, 4);
        BodyPaint.paintCar(none, 11, 4);
        yes("no colour, or a width that is not the picture's, changes nothing", java.util.Arrays.equals(none, px));
        int[] empty = new int[w * h];
        BodyPaint.paintCar(empty, w, 4);
        yes("an empty picture stays empty", java.util.Arrays.equals(empty, new int[w * h]));
    }

    /** Surround-view overlay: the layouts it shows in, its box, and the embedded picture */
    static void around() throws Exception {
        yes("the three rear layouts, at the measured zoom", AroundPlan.shows(AroundPlan.REAR_VIEW, AroundPlan.ZOOM_NO_PLUS)
                && AroundPlan.shows(AroundPlan.REAR_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS) && AroundPlan.shows(AroundPlan.REAR_SIDE_VIEW, AroundPlan.ZOOM_NO_PLUS));
        yes("and the three front layouts, going forward", AroundPlan.shows(AroundPlan.FRONT_VIEW, AroundPlan.ZOOM_NO_PLUS)
                && AroundPlan.shows(AroundPlan.FRONT_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS) && AroundPlan.shows(AroundPlan.FRONT_SIDE_VIEW, AroundPlan.ZOOM_NO_PLUS));
        yes("zoomed out a step and two, the same layouts", AroundPlan.shows(AroundPlan.REAR_VIEW, AroundPlan.ZOOM_BOTH)
                && AroundPlan.shows(AroundPlan.REAR_VIEW, AroundPlan.ZOOM_NO_MINUS) && AroundPlan.shows(AroundPlan.FRONT_SIDE_VIEW, AroundPlan.ZOOM_BOTH)
                && AroundPlan.shows(AroundPlan.REAR_AROUND_VIEW, AroundPlan.ZOOM_NO_MINUS));
        yes("the zoom's steps by the buttons' state", AroundPlan.zoomStep(AroundPlan.ZOOM_NO_PLUS) == 0 && AroundPlan.zoomStep(AroundPlan.ZOOM_BOTH) == 1
                && AroundPlan.zoomStep(AroundPlan.ZOOM_NO_MINUS) == 2 && AroundPlan.zoomStep(-1) == -1 && AroundPlan.zoomStep(3) == -1);
        yes("no other view, no zoom that is not known, nothing before the camera unit has reported",
                !AroundPlan.shows(AroundPlan.OFF, AroundPlan.ZOOM_NO_PLUS) && !AroundPlan.shows(22, AroundPlan.ZOOM_NO_PLUS)
                        && !AroundPlan.shows(23, AroundPlan.ZOOM_NO_PLUS) && !AroundPlan.shows(19, AroundPlan.ZOOM_NO_PLUS)
                        && !AroundPlan.shows(24, AroundPlan.ZOOM_NO_PLUS) && !AroundPlan.shows(-1, -1) && !AroundPlan.shows(AroundPlan.REAR_VIEW, -1)
                        && !AroundPlan.shows(AroundPlan.REAR_VIEW, 3));
        // the box: bed (solid 1530,153 149x411, fading out over six pixels), mirror patches and the car
        yes("as measured", java.util.Arrays.equals(new int[] { 1519, 147, 171, 423 }, AroundPlan.place(0)));
        // zoomed out: 5 % smaller per step, around the same centre
        int[] once = AroundPlan.place(1), twice = AroundPlan.place(2);
        yes("zoomed out a step: " + java.util.Arrays.toString(once), java.util.Arrays.equals(new int[] { 1523, 157, 162, 402 }, once));
        yes("and two: " + java.util.Arrays.toString(twice), java.util.Arrays.equals(new int[] { 1527, 168, 154, 381 }, twice));
        yes("about the car's middle", Math.abs(2 * once[0] + once[2] - (2 * 1519 + 171)) <= 1 && Math.abs(2 * twice[1] + twice[3] - (2 * 147 + 423)) <= 1);
        yes("the box holds the bed with its fading edge, the pieces at the mirrors and the car",
                AroundPlan.X <= AroundPlan.BED_X - AroundPlan.FADE && AroundPlan.X + AroundPlan.W >= AroundPlan.BED_X + AroundPlan.BED_W + AroundPlan.FADE
                        && AroundPlan.Y <= AroundPlan.BED_Y - AroundPlan.FADE && AroundPlan.Y + AroundPlan.H >= AroundPlan.BED_Y + AroundPlan.BED_H + AroundPlan.FADE
                        && AroundPlan.X <= AroundPlan.EAR_LEFT - AroundPlan.EAR_FADE
                        && AroundPlan.X + AroundPlan.W >= AroundPlan.EAR_RIGHT + AroundPlan.EAR_W + AroundPlan.EAR_FADE
                        && AroundPlan.X <= AroundPlan.CAR_X && AroundPlan.X + AroundPlan.W >= AroundPlan.CAR_X + TopCar.W && AroundPlan.Y <= AroundPlan.CAR_Y);
        yes("the car ends on the solid bed, the bed's middle under its own", AroundPlan.CAR_Y + TopCar.H <= AroundPlan.BED_Y + AroundPlan.BED_H
                && 2 * AroundPlan.CAR_X + TopCar.W == 2 * AroundPlan.BED_X + AroundPlan.BED_W && 2 * AroundPlan.X + AroundPlan.W == 2 * AroundPlan.BED_X + AroundPlan.BED_W);
        int bedRed = (AroundPlan.BED_COLOUR >> 16) & 255, bedGreen = (AroundPlan.BED_COLOUR >> 8) & 255, bedBlue = AroundPlan.BED_COLOUR & 255;
        yes("the bed is a dark grey, not black", bedRed > 40 && bedRed < 75 && Math.abs(bedRed - bedGreen) <= 4 && Math.abs(bedRed - bedBlue) <= 4);
        yes("solid where the camera unit's car is", AroundPlan.bedAt(AroundPlan.W / 2f, AroundPlan.H / 2f) == 255
                && AroundPlan.bedAt(AroundPlan.BED_X - AroundPlan.X + 0.5f, 200) == 255 && AroundPlan.bedAt(AroundPlan.W / 2f, AroundPlan.BED_Y - AroundPlan.Y + 0.5f) == 255
                && AroundPlan.bedAt(AroundPlan.W / 2f, AroundPlan.BED_Y + AroundPlan.BED_H - AroundPlan.Y - 0.5f) == 255);
        yes("gone at the box's edges", AroundPlan.bedAt(0.5f, 200) == 0 && AroundPlan.bedAt(AroundPlan.W - 0.5f, 200) == 0
                && AroundPlan.bedAt(AroundPlan.W / 2f, 0.25f) < 12 && AroundPlan.bedAt(AroundPlan.W / 2f, AroundPlan.H - 0.25f) < 12);
        float earY = AroundPlan.EAR_Y - AroundPlan.Y + AroundPlan.EAR_H / 2f;
        yes("solid over the camera unit's mirrors, either side", AroundPlan.bedAt(AroundPlan.EAR_LEFT - AroundPlan.X + 1, earY) == 255
                && AroundPlan.bedAt(AroundPlan.EAR_RIGHT + AroundPlan.EAR_W - AroundPlan.X - 1, earY) == 255
                && AroundPlan.bedAt(AroundPlan.EAR_LEFT - AroundPlan.X + 1, earY - 30) < 255 && AroundPlan.bedAt(AroundPlan.EAR_LEFT - AroundPlan.X + 1, earY + 30) < 255);
        boolean mirrored = true;
        for (int y = 0; y < AroundPlan.H; y += 7) {
            for (int x = 0; x < AroundPlan.W / 2; x += 3) mirrored &= AroundPlan.bedAt(x + 0.5f, y + 0.5f) == AroundPlan.bedAt(AroundPlan.W - x - 0.5f, y + 0.5f);
        }
        yes("the same left and right", mirrored);
        yes("the bed is solid on itself", AroundPlan.bed(81, 210, 163, 420, 14, 10) == 255 && AroundPlan.bed(0.5f, 210, 163, 420, 14, 10) == 255
                && AroundPlan.bed(81, 419.5f, 163, 420, 14, 10) == 255);
        yes("clear beyond its fading edge", AroundPlan.bed(-10.5f, 210, 163, 420, 14, 10) == 0 && AroundPlan.bed(81, 431, 163, 420, 14, 10) == 0
                && AroundPlan.bed(-8, -8, 163, 420, 14, 10) == 0);
        int midway = AroundPlan.bed(-5, 210, 163, 420, 14, 10), close = AroundPlan.bed(-2, 210, 163, 420, 14, 10), out = AroundPlan.bed(-8, 210, 163, 420, 14, 10);
        yes("half way out half as black, and smoothly: " + close + "/" + midway + "/" + out, Math.abs(midway - 128) <= 2 && close > 220 && out < 35);
        int corner = AroundPlan.bed(1, 1, 163, 420, 14, 10);
        yes("its corners are round: " + corner, corner > 0 && corner < 255);
        yes("the large rear end's overlay starts at the screen's top edge", java.util.Arrays.equals(new int[] { 544, 0, 340, 307 }, AroundPlan.tailPlace()));
        yes("the large front's ends at its bottom edge", java.util.Arrays.equals(new int[] { 544, 403, 340, 317 }, AroundPlan.nosePlace()));
        int tailH = AroundPlan.tailPlace()[3], noseH = AroundPlan.nosePlace()[3];
        yes("the rear end's bed: solid from the top edge, fading out below and at the sides", AroundPlan.largeBedAt(170, 0.5f, tailH, true) == 255
                && AroundPlan.largeBedAt(170, AroundPlan.TAIL_BED_END - 0.5f, tailH, true) == 255 && AroundPlan.largeBedAt(170, tailH - 0.25f, tailH, true) < 12
                && AroundPlan.largeBedAt(0.5f, 100, tailH, true) == 0 && AroundPlan.largeBedAt(339.5f, 100, tailH, true) == 0
                && AroundPlan.largeBedAt(AroundPlan.LARGE_BED_X - AroundPlan.LARGE_X + 0.5f, 100, tailH, true) == 255);
        yes("the front's: solid to the bottom edge, fading out above and at the sides", AroundPlan.largeBedAt(170, noseH - 0.5f, noseH, false) == 255
                && AroundPlan.largeBedAt(170, AroundPlan.LARGE_FADE + 0.5f, noseH, false) == 255 && AroundPlan.largeBedAt(170, 0.25f, noseH, false) < 12
                && AroundPlan.largeBedAt(0.5f, 200, noseH, false) == 0 && AroundPlan.largeBedAt(339.5f, 200, noseH, false) == 0);
        int lower = AroundPlan.largeBedAt(AroundPlan.LARGE_BED_X - AroundPlan.LARGE_X + 3, AroundPlan.TAIL_BED_END - 3, tailH, true);
        int upper = AroundPlan.largeBedAt(AroundPlan.LARGE_BED_X - AroundPlan.LARGE_X + 3, AroundPlan.LARGE_FADE + 3, noseH, false);
        yes("their corners on the screen are round: " + lower + "/" + upper, lower < 255 && upper < 255 && lower == upper);
        int[] whole = AroundPlan.place(0);
        yes("on the screen's top-view panel", whole[0] >= 1320 && whole[0] + whole[2] <= 1920 && whole[1] >= 0 && whole[1] + whole[3] <= 720);
        yes("the modes by name", "rear view".equals(AroundPlan.mode(2)) && "front side view".equals(AroundPlan.mode(21)) && "view 99".equals(AroundPlan.mode(99)));

        yes("the large car only in the view down behind the car", AroundPlan.tails(AroundPlan.REAR_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS)
                && !AroundPlan.tails(AroundPlan.REAR_VIEW, AroundPlan.ZOOM_NO_PLUS) && !AroundPlan.tails(AroundPlan.REAR_SIDE_VIEW, AroundPlan.ZOOM_NO_PLUS)
                && !AroundPlan.tails(AroundPlan.REAR_AROUND_VIEW, AroundPlan.ZOOM_BOTH) && !AroundPlan.tails(-1, -1)
                && !AroundPlan.tails(AroundPlan.FRONT_SIDE_VIEW, AroundPlan.ZOOM_NO_PLUS) && !AroundPlan.tails(AroundPlan.FRONT_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS));
        yes("its front only in the view down ahead of the car", AroundPlan.noses(AroundPlan.FRONT_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS)
                && !AroundPlan.noses(AroundPlan.FRONT_VIEW, AroundPlan.ZOOM_NO_PLUS) && !AroundPlan.noses(AroundPlan.FRONT_SIDE_VIEW, AroundPlan.ZOOM_NO_PLUS)
                && !AroundPlan.noses(AroundPlan.FRONT_AROUND_VIEW, AroundPlan.ZOOM_BOTH) && !AroundPlan.noses(-1, -1)
                && !AroundPlan.noses(AroundPlan.REAR_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS));
        yes("and never without the small one", AroundPlan.shows(AroundPlan.REAR_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS)
                && AroundPlan.shows(AroundPlan.FRONT_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS));
        yes("reverse let go takes the rear end out of the video, reverse engaged the front",
                AroundPlan.leaves(AroundPlan.REAR_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS, false) && !AroundPlan.leaves(AroundPlan.REAR_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS, true)
                        && AroundPlan.leaves(AroundPlan.FRONT_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS, true)
                        && !AroundPlan.leaves(AroundPlan.FRONT_AROUND_VIEW, AroundPlan.ZOOM_NO_PLUS, false)
                        && !AroundPlan.leaves(AroundPlan.REAR_VIEW, AroundPlan.ZOOM_NO_PLUS, false) && !AroundPlan.leaves(AroundPlan.FRONT_SIDE_VIEW, AroundPlan.ZOOM_NO_PLUS, true));
        yes("rear and front layouts by their numbers", AroundPlan.rear(2) && AroundPlan.rear(16) && AroundPlan.rear(20) && !AroundPlan.rear(3) && !AroundPlan.rear(1)
                && AroundPlan.front(3) && AroundPlan.front(17) && AroundPlan.front(21) && !AroundPlan.front(2) && !AroundPlan.front(22) && !AroundPlan.front(-1));
        yes("reverse let go in a rear layout: the front layout shown last is expected", AroundPlan.expected(2, false, 17) == 17 && AroundPlan.expected(16, false, 21) == 21
                && AroundPlan.expected(20, false, 3) == 3);
        yes("none seen yet, or not in a rear layout: nothing to expect", AroundPlan.expected(2, false, -1) == -1 && AroundPlan.expected(17, false, 17) == -1
                && AroundPlan.expected(1, false, 17) == -1 && AroundPlan.expected(2, false, 22) == -1);
        yes("reverse engaged from a front layout or from nothing: the rear view", AroundPlan.expected(17, true, 17) == 2 && AroundPlan.expected(21, true, -1) == 2
                && AroundPlan.expected(1, true, -1) == 2 && AroundPlan.expected(-1, true, -1) == 2);
        yes("engaged in a rear layout: it is there already", AroundPlan.expected(16, true, 17) == -1 && AroundPlan.expected(2, true, 17) == -1);
        yes("the large beds lie in the left-hand picture", AroundPlan.LARGE_X >= 140 && AroundPlan.LARGE_X + AroundPlan.LARGE_W <= 1280
                && AroundPlan.LARGE_X <= AroundPlan.LARGE_BED_X - AroundPlan.LARGE_FADE
                && AroundPlan.LARGE_X + AroundPlan.LARGE_W >= AroundPlan.LARGE_BED_X + AroundPlan.LARGE_BED_W + AroundPlan.LARGE_FADE
                && 2 * AroundPlan.LARGE_X + AroundPlan.LARGE_W == 2 * AroundPlan.LARGE_BED_X + AroundPlan.LARGE_BED_W);
        yes("the large car's rear end fits its bed", TopCar.TAIL_W == AroundPlan.LARGE_W && TopCar.TAIL_H <= AroundPlan.TAIL_END
                && AroundPlan.TAIL_END < AroundPlan.TAIL_BED_END);
        yes("its front too, down to the screen's bottom edge", TopCar.NOSE_W == AroundPlan.LARGE_W && AroundPlan.NOSE_TOP > AroundPlan.NOSE_BED_TOP
                && AroundPlan.NOSE_TOP + TopCar.NOSE_H == AroundPlan.SCREEN_H);
        BufferedImage tailMap = ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(TopCar.TAIL_PAINT)));
        yes("its paint map is a picture of its size", tailMap != null && tailMap.getWidth() == TopCar.TAIL_W && tailMap.getHeight() == TopCar.TAIL_H);
        BufferedImage noseMap = ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(TopCar.NOSE_PAINT)));
        yes("the front's paint map too", noseMap != null && noseMap.getWidth() == TopCar.NOSE_W && noseMap.getHeight() == TopCar.NOSE_H);
        byte[] car = java.util.Base64.getDecoder().decode(TopCar.CAR), map = java.util.Base64.getDecoder().decode(TopCar.PAINT);
        yes("the car is a WebP picture", car.length > 1000 && car[0] == 'R' && car[1] == 'I' && car[2] == 'F' && car[3] == 'F' && car[8] == 'W' && car[9] == 'E'
                && car[10] == 'B' && car[11] == 'P');
        BufferedImage paintMap = ImageIO.read(new java.io.ByteArrayInputStream(map));
        yes("its paint map is a picture of the car's size", paintMap != null && paintMap.getWidth() == TopCar.W && paintMap.getHeight() == TopCar.H);

        int[] px = { 0xffbcbdc2, 0xffbcbdc2, 0xffbcbdc2, 0xff1c1e22, 0x00000000 }, where = { 255, 0, 128, 255, 255 };
        int[] all = px.clone(), mapped = px.clone();
        BodyPaint.paint(all, BodyPaint.of("hichroma_red"));
        BodyPaint.paint(mapped, BodyPaint.of("hichroma_red"), where);
        yes("where the map says paint, the colour", mapped[0] == all[0] && reddish(mapped[0]));
        yes("where it says none, the picture", mapped[1] == px[1]);
        int half = (mapped[2] >> 16) & 255, full = (all[2] >> 16) & 255, none = (px[2] >> 16) & 255, green = (mapped[2] >> 8) & 255;
        yes("in between, in between: " + half + "/" + green, green < ((px[2] >> 8) & 255) && green > ((all[2] >> 8) & 255) && Math.abs(half - (none + full) / 2) <= 2);
        yes("glass and nothing stay under any map", mapped[3] == px[3] && mapped[4] == px[4]);
        int[] wrong = px.clone();
        BodyPaint.paint(wrong, 4, new int[] { 255 });
        BodyPaint.paint(wrong, 4, null);
        BodyPaint.paint(wrong, -1, where);
        yes("a map of another size, none, or no colour changes nothing", java.util.Arrays.equals(wrong, px));
    }

    private static boolean aheadOk() {
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (String name : Art.SIGNS_AHEAD) if (Art.family(name) != Art.SIGN || !seen.add(name)) return false;
        return seen.size() == 38;
    }

    private static void fill(int[] px, int w, int x0, int y0, int x1, int y1, int colour) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * w + x] = colour;
    }

    private static boolean reddish(int p) {
        return ((p >> 16) & 255) > 150 && ((p >> 8) & 255) < 60 && (p & 255) < 70;
    }

    /** Resizing: sizes, a flat colour stays flat, nothing appears out of nowhere, edges keep their colour */
    static void resize() {
        int w = 40, h = 30;
        int[] flat = new int[w * h];
        java.util.Arrays.fill(flat, 0xff40a0e0);
        same("same size: a copy", 0, differ(flat, Resample.lanczos(flat, w, h, w, h), 0));
        int[] big = Resample.lanczos(flat, w, h, 64, 48);
        same("enlarged: size", 64 * 48, big.length);
        same("enlarged: a flat colour stays flat", 0, differ(big, filled(64 * 48, 0xff40a0e0), 1));
        int[] small = Resample.lanczos(flat, w, h, 13, 11);
        same("reduced: a flat colour stays flat", 0, differ(small, filled(13 * 11, 0xff40a0e0), 1));
        same("transparent stays transparent", 0, differ(Resample.lanczos(new int[w * h], w, h, 64, 48), new int[64 * 48], 0));
        // a white line on a transparent background: wherever it shows it must be white, not greyed by the transparency next to it
        int[] line = new int[w * h];
        for (int x = 0; x < w; x++) line[15 * w + x] = 0xffffffff;
        int[] wide = Resample.lanczos(line, w, h, 64, 48);
        int grey = 0, seen = 0, strongest = 0;
        for (int p : wide) {
            if ((p >>> 24) < 8) continue;
            seen++;
            strongest = Math.max(strongest, p >>> 24);
            if ((p & 255) < 250 || ((p >> 8) & 255) < 250 || ((p >> 16) & 255) < 250) grey++;
        }
        // one pixel thick, enlarged 1.6 times: spread over two rows, the stronger one nearly opaque
        yes("a white line is still a strong line (alpha " + strongest + ")", seen >= 64 && strongest >= 220);
        same("a white line stays white at its edges", 0, grey);
        // half-transparent red stays half-transparent red
        int[] half = Resample.lanczos(filled(w * h, 0x80ff0000), w, h, 64, 48);
        same("half transparent keeps its alpha and colour", 0, differ(half, filled(64 * 48, 0x80ff0000), 2));
    }

    static int[] filled(int n, int colour) {
        int[] out = new int[n];
        java.util.Arrays.fill(out, colour);
        return out;
    }

    /** Number of pixels that differ by more than this in any of their four channels */
    static int differ(int[] a, int[] b, int by) {
        if (a.length != b.length) return Math.max(a.length, b.length);
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            for (int s = 0; s <= 24; s += 8) {
                if (Math.abs(((a[i] >> s) & 255) - ((b[i] >> s) & 255)) > by) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /** The real source pictures: each mode's floor-and-car picture against its reference */
    static void pieces(File dir) throws Exception {
        BufferedImage car = ImageIO.read(new File(dir, "vs/set_etc_drivemode_comfort_ck.png"));
        same("car drawing width", Art.CAR_W, car.getWidth());
        same("car drawing height", Art.CAR_H, car.getHeight());
        for (int mode = 0; mode < Art.MODES.length; mode++) {
            BufferedImage floor = ImageIO.read(new File(dir, "vs/set_etc_drive_bg_" + Art.FLOORS[mode] + ".png"));
            BufferedImage want = ImageIO.read(new File(dir, "rebuild/rebuild_" + Art.MODES[mode] + ".png"));
            same(Art.MODES[mode] + " floor width", Art.BASE_W, floor.getWidth());
            same(Art.MODES[mode] + " floor height", Art.BASE_H, floor.getHeight());
            int w = car.getWidth(), h = car.getHeight();
            int[] px = car.getRGB(0, 0, w, h, null, 0, w);
            Art.tint(px, Art.TONES[mode]);
            int off = 0, worst = 0, margin = 0;
            for (int y = 0; y < Art.BASE_H; y++) for (int x = 0; x < Art.BASE_W; x++) {
                int under = floor.getRGB(x, y), over = y >= Art.CAR_Y && y < Art.CAR_Y + h ? px[(y - Art.CAR_Y) * w + x] : 0;
                int have = over(under, over), wanted = want.getRGB(x, y);
                boolean inside = x >= Art.INNER_X && x < Art.INNER_X + Art.INNER_W && y >= Art.INNER_Y && y < Art.INNER_Y + Art.INNER_H;
                if (!inside) margin = Math.max(margin, have >>> 24);
                int d = 0;
                // compare as composited on black: a fully transparent pixel has no colour to compare
                for (int s = 0; s <= 16; s += 8) {
                    d = Math.max(d, Math.abs(((have >> s) & 255) * (have >>> 24) / 255 - ((wanted >> s) & 255) * (wanted >>> 24) / 255));
                }
                d = Math.max(d, Math.abs((have >>> 24) - (wanted >>> 24)));
                worst = Math.max(worst, d);
                if (d > 3) off++;
            }
            same(Art.MODES[mode] + " picture: pixels off by more than 3 (worst " + worst + ")", 0, off);
            yes(Art.MODES[mode] + " picture: nothing to see outside its filled part (strongest alpha " + margin + " of 255)", margin <= 24);
        }
    }

    /** Plain "source over" of two non-premultiplied ARGB colours */
    static int over(int under, int over) {
        float ao = (over >>> 24) / 255f, au = (under >>> 24) / 255f, a = ao + au * (1 - ao);
        if (a <= 0) return 0;
        int out = Math.round(a * 255) << 24;
        for (int s = 0; s <= 16; s += 8) {
            float c = (((over >> s) & 255) * ao + ((under >> s) & 255) * au * (1 - ao)) / a;
            out |= Math.min(255, Math.round(c)) << s;
        }
        return out;
    }
}
