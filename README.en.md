# Stinger Image Shifter

[한국어](README.md)

An [HMG-API](https://github.com/Open-HMG/HMG-API) mod for the Kia Stinger (CK PE). When you change the drive mode, it shows the see-through Stinger drawing from the older infotainment instead of the stock picture.

The stock head unit shows a plain black saloon whenever you switch modes. The older system showed a see-through Stinger in the mode's colour, and I made this to get that picture back.

## What changes

- The right-hand panel on the map screen, and the pop-up that appears there when the mode changes
- The small car in the help picture on Vehicle settings > Drive mode
- The mode-change pop-up of the vehicle settings app (it never came up on the test car, so this one is unchecked)

The pictures of five modes change: Smart, Eco, Comfort, Sport and Custom.

The mod carries none of the stock pictures. The drawing and the floor of each mode are already on the car and are read from there. No stock file is touched, and when you switch the mod off the stock pictures are back right away.

## What you need

- A Stinger (CK PE) with the Gen5W navigation head unit. HMG-API refuses the mod on any other car.
- HMG-API 1.0.2 or later

## Install

1. Get `stinger-imgshifter-<version>.zip` from [Releases](https://github.com/parkky0331/kia_stinger_img_shifter/releases) and put it in `HMGAPI/updates/` on a USB stick. Don't unzip it. The stick should look like this:

   ```
   USB
   └── HMGAPI
       └── updates
           └── stinger-imgshifter-0.1.0.zip
   ```

2. In the HMG-API app: Check USB updates → install → Apply changes → Restart unit.
3. The camera screen's body colour (still testing) is in the mod's settings (Mods tab). Restart the unit once more after changing it.

To go back to stock, switch the mod off in the Mods tab.

## Tested on

Tested on one privately owned car only (firmware `CK_PE_22.KOR.S5W_M.V039.001.260902`). On other firmware the pictures may have different names or places; in that case the mod leaves them as they are.

<details>
<summary><b>Still testing: the car on the camera screen in your body colour</b></summary>

The car on the camera (surround view) screen is silver whatever colour your car is. Pick a colour in the settings and, while the camera screen is up, that car is shown in it. The default is "Off", and left there the camera app isn't touched at all.

The car on the screen is not a picture on the head unit. The camera unit draws it straight into the video, so it can't simply be repainted. Instead a car in the colour you picked is laid over it at the same place. The camera video itself is left alone.

The colours are the ones the Stinger was sold in: Snow White Pearl, Aurora Black Pearl, Panthera Metal, Ceramic Silver, HiChroma Red, Deep Chroma Blue, Micro Blue, Neon Orange, Ascot Green and Moonscape Matte Gray. They were matched to look close on the screen, so they may be a little off from the real paint.

It's still rough, so a few things to know:

- Where the car goes and how large it is were measured from photos of the test car's screen. On another car it may sit a few pixels off.
- It shows in the three rear layouts and the three front layouts. It covers the car in the top view on the right, and in the views that look down at the car, the large car in the left picture as well. The small cars on the view buttons are painted the same colour.
- Zoomed out, the stock car gets smaller and I haven't measured by how much yet. For now the overlay shrinks 5% per step.
- The overlaid car fades in over the stock one in half a second. When the screen closes or switches to another view it goes at once, so it never sits on top of the new picture.
- When the screen closes, the overlaid car can stay a split second longer than the video. There's a "draw the car on its own layer" setting meant to fix that, but I haven't been able to try it on the car yet. If the car is missing or the screen looks wrong with it on, switch it off.
- On a car without the surround view, the car picture between the parking sensor bars is painted the same colour. I had no such car to test that on.
- The overlaid car is not an official Stinger picture. I generated it with AI and cleaned it up myself.

</details>

<details>
<summary><b>How it works</b></summary>

- It steps in at the moment a stock app puts a picture on the screen (`ImageView.setImageResource`, and `View.setBackgroundResource` in the settings app) and hands over a different picture only for the drive mode pictures. Everything else passes through.
- The pieces are read from the vehicle settings app while it runs: the Stinger drawing, coloured for the mode, goes on top of that mode's floor.
- The old floor pictures are 600×330, the same as the right-hand panel's picture, so the panel shows them pixel for pixel. The settings pop-up needs them 1.6 times larger: it first gets a quickly scaled picture, then a sharper one made in the background.
- For the help picture the stock one is copied and only the pane with the small car is redrawn. If the frame isn't where it should be, the picture is left alone.
- The car on the camera screen is one picture laid over the video. On the head unit's own pictures, such as the view buttons, only the silver body is repainted; glass, lamps and the shadow stay.
- Pictures are made ahead in the background after the app starts. If one is needed before it's ready, the stock picture shows that once. The camera app never waits for the mod.
- If the mod is off, a piece is missing or a picture can't be made, you get the stock picture.
- No stock file is changed, nothing is sent to the car, and there's no network use.

</details>

<details>
<summary><b>Building it yourself</b></summary>

You need a JDK, Android API 19's `android.jar`, the Android SDK build-tools (d8) and a checkout of HMG-API.

```sh
export HMG_API=/path/to/HMG-API
export ANDROID_JAR=/path/to/Android/sdk/platforms/android-19/android.jar
export D8=/path/to/Android/sdk/build-tools/35.0.0/d8
export SIGNING_KEY=/absolute/path/to/your.key
sh build.sh        # dist/stinger-imgshifter-<version>.zip
```

The same values can go in a `local.env` file next to `build.sh` (git ignores it). Make your own signing key with HMG-API's signing tool; HMG-API only updates a mod with packages signed by the same key.

There are two checks:

```sh
sh tools/check.sh             # names, colouring, resizing, body paint; also runs in the build
bash tests/harness/run.sh     # on an emulator: stock and replaced pictures side by side
```

`tests/harness/run.sh` needs the three stock apps from your own car (`HKMC_VehicleSetting.apk`, `RightScreenHome.apk`, `HKMC_RearCamera.apk`). Stock apps, pictures and anything else pulled from a head unit don't belong in this repository.

</details>

<details>
<summary><b>Reading the log</b></summary>

Lines go to HMG-API's log with the tag `[stinger-imgshifter]`.

- `ready in <process>`: the hooks are in that app
- `sees <picture name> <size>: …`: a picture name met for the first time; `left as it is` means it wasn't touched
- `made … in N ms`: how long one picture took
- `shows ours for <picture name>`: a picture was replaced
- `<picture name> was asked for before its picture was ready`: the stock one went out that once
- `camera screen started: RearMode | AvmMode | AvmModeLowCam`: which camera screen came up
- `surround view: …`: with a body colour picked, what the camera unit reported and what the overlay did

</details>

## Good to know

- The settings pop-up shows the drawing (649×302) enlarged 1.6 times, so it can look a little soft.
- The drive mode help picture only changes on head units that use the newer help drawing (`usm_dm_008_01_n_…`).

## Notice

A personal hobby project, not affiliated with Kia, Hyundai or Hyundai Mobis. It changes what your head unit shows, so use it at your own risk. Licensed under the [GNU AGPL-3.0](LICENSE).
