package io.github.vivibibi30.msgbus;

/** 把「别人该怎么发」拼成一条可直接粘贴的 shell 命令（纯 Java，可脱离设备测）。 */
public final class CmdBuilder {

    private CmdBuilder() {
    }

    /**
     * @param dir    消息目录（MSGBUS_DIR）
     * @param script msg.sh 的绝对路径；null 表示没找到脚本，退回「直接写文件」说明
     * @param name   会话名（写进 from:）
     * @param refs   commit 或文件路径，空则填 -
     * @param body   正文（一句话结论）
     */
    public static String build(String dir, String script, String name, String refs, String body) {
        String r = refs == null || refs.trim().isEmpty() ? "-" : refs.trim();
        String b = body == null || body.trim().isEmpty() ? "（这里写一句话结论）" : body.trim();
        String esc = b.replace("'", "'\\''");
        if (script != null) {
            return "printf 'refs: " + r + "\\n" + esc + "\\n' \\\n"
                    + "  | MSGBUS_DIR=" + dir + " sh " + script + " post " + name + "\n"
                    + "MSGBUS_DIR=" + dir + " sh " + script + " new " + name;
        }
        return "（这台机器上没找到 msg.sh）\n"
                + "把下面这段写成 " + dir + "/spool/<秒>-<纳秒>-" + name + "-1.msg：\n"
                + "from: " + name + "\nscope: group\nrefs: " + r
                + "\ntime: <ISO8601>\n\n" + b;
    }
}
