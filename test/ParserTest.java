import io.github.vivibibi30.msgbus.MessageParser;
import io.github.vivibibi30.msgbus.Msg;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * 解析器自测。两种模式：
 *   无参数   → 跑内置断言（不用设备）
 *   一个路径 → 集成模式：喂 `for f in *.msg; do echo "@@@"$f"; cat "$f"; done` 的输出
 */
public class ParserTest {

    private static int fail = 0;

    public static void main(String[] a) throws Exception {
        if (a.length == 1) {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(a[0]), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = r.readLine()) != null) sb.append(ln).append('\n');
            r.close();
            List<Msg> m = MessageParser.parse(sb.toString());
            System.out.println("条数 " + m.size());
            for (Msg x : m) {
                System.out.println("  " + x.from + " | refs=" + x.refs
                        + " | covers=" + x.covers + " | body=" + x.body.replace('\n', ' '));
            }
            return;
        }

        String raw =
                "@@@20261113-100000-1-yg-ime-1.msg\n"
                        + "from: yg-ime\n"
                        + "scope: group\n"
                        + "refs: 6e996a5\n"
                        + "time: 2026-11-13T10:00:00+0000\n"
                        + "subject: ime 归档\n"
                        + "\n"
                        + "样本 6 个。\n"
                        + "refs: 这行在正文里，不该被当字段\n"
                        + "\n"
                        + "@@@20261113-100100-2-yg-ime-2.msg\n"
                        + "from: yg-ime\n"
                        + "scope: digest\n"
                        + "refs: -\n"
                        + "covers: 3\n"
                        + "upto: 20261113-100000-1-yg-ime-1\n"
                        + "time: 2026-11-13T10:01:00+0000\n"
                        + "subject: 汇总\n"
                        + "\n"
                        + "· 第一条\n"
                        + "· 第二条\n"
                        + "@@@20261113-100200-3-yg-hp-3.msg\n"
                        + "from: yg-hyperpguard\n"
                        + "scope: group\n"
                        + "refs: -\n"
                        + "time: 2026-11-13T10:02:00+0000\n"
                        + "\n"
                        + "没有标题的一条。\n";

        List<Msg> m = MessageParser.parse(raw);
        check("条数", "3", "" + m.size());

        Msg x = m.get(0);
        check("from", "yg-ime", x.from);
        check("id", "20261113-100000-1-yg-ime-1", x.id());
        check("refs", "6e996a5", x.refs);
        check("subject", "ime 归档", x.subject);
        check("covers 空", "", x.covers);
        check("isDigest false", "false", "" + x.isDigest());
        check("正文含 refs 行", "样本 6 个。\nrefs: 这行在正文里，不该被当字段", x.body);

        Msg d = m.get(1);
        check("summary covers", "3", d.covers);
        check("isDigest true", "true", "" + d.isDigest());
        check("summary 正文行数", "2", "" + d.body.split("\n").length);
        check("summary refs 不串", "-", d.refs);

        Msg n = m.get(2);
        check("无标题 subject", "", n.subject);
        check("无标题正文", "没有标题的一条。", n.body);
        check("firstLine", "没有标题的一条。", n.firstLine());

        List<Msg> e = MessageParser.parse("@@@only.msg\nfrom: x\n\n");
        check("空正文条数", "1", "" + e.size());
        check("空正文", "", e.get(0).body);
        check("空输入", "0", "" + MessageParser.parse("").size());

        // 回归：扩展字段（kind:/re:）在头部里必须被忽略，不能把后续头部推进正文
        String intro = "@@@i1.msg\nfrom: yg-x\nscope: group\nrefs: -\nkind: intro\n"
                + "time: 2026-11-13T19:37:12+0000\nsubject: 自我介绍\n\n我是 yg-x。\n职责：测试";
        java.util.List<Msg> im = MessageParser.parse(intro);
        check("扩展字段不泄入正文", "我是 yg-x。\n职责：测试", im.get(0).body);
        check("扩展字段之后 subject 仍解析", "自我介绍", im.get(0).subject);
        check("扩展字段之后 time 仍解析", "2026-11-13T19:37:12+0000", im.get(0).time);

        // 附件字段：不进正文，单独解析
        String attMsg = "@@@a1.msg\nfrom: yg-x\nscope: group\nrefs: -\n"
                + "attachments: /sdcard/a.png,/sdcard/b.png\ntime: 2026-11-14T01:00:00+0000\n\n看这两张图";
        java.util.List<Msg> am = MessageParser.parse(attMsg);
        check("附件路径", "/sdcard/a.png,/sdcard/b.png", am.get(0).attachments);
        check("附件不进正文", "看这两张图", am.get(0).body);

        // Store 的共用拼装（两个通道必须产出同一格式，spec §3）
        String[] rb = io.github.vivibibi30.msgbus.Store.splitRefs("refs: 6e996a5\n正文一行");
        check("splitRefs refs", "6e996a5", rb[0]);
        check("splitRefs body", "正文一行", rb[1]);
        String[] rb2 = io.github.vivibibi30.msgbus.Store.splitRefs("没有 refs 的正文");
        check("splitRefs 默认 -", "-", rb2[0]);
        check("splitRefs 正文原样", "没有 refs 的正文", rb2[1]);
        String msg = io.github.vivibibi30.msgbus.Store.messageText("abc", "内容");
        check("messageText from", "机主", io.github.vivibibi30.msgbus.Store.header(msg, "from"));
        check("messageText scope", "group", io.github.vivibibi30.msgbus.Store.header(msg, "scope"));
        check("messageText refs", "abc", io.github.vivibibi30.msgbus.Store.header(msg, "refs"));
        // 生成给别人的命令：不能留占位符、引号要转义
        String cmd = io.github.vivibibi30.msgbus.CmdBuilder.build(
                "/data/local/tmp/msgbus", "/x/msg.sh", "yg-文件管理", "6e996a5", "ime 样本归档完成");
        check("cmd 含 refs", "true", "" + cmd.contains("refs: 6e996a5"));
        check("cmd 含会话名", "true", "" + cmd.contains("post yg-文件管理"));
        check("cmd 含 new", "true", "" + cmd.contains("new yg-文件管理"));
        check("cmd 含目录", "true", "" + cmd.contains("MSGBUS_DIR=/data/local/tmp/msgbus"));
        check("cmd 无尖括号占位", "false", "" + cmd.contains("<"));
        String cmd2 = io.github.vivibibi30.msgbus.CmdBuilder.build(
                "/d", null, "n", "", "it's ok");
        check("无脚本时给格式说明", "true", "" + cmd2.contains("from: n"));
        String fn = io.github.vivibibi30.msgbus.Store.fileName();
        check("文件名符合 spec §2", "true",
                "" + fn.matches("\\d{8}-\\d{6}-\\d{9}-机主-app\\.msg"));

        System.out.println(fail == 0 ? "全部通过" : (fail + " 项失败"));
        if (fail != 0) System.exit(1);
    }

    private static void check(String name, String want, String got) {
        if (want.equals(got)) {
            System.out.println("  ok   " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name + "：期望 [" + want + "] 实际 [" + got + "]");
        }
    }
}
