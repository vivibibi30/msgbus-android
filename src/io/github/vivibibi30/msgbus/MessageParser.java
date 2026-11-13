package io.github.vivibibi30.msgbus;

import java.util.ArrayList;
import java.util.List;

/**
 * msgbus 消息解析（纯 Java，无 Android 依赖，可用 JVM 直接跑 test/ParserTest）。
 * 输入是 `for f in *.msg; do echo "@@@$f"; cat "$f"; done` 的输出。
 */
public final class MessageParser {

    private MessageParser() {
    }

    public static List<Msg> parse(String raw) {
        List<Msg> out = new ArrayList<Msg>();
        if (raw == null) return out;
        String[] lines = raw.split("\n", -1);
        String file = null;
        String from = "", time = "", refs = "", subject = "", covers = "", scope = "group";
        StringBuilder body = null;
        boolean inBody = false;

        for (String ln : lines) {
            if (ln.startsWith("@@@")) {
                if (file != null) out.add(new Msg(file, from, time, refs, subject, covers, scope, trim(body)));
                file = ln.substring(3);
                from = time = refs = subject = covers = "";
                scope = "group";
                body = new StringBuilder();
                inBody = false;
                continue;
            }
            if (file == null) continue;
            if (!inBody) {
                if (ln.length() == 0) {
                    inBody = true;
                    continue;
                }
                // 先判断是不是头部行：`key: value`（key 不含空格）。不认识的键也必须忽略，
                // 否则像 kind:/re: 这种扩展字段会把后续所有头部推进正文（0.16.0 实测出现过）。
                int colon = ln.indexOf(": ");
                boolean header = colon > 0 && ln.lastIndexOf(' ', colon) < 0;
                if (!header) {
                    inBody = true;
                    body.append(ln).append('\n');
                    continue;
                }
                if (ln.startsWith("from: ")) from = ln.substring(6).trim();
                else if (ln.startsWith("time: ")) time = ln.substring(6).trim();
                else if (ln.startsWith("refs: ")) refs = ln.substring(6).trim();
                else if (ln.startsWith("subject: ")) subject = ln.substring(9).trim();
                else if (ln.startsWith("covers: ")) covers = ln.substring(8).trim();
                else if (ln.startsWith("scope: ")) scope = ln.substring(7).trim();
                else {
                    // to:/upto:/kind:/re:/x-* 等：按 SPEC §0.2 忽略，但不算正文开始
                }
            } else {
                body.append(ln).append('\n');
            }
        }
        if (file != null) out.add(new Msg(file, from, time, refs, subject, covers, scope, trim(body)));
        return out;
    }

    private static String trim(StringBuilder b) {
        return b == null ? "" : b.toString().trim();
    }
}
