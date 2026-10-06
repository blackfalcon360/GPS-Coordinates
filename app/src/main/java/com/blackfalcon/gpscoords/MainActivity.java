package com.blackfalcon.gpscoords;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity implements LocationListener {

    private static final int GREEN = Color.parseColor("#2ECC71");
    private static final int CARD = Color.parseColor("#121212");
    private static final int BORDER = Color.parseColor("#2A2A2A");
    private static final int GRAY = Color.parseColor("#9E9E9E");
    private static final String NONE = "\u2014";

    private static final int DD = 0, DDH = 1, DMS = 2, DDM = 3, UTM = 4, MGRS = 5, MERC = 6, MAID = 7,
            PLUS = 8, GEOH = 9, GMAPS = 10, GEO = 11, OSM = 12, ALT = 13, ACC = 14, TIME = 15;

    private static class Entry {
        String title, value = NONE;
        TextView valueTv;
    }

    private final List<Entry> entries = new ArrayList<>();
    private TextView statusTv;

    private LocationManager lm;
    private SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeout;

    private boolean hasFix = false, live = false, searching = false, wasSearching = false;
    private double lat, lon, alt, acc;
    private boolean hasAlt = false, hasAcc = false;
    private long fixTime = 0;
    private long lastGpsMs = 0;
    private int status = 0; // 0 none,1 searching,2 live,3 saved,4 gps off,5 no permission,6 no fix

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sp = getSharedPreferences("gps", MODE_PRIVATE);

        buildUi();

        if (sp.contains("lat") && sp.contains("lon")) {
            lat = Double.longBitsToDouble(sp.getLong("lat", 0));
            lon = Double.longBitsToDouble(sp.getLong("lon", 0));
            fixTime = sp.getLong("time", 0);
            hasFix = true;
            status = 3;
        }
        render();

        if (hasPerm()) {
            startLocation();
        } else {
            status = 5;
            renderStatus();
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wasSearching || live) { wasSearching = false; if (hasPerm()) startLocation(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasSearching = searching || live;
        stopLocation();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (hasPerm()) startLocation(); else { status = 5; renderStatus(); }
    }

    private boolean hasPerm() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------------ UI
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(6));

        TextView title = tv(24, Color.WHITE, true);
        title.setText("\uD83D\uDCCD GPS Coordinates");
        statusTv = tv(13, GRAY, false);
        statusTv.setPadding(0, dp(2), 0, dp(10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView refresh = pill("\u21BB  Refresh");
        refresh.setOnClickListener(v -> startLocation());
        TextView copyAll = pill("\u29C9  Copy all");
        copyAll.setOnClickListener(v -> copyAll());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l1.setMarginEnd(dp(6));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l2.setMarginStart(dp(6));
        row.addView(refresh, l1);
        row.addView(copyAll, l2);

        header.addView(title);
        header.addView(statusTv);
        header.addView(row);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(4), dp(12), dp(12));

        section(list, "DEGREES");
        add(list, "Decimal degrees (DD)");
        add(list, "DD with N / S / E / W");
        add(list, "Degrees, minutes, seconds (DMS)");
        add(list, "Degrees, decimal minutes (DDM)");
        section(list, "GRIDS");
        add(list, "UTM");
        add(list, "MGRS");
        add(list, "Web Mercator (EPSG:3857)");
        add(list, "Maidenhead locator");
        section(list, "CODES");
        add(list, "Plus Code (Open Location Code)");
        add(list, "Geohash");
        section(list, "LINKS");
        add(list, "Google Maps link");
        add(list, "Geo URI");
        add(list, "OpenStreetMap link");
        section(list, "EXTRA");
        add(list, "Altitude (GPS)");
        add(list, "Accuracy");
        add(list, "Fix time (UTC)");

        ScrollView sv = new ScrollView(this);
        sv.addView(list);

        TextView credit = tv(14, Color.WHITE, true);
        credit.setText("By: Black Falcon \uD83E\uDD85");
        credit.setGravity(Gravity.RIGHT);
        credit.setPadding(dp(16), dp(6), dp(16), dp(10));

        root.addView(header);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(credit);
        setContentView(root);
    }

    private TextView tv(int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String s) {
        TextView b = tv(15, GREEN, true);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(12), dp(10), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#0D1F14"));
        g.setStroke(dp(1), GREEN);
        g.setCornerRadius(dp(24));
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    private void section(LinearLayout list, String name) {
        TextView t = tv(12, GREEN, true);
        t.setText(name);
        t.setPadding(dp(6), dp(14), 0, dp(2));
        list.addView(t);
    }

    private void add(LinearLayout list, String title) {
        final Entry e = new Entry();
        e.title = title;
        entries.add(e);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(10), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setStroke(dp(1), BORDER);
        g.setCornerRadius(dp(14));
        card.setBackground(g);
        card.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(lp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView label = tv(12, GRAY, false);
        label.setText(title);
        e.valueTv = tv(17, Color.WHITE, true);
        e.valueTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        e.valueTv.setText(NONE);
        col.addView(label);
        col.addView(e.valueTv);
        card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView copy = tv(13, Color.BLACK, true);
        copy.setText("Copy");
        copy.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(GREEN);
        cg.setCornerRadius(dp(16));
        copy.setBackground(cg);
        card.addView(copy);

        View.OnClickListener cl = v -> copy(e.title, e.value);
        card.setOnClickListener(cl);
        copy.setOnClickListener(cl);
        list.addView(card);
    }

    // -------------------------------------------------------------- values
    private void set(int i, String v) {
        Entry e = entries.get(i);
        e.value = v;
        e.valueTv.setText(v);
    }

    private void render() {
        if (!hasFix) {
            for (int i = 0; i < entries.size(); i++) set(i, NONE);
            renderStatus();
            return;
        }
        set(DD, Coords.dd(lat, lon));
        set(DDH, Coords.ddHemi(lat, lon));
        set(DMS, Coords.dms(lat, lon));
        set(DDM, Coords.ddm(lat, lon));
        set(UTM, Coords.utm(lat, lon));
        set(MGRS, Coords.mgrs(lat, lon));
        set(MERC, Coords.mercator(lat, lon));
        set(MAID, Coords.maidenhead(lat, lon));
        set(PLUS, Coords.plusCode(lat, lon));
        set(GEOH, Coords.geohash(lat, lon, 10));
        set(GMAPS, Coords.googleMaps(lat, lon));
        set(GEO, Coords.geoUri(lat, lon));
        set(OSM, Coords.osm(lat, lon));
        set(ALT, hasAlt ? String.format(Locale.US, "%.0f m  (%.0f ft)", alt, alt * 3.28084) : NONE);
        set(ACC, hasAcc ? String.format(Locale.US, "\u00B1 %.0f m", acc) : NONE);
        if (fixTime > 0) {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            set(TIME, f.format(new Date(fixTime)) + " UTC");
        } else {
            set(TIME, NONE);
        }
        renderStatus();
    }

    private void renderStatus() {
        int color = GRAY;
        String s;
        switch (status) {
            case 1: s = hasFix ? "Getting a new GPS fix\u2026 (showing saved location)" : "Getting GPS fix\u2026"; break;
            case 2:
                s = "\u25CF Live GPS" + (hasAcc ? String.format(Locale.US, " \u2022 accuracy \u00B1%.0f m", acc) : "") + " \u2022 works offline";
                color = GREEN; break;
            case 3: s = "Saved location \u2022 tap Refresh for a new fix"; break;
            case 4: s = "GPS is off \u2014 turn it on, then tap Refresh"; break;
            case 5: s = "Location permission is needed"; break;
            case 6: s = hasFix ? "No GPS fix \u2014 showing saved location" : "No GPS fix yet \u2014 go outside and tap Refresh"; break;
            default: s = "Tap Refresh to get your location";
        }
        statusTv.setText(s);
        statusTv.setTextColor(color);
    }

    // ---------------------------------------------------------------- copy
    private void copy(String label, String text) {
        if (NONE.equals(text)) {
            Toast.makeText(this, "Nothing to copy yet \u2014 waiting for GPS", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, "Copied \u2713  " + label, Toast.LENGTH_SHORT).show();
    }

    private void copyAll() {
        if (!hasFix) { copy("All", NONE); return; }
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (NONE.equals(e.value)) continue;
            sb.append(e.title).append(": ").append(e.value).append("\n");
        }
        copy("All formats", sb.toString().trim());
    }

    // ------------------------------------------------------------ location
    private void startLocation() {
        if (!hasPerm()) {
            status = 5; renderStatus();
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
            return;
        }
        stopLocation();
        lastGpsMs = 0; // accept the first new fix again
        boolean any = false;
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(p)) { lm.requestLocationUpdates(p, 1000, 0, this); any = true; }
            }
            if (!hasFix) {
                Location last = null;
                for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
                }
                if (last != null) { applyFix(last, false); }
            }
        } catch (SecurityException ignored) { }

        if (!any) { status = 4; render(); return; }
        searching = true;
        status = 1;
        renderStatus();
        timeout = () -> {
            if (!searching) return;
            // no fix in time: keep listening quietly, but tell the user
            searching = false;
            status = 6;
            renderStatus();
        };
        handler.postDelayed(timeout, 30000);
    }

    private void stopLocation() {
        searching = false;
        live = false;
        if (timeout != null) { handler.removeCallbacks(timeout); timeout = null; }
        try { lm.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    private void applyFix(Location l, boolean isLive) {
        lat = l.getLatitude();
        lon = l.getLongitude();
        hasAlt = l.hasAltitude();
        alt = l.getAltitude();
        hasAcc = l.hasAccuracy();
        acc = l.getAccuracy();
        fixTime = l.getTime();
        hasFix = true;
        if (isLive) {
            live = true;
            searching = false;
            if (timeout != null) { handler.removeCallbacks(timeout); timeout = null; }
            status = 2;
            sp.edit().putLong("lat", Double.doubleToRawLongBits(lat))
                    .putLong("lon", Double.doubleToRawLongBits(lon))
                    .putLong("time", fixTime).apply();
        } else {
            status = 3;
        }
        render();
    }

    @Override
    public void onLocationChanged(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        long now = System.currentTimeMillis();
        if (gps) {
            lastGpsMs = now;
        } else if (now - lastGpsMs < 15000) {
            return; // a recent GPS fix is better than a network guess
        }
        applyFix(l, true);
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }
}
