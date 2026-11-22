import hmgapi.engine.Json;
import hmgapi.engine.SystemCheck;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Map;

/**
 * Build-time check of the "system" entry in mod.json using HMG-API's own checker: a Stinger head
 * unit must be accepted and another car's refused (the mod would draw a Stinger there).
 *   java -cp <HMG-API runtime classes>:<this> SystemTest mod.json
 */
public final class SystemTest {
    static String ask(Object system, String product) throws Exception {
        File root = Files.createTempDirectory("imgshifter-system").toFile();
        try (FileWriter w = new FileWriter(new File(root, "props.txt"))) {
            w.write("ro.build.product=" + product + "\nro.board.platform=tcc897x\nro.build.version.release=4.4.2\n");
        }
        String why = SystemCheck.check(system, new SystemCheck.Facts(root.getPath(), null));
        new File(root, "props.txt").delete();
        root.delete();
        return why;
    }

    public static void main(String[] a) throws Exception {
        Map<String, Object> mod = Json.object(new String(Files.readAllBytes(new File(a[0]).toPath()), "UTF-8"));
        Object system = mod.get("system");
        int bad = 0;
        // product names as head units report them: <model>.<region>.<...>.<firmware date>
        String[] stingers = { "CKPE22.KOR.0000.V039.001.260902", "CKPE22.USA.0000.V001.001.260507", "CKPE23.EUR.0000.V001.001.260101" };
        String[] others = { "CVEV.USA.0000.V001.001.260507", "NEEV23.CAN.0000.V001.001.260206", "DL3.KOR.0000.V001.001.260101",
                "CN7.KOR.0000.V001.001.260101" };
        for (String p : stingers) {
            String why = ask(system, p);
            if (why != null) {
                bad++;
                System.out.println("FAIL a Stinger is refused: " + p + ": " + why);
            }
        }
        for (String p : others) {
            String why = ask(system, p);
            if (why == null) {
                bad++;
                System.out.println("FAIL another car is taken: " + p);
            }
        }
        System.out.println(bad == 0 ? "ok: \"system\" takes the Stinger only (" + (stingers.length + others.length) + " head units asked)"
                : "FAILED: " + bad);
        if (bad != 0) System.exit(1);
    }
}
