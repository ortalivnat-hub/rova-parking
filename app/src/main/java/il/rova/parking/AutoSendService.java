package il.rova.parking;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Only while a permit call started from this app is running:
 * when the phone shows its "send tones" prompt (caused by ";" in the dialed number),
 * wait the calibrated time for that question and press "send".
 * Does nothing at any other time.
 */
public class AutoSendService extends AccessibilityService {

    private static final long JOB_TIMEOUT_MS = 5 * 60 * 1000;
    private static final Set<String> SEND_WORDS = new HashSet<>(Arrays.asList(
            "שלח", "שליחה", "שלח צלילים", "שלח טונים", "כן", "אישור",
            "send", "send tones", "yes", "ok"
    ));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastDump;
    private long lastClickAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Store.Job j = Store.job();
        if (j == null || !j.active) return;
        if (System.currentTimeMillis() - j.startedAt > JOB_TIMEOUT_MS) {
            Store.stopJob();
            Store.log(this, "הפסקתי: עברו 5 דקות");
            return;
        }
        if (j.next >= j.steps.size()) { Store.stopJob(); return; }
        if (j.scheduledFor == j.next) return; // already waiting to press for this question
        // the old prompt may still be on screen for a moment right after a press
        if (System.currentTimeMillis() - lastClickAt < 1500) return;

        AccessibilityNodeInfo send = findSend();
        if (send == null) {
            maybeDump(event);
            return;
        }
        final int stepIndex = j.next;
        final Store.Step step = j.steps.get(stepIndex);
        j.scheduledFor = stepIndex;
        Store.log(this, "הופיע \"שלח\" ל" + step.label + ". לוחץ בעוד " + (step.delayMs / 1000f) + " שניות");
        handler.postDelayed(() -> press(stepIndex), step.delayMs);
    }

    private void press(int stepIndex) {
        Store.Job j = Store.job();
        if (j == null || !j.active || j.next != stepIndex) return;
        AccessibilityNodeInfo send = findSend();
        Store.Step step = j.steps.get(stepIndex);
        if (send == null) {
            Store.log(this, "לא מצאתי את \"שלח\" ל" + step.label + " (אולי כבר נלחץ או שהשיחה נותקה)");
            j.scheduledFor = -1;
            return;
        }
        boolean ok = click(send);
        if (ok) lastClickAt = System.currentTimeMillis();
        Store.log(this, (ok ? "לחצתי \"שלח\": " : "הלחיצה נכשלה: ") + step.label + " (" + step.tones + ")");
        if (!ok) { j.scheduledFor = -1; return; }
        j.next = stepIndex + 1;
        j.scheduledFor = -1;
        if (j.next >= j.steps.size()) {
            Store.stopJob();
            Store.log(this, "סיימתי את כל השלבים ✓");
        }
    }

    private static boolean click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (cur.isClickable() && cur.isEnabled()) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            cur = cur.getParent();
        }
        return false;
    }

    /** Finds a visible "send"/"yes" button in phone-related windows. */
    private AccessibilityNodeInfo findSend() {
        for (AccessibilityNodeInfo root : roots()) {
            if (root == null || !isPhoneUi(root)) continue;
            ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);
            int seen = 0;
            while (!q.isEmpty() && seen++ < 600) {
                AccessibilityNodeInfo n = q.poll();
                if (n == null) continue;
                if (n.isVisibleToUser() && SEND_WORDS.contains(norm(n.getText())) || n.isVisibleToUser() && SEND_WORDS.contains(norm(n.getContentDescription()))) {
                    return n;
                }
                for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
            }
        }
        return null;
    }

    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> r = new ArrayList<>();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root != null) r.add(root);
            }
        } catch (Exception ignored) { }
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) r.add(active);
        return r;
    }

    private static boolean isPhoneUi(AccessibilityNodeInfo root) {
        CharSequence pkg = root.getPackageName();
        if (pkg == null) return false;
        String s = pkg.toString().toLowerCase(Locale.ROOT);
        return s.contains("dialer") || s.contains("incallui") || s.contains("telecom")
                || s.contains("phone") || s.contains("contacts") || s.contains("incall");
    }

    private static String norm(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim().toLowerCase(Locale.ROOT);
    }

    /** While a call is running and no button was found, log what the phone screen shows (helps tuning). */
    private void maybeDump(AccessibilityEvent e) {
        long now = System.currentTimeMillis();
        if (now - lastDump < 4000) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        String s = pkg.toString().toLowerCase(Locale.ROOT);
        if (!(s.contains("dialer") || s.contains("incallui") || s.contains("phone") || s.contains("incall"))) return;
        lastDump = now;
        StringBuilder b = new StringBuilder();
        int count = 0;
        for (AccessibilityNodeInfo root : roots()) {
            if (root == null || !isPhoneUi(root)) continue;
            ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);
            int seen = 0;
            while (!q.isEmpty() && seen++ < 400 && count < 25) {
                AccessibilityNodeInfo n = q.poll();
                if (n == null) continue;
                String t = norm(n.getText());
                if (t.isEmpty()) t = norm(n.getContentDescription());
                if (!t.isEmpty() && t.length() < 40) { b.append('[').append(t).append("] "); count++; }
                for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
            }
        }
        if (b.length() > 0) Store.log(this, "במסך השיחה (" + pkg + "): " + b);
    }

    @Override
    public void onInterrupt() { }
}
