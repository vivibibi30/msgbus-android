package io.github.vivibibi30.msgbus;

import java.util.ArrayList;
import java.util.List;

/**
 * members 输出解析（纯 Java，可脱离设备测）。
 * 新格式：`名字<TAB>状态<TAB>更新时间<TAB>说明`（名字可含空格，不能用空格切）。
 * 兼容旧格式（空格对齐）：名字取到 " active " / " left " 之前为止。
 */
public final class Members {

    private Members() {
    }

    /** 每项 {name, status, updated, note}。 */
    public static List<String[]> parse(String raw) {
        List<String[]> out = new ArrayList<String[]>();
        if (raw == null) return out;
        for (String ln : raw.split("\n")) {
            if (ln.trim().isEmpty()) continue;
            String[] f = ln.split("\t", -1);
            if (f.length >= 2) {
                out.add(new String[]{f[0], f[1], f.length > 2 ? f[2] : "",
                        f.length > 3 ? f[3] : ""});
                continue;
            }
            // 旧格式兜底：以 " active " / " left " 为分界，名字保留原样（可能含空格）
            int i = ln.indexOf(" active ");
            String status = "active";
            if (i < 0) {
                i = ln.indexOf(" left ");
                status = "left";
            }
            if (i > 0) {
                String name = ln.substring(0, i).trim();
                String rest = ln.substring(i + status.length() + 2).trim();
                String updated = "";
                String note = "";
                int sp = rest.indexOf(' ');
                if (sp > 0) {
                    updated = rest.substring(0, sp);
                    note = rest.substring(sp + 1).trim();
                } else {
                    updated = rest;
                }
                out.add(new String[]{name, status, updated, note});
            } else {
                out.add(new String[]{ln.trim(), "", "", ""});
            }
        }
        return out;
    }
}
