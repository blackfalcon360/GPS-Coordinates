package com.blackfalcon.gpscoords;

import java.util.Locale;

/** Every coordinate format is calculated on the phone, so the app works fully offline. */
public final class Coords {

    private Coords() { }

    // ---------------------------------------------------------------- degrees
    public static String dd(double lat, double lon) {
        return String.format(Locale.US, "%.6f, %.6f", lat, lon);
    }

    public static String ddHemi(double lat, double lon) {
        return String.format(Locale.US, "%.6f\u00B0 %s, %.6f\u00B0 %s",
                Math.abs(lat), lat >= 0 ? "N" : "S", Math.abs(lon), lon >= 0 ? "E" : "W");
    }

    private static String dmsOne(double v, String pos, String neg) {
        double a = Math.abs(v);
        int d = (int) Math.floor(a);
        double mf = (a - d) * 60.0;
        int m = (int) Math.floor(mf);
        double s = Math.round((mf - m) * 60.0 * 100.0) / 100.0;
        if (s >= 60.0) { s = 0; m++; }
        if (m >= 60) { m = 0; d++; }
        return String.format(Locale.US, "%d\u00B0 %02d\u2032 %05.2f\u2033 %s", d, m, s, v >= 0 ? pos : neg);
    }

    public static String dms(double lat, double lon) {
        return dmsOne(lat, "N", "S") + ", " + dmsOne(lon, "E", "W");
    }

    private static String ddmOne(double v, String pos, String neg) {
        double a = Math.abs(v);
        int d = (int) Math.floor(a);
        double m = Math.round((a - d) * 60.0 * 10000.0) / 10000.0;
        if (m >= 60.0) { m = 0; d++; }
        return String.format(Locale.US, "%d\u00B0 %07.4f\u2032 %s", d, m, v >= 0 ? pos : neg);
    }

    public static String ddm(double lat, double lon) {
        return ddmOne(lat, "N", "S") + ", " + ddmOne(lon, "E", "W");
    }

    // -------------------------------------------------------------------- UTM
    private static final String LAT_BANDS = "CDEFGHJKLMNPQRSTUVWXX";

    public static final class Utm {
        public boolean valid = true;
        public int zone;
        public char band;
        public double e, n;
    }

    private static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }
    private static double asinh(double x) { return Math.log(x + Math.sqrt(x * x + 1)); }

    public static Utm toUtm(double lat, double lon) {
        Utm u = new Utm();
        if (lat < -80 || lat > 84) { u.valid = false; return u; }
        lon = ((lon + 540.0) % 360.0) - 180.0;

        int zone = (int) Math.floor((lon + 180.0) / 6.0) + 1;
        char band = LAT_BANDS.charAt((int) Math.floor(lat / 8.0 + 10.0));
        if (zone == 31 && band == 'V' && lon >= 3) zone++;
        if (zone == 32 && band == 'X' && lon < 9) zone--;
        if (zone == 32 && band == 'X' && lon >= 9) zone++;
        if (zone == 34 && band == 'X' && lon < 21) zone--;
        if (zone == 34 && band == 'X' && lon >= 21) zone++;
        if (zone == 36 && band == 'X' && lon < 33) zone--;
        if (zone == 36 && band == 'X' && lon >= 33) zone++;
        double lon0 = (zone - 1) * 6 - 180 + 3;

        double a = 6378137.0, f = 1 / 298.257223563, k0 = 0.9996;
        double ecc = Math.sqrt(f * (2 - f));
        double n = f / (2 - f);
        double n2 = n * n, n3 = n2 * n, n4 = n3 * n, n5 = n4 * n, n6 = n5 * n;

        double phi = Math.toRadians(lat), lam = Math.toRadians(lon - lon0);
        double cosl = Math.cos(lam), sinl = Math.sin(lam);
        double tau = Math.tan(phi);
        double sigma = Math.sinh(ecc * atanh(ecc * tau / Math.sqrt(1 + tau * tau)));
        double taup = tau * Math.sqrt(1 + sigma * sigma) - sigma * Math.sqrt(1 + tau * tau);
        double xip = Math.atan2(taup, cosl);
        double etap = asinh(sinl / Math.sqrt(taup * taup + cosl * cosl));

        double A = a / (1 + n) * (1 + n2 / 4 + n4 / 64 + n6 / 256);
        double[] al = {0,
                1.0 / 2 * n - 2.0 / 3 * n2 + 5.0 / 16 * n3 + 41.0 / 180 * n4 - 127.0 / 288 * n5 + 7891.0 / 37800 * n6,
                13.0 / 48 * n2 - 3.0 / 5 * n3 + 557.0 / 1440 * n4 + 281.0 / 630 * n5 - 1983433.0 / 1935360 * n6,
                61.0 / 240 * n3 - 103.0 / 140 * n4 + 15061.0 / 26880 * n5 + 167603.0 / 181440 * n6,
                49561.0 / 161280 * n4 - 179.0 / 168 * n5 + 6601661.0 / 7257600 * n6,
                34729.0 / 80640 * n5 - 3418889.0 / 1995840 * n6,
                212378941.0 / 319334400 * n6};

        double xi = xip, eta = etap;
        for (int j = 1; j <= 6; j++) {
            xi += al[j] * Math.sin(2 * j * xip) * Math.cosh(2 * j * etap);
            eta += al[j] * Math.cos(2 * j * xip) * Math.sinh(2 * j * etap);
        }
        double x = k0 * A * eta + 500000.0;
        double y = k0 * A * xi;
        if (y < 0) y += 10000000.0;

        u.zone = zone; u.band = band; u.e = x; u.n = y;
        return u;
    }

    public static String utm(double lat, double lon) {
        Utm u = toUtm(lat, lon);
        if (!u.valid) return "Not available (polar region)";
        return String.format(Locale.US, "%d%c  %d mE  %d mN", u.zone, u.band, Math.round(u.e), Math.round(u.n));
    }

    // ------------------------------------------------------------------- MGRS
    private static final String[] E100K = {"ABCDEFGH", "JKLMNPQR", "STUVWXYZ"};
    private static final String[] N100K = {"ABCDEFGHJKLMNPQRSTUV", "FGHJKLMNPQRSTUVABCDE"};

    public static String mgrs(double lat, double lon) {
        Utm u = toUtm(lat, lon);
        if (!u.valid) return "Not available (polar region)";
        long e = Math.round(u.e), n = Math.round(u.n);
        int col = (int) (e / 100000);
        int row = (int) ((n / 100000) % 20);
        char c1 = E100K[(u.zone - 1) % 3].charAt(Math.max(0, Math.min(7, col - 1)));
        char c2 = N100K[(u.zone - 1) % 2].charAt(row);
        return String.format(Locale.US, "%d%c %c%c %05d %05d", u.zone, u.band, c1, c2, e % 100000, n % 100000);
    }

    // ------------------------------------------------------------ Web Mercator
    public static String mercator(double lat, double lon) {
        double R = 6378137.0;
        double la = Math.max(-85.05112878, Math.min(85.05112878, lat));
        double x = R * Math.toRadians(lon) + 0.0;
        double y = R * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(la) / 2)) + 0.0;
        return String.format(Locale.US, "X %.2f m,  Y %.2f m", x, y);
    }

    // -------------------------------------------------------------- Maidenhead
    public static String maidenhead(double lat, double lon) {
        double lo = ((lon + 540.0) % 360.0) - 180.0 + 180.0;
        double la = Math.max(-90, Math.min(89.999999, lat)) + 90.0;
        StringBuilder sb = new StringBuilder();
        int f1 = (int) Math.floor(lo / 20), f2 = (int) Math.floor(la / 10);
        sb.append((char) ('A' + f1)).append((char) ('A' + f2));
        lo -= f1 * 20; la -= f2 * 10;
        int s1 = (int) Math.floor(lo / 2), s2 = (int) Math.floor(la / 1);
        sb.append(s1).append(s2);
        lo -= s1 * 2; la -= s2 * 1;
        int u1 = (int) Math.floor(lo / (2.0 / 24)), u2 = (int) Math.floor(la / (1.0 / 24));
        sb.append((char) ('a' + u1)).append((char) ('a' + u2));
        lo -= u1 * (2.0 / 24); la -= u2 * (1.0 / 24);
        int x1 = (int) Math.floor(lo / (2.0 / 240)), x2 = (int) Math.floor(la / (1.0 / 240));
        sb.append(Math.min(9, x1)).append(Math.min(9, x2));
        return sb.toString();
    }

    // --------------------------------------------------------------- Plus Code
    private static final String OLC = "23456789CFGHJMPQRVWX";

    /** Full 10-digit Open Location Code (about 14 m x 14 m). */
    public static String plusCode(double lat, double lon) {
        double la = Math.max(-90, Math.min(90, lat));
        double lo = ((lon + 540.0) % 360.0) - 180.0;
        long latVal = (long) Math.floor((la + 90.0) * 8000.0 + 1e-6);
        long lonVal = (long) Math.floor((lo + 180.0) * 8000.0 + 1e-6);
        if (latVal >= 1440000L) latVal = 1439999L;
        char[] code = new char[10];
        for (int i = 4; i >= 0; i--) {
            code[i * 2 + 1] = OLC.charAt((int) (lonVal % 20));
            code[i * 2] = OLC.charAt((int) (latVal % 20));
            latVal /= 20; lonVal /= 20;
        }
        return new String(code, 0, 8) + "+" + new String(code, 8, 2);
    }

    // ----------------------------------------------------------------- Geohash
    private static final String B32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    public static String geohash(double lat, double lon, int len) {
        double[] latR = {-90, 90}, lonR = {-180, 180};
        StringBuilder sb = new StringBuilder();
        boolean even = true;
        int bit = 0, ch = 0;
        while (sb.length() < len) {
            double[] r = even ? lonR : latR;
            double v = even ? lon : lat;
            double mid = (r[0] + r[1]) / 2;
            if (v >= mid) { ch = (ch << 1) | 1; r[0] = mid; } else { ch = ch << 1; r[1] = mid; }
            even = !even;
            if (++bit == 5) { sb.append(B32.charAt(ch)); bit = 0; ch = 0; }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------- links
    public static String googleMaps(double lat, double lon) {
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
    }

    public static String geoUri(double lat, double lon) {
        return String.format(Locale.US, "geo:%.6f,%.6f", lat, lon);
    }

    public static String osm(double lat, double lon) {
        return String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f", lat, lon, lat, lon);
    }

    // test harness (not used by the app)
    public static void main(String[] a) {
        double[][] pts = {{48.8584, 2.2945}, {37.4220, -122.0841}, {-33.8568, 151.2153}, {31.48, 72.52}, {0.0, 0.0}, {-54.8, -68.3}};
        for (double[] p : pts) {
            System.out.println(dd(p[0], p[1]));
            System.out.println("  " + ddHemi(p[0], p[1]));
            System.out.println("  " + dms(p[0], p[1]));
            System.out.println("  " + ddm(p[0], p[1]));
            System.out.println("  UTM " + utm(p[0], p[1]));
            System.out.println("  MGRS " + mgrs(p[0], p[1]));
            System.out.println("  MERC " + mercator(p[0], p[1]));
            System.out.println("  MAID " + maidenhead(p[0], p[1]));
            System.out.println("  PLUS " + plusCode(p[0], p[1]));
            System.out.println("  GEOH " + geohash(p[0], p[1], 10));
        }
        System.out.println("Munich maidenhead (expect JN58td): " + maidenhead(48.14666, 11.60833));
        System.out.println("Geohash 57.64911,10.40744 (expect u4pruydqqv): " + geohash(57.64911, 10.40744, 10));
    }
}
