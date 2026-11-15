package il.rova.parking;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_CALL = 7;

    private LinearLayout root;
    private TextView statusText;
    private Button enableBtn;
    private EditText plateIn;
    private RadioGroup typeGroup;
    private LinearLayout settingsBox;
    private EditText hotlineIn, pinIn, idIn, phoneIn, guestIn, serviceIn, deliveryIn, confirmIn, lastIn;
    private CheckBox hashBox;
    private final EditText[] delayIns = new EditText[Store.STEP_LABELS.length];
    private TextView logText;
    private String pendingPlate;
    private int pendingType;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        setContentView(sv);

        TextView title = text("חניה ברובע", 24, true);
        root.addView(title);
        root.addView(text("מזינים מספר רכב, לוחצים חייג, והטלפון עושה את השאר. מומלץ לשים על רמקול ולהשאיר את המסך דלוק.", 14, false));

        // status
        statusText = text("", 15, true);
        statusText.setPadding(0, dp(12), 0, dp(4));
        root.addView(statusText);
        enableBtn = button("הפעל לחיצה אוטומטית (הגדרות נגישות)", 0xFF1F4E8C);
        enableBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(enableBtn);

        // permit
        root.addView(section("אישור כניסה"));
        root.addView(text("מספר רכב", 14, true));
        plateIn = input(InputType.TYPE_CLASS_NUMBER, "1234567");
        root.addView(plateIn);
        typeGroup = new RadioGroup(this);
        typeGroup.setOrientation(LinearLayout.HORIZONTAL);
        String[] types = {"אורח", "נותן שירות", "משלוח"};
        for (int i = 0; i < types.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(types[i]);
            rb.setId(100 + i);
            rb.setTextSize(16);
            typeGroup.addView(rb);
        }
        typeGroup.check(100);
        root.addView(typeGroup);
        Button dial = button("חייג ואשר כניסה", 0xFF1D7A4F);
        dial.setTextSize(20);
        dial.setOnClickListener(v -> dial());
        root.addView(dial);

        // settings
        Button toggle = button("הגדרות וזמנים", 0xFF555555);
        root.addView(toggle);
        settingsBox = new LinearLayout(this);
        settingsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(settingsBox);
        toggle.setOnClickListener(v -> settingsBox.setVisibility(settingsBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        hotlineIn = field("טלפון המוקד", InputType.TYPE_CLASS_PHONE, Store.get(this, "hotline", "026281615"));
        pinIn = field("קוד סודי", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, Store.get(this, "pin", ""));
        idIn = field("תעודת זהות", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, Store.get(this, "id", ""));
        phoneIn = field("טלפון מארח", InputType.TYPE_CLASS_PHONE, Store.get(this, "phone", ""));
        guestIn = field("מקש אורח", InputType.TYPE_CLASS_PHONE, Store.get(this, "keyGuest", "1"));
        serviceIn = field("מקש נותן שירות", InputType.TYPE_CLASS_PHONE, Store.get(this, "keyService", "2"));
        deliveryIn = field("מקש משלוח", InputType.TYPE_CLASS_PHONE, Store.get(this, "keyDelivery", "3"));
        hashBox = new CheckBox(this);
        hashBox.setText("להקיש # אחרי סוג האישור");
        hashBox.setChecked(Store.getBool(this, "hashAfterType", true));
        settingsBox.addView(hashBox);
        confirmIn = field("מקשי אישור סופי (ריק = בלי)", InputType.TYPE_CLASS_PHONE, Store.get(this, "confirm", "1#"));
        lastIn = field("מקשי \"לאישור הקש 1\" (ריק = בלי)", InputType.TYPE_CLASS_PHONE, Store.get(this, "last", "1#"));

        settingsBox.addView(section("זמני המתנה (שניות)"));
        settingsBox.addView(text("כמה לחכות מהרגע שמופיע \"שלח\" ועד הלחיצה. זה הזמן שהמוקד מדבר לפני כל שאלה. אפשר להעתיק את הזמנים מהכיול שעשית.", 13, false));
        for (int i = 0; i < Store.STEP_LABELS.length; i++) {
            delayIns[i] = field("לפני: " + Store.STEP_LABELS[i], InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                    trimFloat(Store.getDelay(this, i)));
        }
        Button save = button("שמור הגדרות", 0xFF1D7A4F);
        save.setOnClickListener(v -> { saveSettings(); toast("נשמר"); });
        settingsBox.addView(save);
        settingsBox.setVisibility(Store.get(this, "pin", "").isEmpty() ? View.VISIBLE : View.GONE);

        // log
        root.addView(section("יומן (לבדיקה)"));
        LinearLayout logBtns = new LinearLayout(this);
        logBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = button("העתק יומן", 0xFF555555);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("log", Store.readLog(this)));
            toast("הועתק");
        });
        Button clear = button("נקה", 0xFF555555);
        clear.setOnClickListener(v -> { Store.clearLog(this); refresh(); });
        logBtns.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        logBtns.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(logBtns);
        logText = text("", 12, false);
        logText.setTextIsSelectable(true);
        root.addView(logText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean on = serviceEnabled();
        statusText.setText(on ? "✓ לחיצה אוטומטית פעילה" : "✗ לחיצה אוטומטית כבויה. צריך להפעיל אותה פעם אחת");
        statusText.setTextColor(on ? 0xFF1D7A4F : 0xFFB3362F);
        enableBtn.setVisibility(on ? View.GONE : View.VISIBLE);
        String log = Store.readLog(this);
        logText.setText(log.isEmpty() ? "אין רישומים עדיין." : log);
    }

    private void saveSettings() {
        Store.put(this, "hotline", Store.digits(hotlineIn.getText().toString()));
        Store.put(this, "pin", Store.digits(pinIn.getText().toString()));
        Store.put(this, "id", Store.digits(idIn.getText().toString()));
        Store.put(this, "phone", Store.digits(phoneIn.getText().toString()));
        Store.put(this, "keyGuest", Store.keys(guestIn.getText().toString()));
        Store.put(this, "keyService", Store.keys(serviceIn.getText().toString()));
        Store.put(this, "keyDelivery", Store.keys(deliveryIn.getText().toString()));
        Store.putBool(this, "hashAfterType", hashBox.isChecked());
        Store.put(this, "confirm", Store.keys(confirmIn.getText().toString()));
        Store.put(this, "last", Store.keys(lastIn.getText().toString()));
        for (int i = 0; i < delayIns.length; i++) {
            float v;
            try { v = Float.parseFloat(delayIns[i].getText().toString().trim()); } catch (Exception e) { v = Store.DEFAULT_DELAYS[i]; }
            Store.putDelay(this, i, Math.max(0.5f, Math.min(60f, v)));
        }
    }

    private void dial() {
        saveSettings();
        String plate = Store.digits(plateIn.getText().toString());
        if (Store.get(this, "pin", "").isEmpty() || Store.get(this, "id", "").isEmpty() || Store.get(this, "phone", "").isEmpty()) {
            settingsBox.setVisibility(View.VISIBLE);
            toast("קודם ממלאים בהגדרות קוד סודי, ת.ז וטלפון מארח");
            return;
        }
        if (plate.length() < 5) { toast("צריך מספר רכב (5 עד 8 ספרות)"); return; }
        if (!serviceEnabled()) toast("הלחיצה האוטומטית כבויה: תצטרכו ללחוץ \"שלח\" בעצמכם");
        int type = typeGroup.getCheckedRadioButtonId() - 100;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingPlate = plate;
            pendingType = type;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            return;
        }
        placeCall(plate, type);
    }

    private void placeCall(String plate, int type) {
        List<Store.Step> steps = Store.buildSteps(this, plate, type);
        String number = Store.buildDialString(this, steps);
        Store.startJob(steps);
        Store.log(this, "חיוג: " + plate + " (" + steps.size() + " שלבים)");
        Intent call = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
        try {
            startActivity(call);
        } catch (Exception e) {
            Store.stopJob();
            Store.log(this, "החיוג נכשל: " + e.getMessage());
            toast("החיוג נכשל");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQ_CALL && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && pendingPlate != null) {
            placeCall(pendingPlate, pendingType);
        } else if (requestCode == REQ_CALL) {
            toast("בלי הרשאת שיחות האפליקציה לא יכולה לחייג");
        }
    }

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(this, AutoSendService.class);
        for (String s : enabled.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    // ---------- tiny UI helpers ----------
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private Button button(String s, int color) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(2), dp(8), dp(2), dp(4));
        btn.setLayoutParams(lp);
        btn.setPadding(dp(12), dp(14), dp(12), dp(14));
        return btn;
    }

    private EditText input(int type, String hint) {
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setHint(hint);
        e.setTextSize(18);
        e.setTextDirection(View.TEXT_DIRECTION_LTR);
        e.setGravity(Gravity.CENTER);
        return e;
    }

    private EditText field(String label, int type, String value) {
        settingsBox.addView(text(label, 14, true));
        EditText e = input(type, "");
        e.setText(value);
        settingsBox.addView(e);
        return e;
    }

    private static String trimFloat(float f) {
        return f == (long) f ? String.valueOf((long) f) : String.valueOf(f);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
