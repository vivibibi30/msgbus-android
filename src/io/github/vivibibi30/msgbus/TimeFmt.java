package io.github.vivibibi30.msgbus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 消息里的 time 是带偏移的 ISO8601（协议统一写 +0000），显示器要换成设备本地时区，
 * 否则手机会把 04:18 CST 显示成 20:18（前一天）。纯 Java，可脱离设备测。
 */
public final class TimeFmt {

    private static final String IN = "yyyy-MM-dd'T'HH:mm:ssZ";

    private TimeFmt() {
    }

    public static Date parse(String iso) {
        if (iso == null) return null;
        try {
            SimpleDateFormat f = new SimpleDateFormat(IN, Locale.US);
            f.setLenient(false);
            return f.parse(iso.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String local(String iso, String pattern, String fallback) {
        Date d = parse(iso);
        if (d == null) return fallback;
        SimpleDateFormat o = new SimpleDateFormat(pattern, Locale.getDefault());
        o.setTimeZone(TimeZone.getDefault());
        return o.format(d);
    }

    /** 气泡里的「yyyy-MM-dd HH:mm」 */
    public static String display(String iso) {
        String fb = iso == null ? "" : (iso.length() >= 16 ? iso.substring(0, 16).replace('T', ' ') : iso);
        return local(iso, "yyyy-MM-dd HH:mm", fb);
    }

    /** 气泡右下角的「HH:mm」 */
    public static String clock(String iso) {
        String fb = iso == null ? "" : (iso.length() >= 16 ? iso.substring(11, 16) : iso);
        return local(iso, "HH:mm", fb);
    }

    /** 日期分组用的「yyyy-MM-dd」（按本地日期） */
    public static String day(String iso) {
        String fb = iso == null ? "" : (iso.length() >= 10 ? iso.substring(0, 10) : "");
        return local(iso, "yyyy-MM-dd", fb);
    }
}
