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
                if (ln.startsWith("from: ")) from = ln.substring(6).trim();
                else if (ln.startsWith("time: ")) time = ln.substring(6).trim();
                else if (ln.startsWith("refs: ")) refs = ln.substring(6).trim();
                else if (ln.startsWith("subject: ")) subject = ln.substring(9).trim();
                else if (ln.startsWith("covers: ")) covers = ln.substring(8).trim();
                else if (ln.startsWith("scope: ")) scope = ln.substring(7).trim();
                else if (ln.startsWith("to: ") || ln.startsWith("upto: ")) {
                    // 点对点收件人、作用域、压缩上界：界面不用
                } else {
                    inBody = true;
                    body.append(ln).append('\n');
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
