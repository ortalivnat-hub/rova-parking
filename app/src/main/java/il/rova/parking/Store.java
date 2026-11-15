package il.rova.parking;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings, the current call job and a short diagnostic log. */
public final class Store {
    private Store() {}

    /** Questions the hotline asks, in order. */
    public static final String[] STEP_LABELS = {
            "קוד סודי",
            "תעודת זהות",
            "טלפון מארח",
            "מספר רכב",
            "סוג אישור",
            "אישור סופי",
            "לאישור הקש 1"
    };
    /** Default wait (seconds) from the moment "send" appears until it is pressed. */
    public static final float[] DEFAULT_DELAYS = {10f, 4f, 10f, 4f, 6f, 4f, 10f};

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("rova", Context.MODE_PRIVATE);
    }

    public static String get(Context c, String key, String def) { return p(c).getString(key, def); }
    public static void put(Context c, String key, String v) { p(c).edit().putString(key, v).apply(); }
    public static boolean getBool(Context c, String key, boolean def) { return p(c).getBoolean(key, def); }
    public static void putBool(Context c, String key, boolean v) { p(c).edit().putBoolean(key, v).apply(); }

    public static float getDelay(Context c, int i) { return p(c).getFloat("delay" + i, DEFAULT_DELAYS[i]); }
    public static void putDelay(Context c, int i, float v) { p(c).edit().putFloat("delay" + i, v).apply(); }

    public static String digits(String s) { return s == null ? "" : s.replaceAll("[^0-9]", ""); }
    public static String keys(String s) { return s == null ? "" : s.replaceAll("[^0-9*#]", ""); }

    /** One answer the phone sends after the user (or the service) presses "send". */
    public static final class Step {
        public final String label;
        public final String tones;
        public final long delayMs;
        Step(String label, String tones, long delayMs) { this.label = label; this.tones = tones; this.delayMs = delayMs; }
    }

    /** Builds the answers for one permit call. type: 0 guest, 1 service, 2 delivery. */
    public static List<Step> buildSteps(Context c, String plate, int type) {
        List<Step> s = new ArrayList<>();
        String typeKey = type == 1 ? get(c, "keyService", "2") : type == 2 ? get(c, "keyDelivery", "3") : get(c, "keyGuest", "1");
        boolean hashAfterType = getBool(c, "hashAfterType", true);
        String[] tones = {
                digits(get(c, "pin", "")) + "#",
                digits(get(c, "id", "")) + "#",
                digits(get(c, "phone", "")) + "#",
                digits(plate) + "#",
                keys(typeKey) + (hashAfterType ? "#" : ""),
                keys(get(c, "confirm", "1#")),
                keys(get(c, "last", "1#"))
        };
        for (int i = 0; i < tones.length; i++) {
            if (tones[i].isEmpty()) continue; // optional steps left empty
            s.add(new Step(STEP_LABELS[i], tones[i], (long) (getDelay(c, i) * 1000)));
        }
        return s;
    }

    /** Hotline number followed by ";answer" for every step. ";" makes the phone wait for "send". */
    public static String buildDialString(Context c, List<Step> steps) {
        StringBuilder b = new StringBuilder(digits(get(c, "hotline", "026281615")));
        for (Step st : steps) b.append(';').append(st.tones);
        return b.toString();
    }

    // ---------- current job (shared between the screen and the service; same process) ----------
    public static final class Job {
        public List<Step> steps;
        public int next;          // index of the next answer to send
        public int scheduledFor = -1;
        public long startedAt;
        public boolean active;
    }

    private static Job job;

    public static synchronized Job job() { return job; }

    public static synchronized void startJob(List<Step> steps) {
        Job j = new Job();
        j.steps = steps;
        j.next = 0;
        j.startedAt = System.currentTimeMillis();
        j.active = true;
        job = j;
    }

    public static synchronized void stopJob() { if (job != null) job.active = false; }

    // ---------- diagnostic log ----------
    private static final StringBuilder LOG = new StringBuilder();

    public static synchronized void log(Context c, String line) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String prev = p(c).getString("log", "");
        String next = t + "  " + line + "\n" + prev;
        if (next.length() > 6000) next = next.substring(0, 6000);
        p(c).edit().putString("log", next).apply();
    }

    public static String readLog(Context c) { return p(c).getString("log", ""); }
    public static void clearLog(Context c) { p(c).edit().remove("log").apply(); }
}
