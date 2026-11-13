package io.github.vivibibi30.msgbus;

import java.io.IOException;
import java.util.List;

/**
 * 存储通道抽象。两个实现：
 *   MsgStore   —— root 通道：su 直接读写用户指定的绝对路径
 *   SafStore   —— SAF 通道：用户选的目录，DocumentProvider 直读直写（免 root）
 * 协议见 SPEC.md，两个实现必须互认同一份 spool/seen/members。
 */
public interface Store {

    /** 本机在群里的身份名。 */
    String ME = "机主";

    List<Msg> read() throws IOException;

    /** 发群消息；首行 `refs: …` 由实现解析。 */
    void post(String text) throws IOException;

    /** 把 spool 里现有群消息压成一条摘要（spec §4）。 */
    void digest(String title, String body) throws IOException;

    /** 成员表，每行 `名字 状态 时间 说明`。 */
    String members() throws IOException;

    /** 写 members/<名字>（不改历史消息）。 */
    void setMember(String name, String status, String note) throws IOException;

    /** 本人游标最后一行的 id；空串表示没读过。 */
    String cursor() throws IOException;

    void setCursor(String id) throws IOException;

    /** 显示用：`root · <目录>` / `SAF · content://…` */
    String mode();

    /** 被 digest 压缩掉、移到 archive/group 的原文。 */
    List<Msg> readArchived() throws IOException;

    /** 删一条消息（archived=true 表示从归档里删）。 */
    void delete(String id, boolean archived) throws IOException;

    /** 清空消息 / 归档，目录本身保留。 */
    void clear(boolean spool, boolean archive) throws IOException;

    // ---------- 两个实现共用的消息文本拼装 ----------

    String ISO = "yyyy-MM-dd'T'HH:mm:ssZ";

    /** 文件名即 id：秒 + 纳秒 + 会话名 + 固定后缀（spec §2）。 */
    static String fileName() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        long ns = System.nanoTime() % 1000000000L;
        return f.format(new java.util.Date()) + "-" + String.format(java.util.Locale.US, "%09d", ns)
                + "-" + ME + "-app.msg";
    }

    static String isoNow() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(ISO, java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date());
    }

    /** 把输入拆成 refs + 正文（首行 `refs:` 才算）。 */
    static String[] splitRefs(String text) {
        String refs = "-";
        String body = text == null ? "" : text.trim();
        String[] lns = body.split("\n", 2);
        if (lns[0].startsWith("refs:")) {
            String r = lns[0].substring(5).trim();
            refs = r.isEmpty() ? "-" : r;
            body = lns.length > 1 ? lns[1].trim() : "";
        }
        return new String[]{refs, body};
    }

    static String messageText(String refs, String body) {
        return "from: " + ME + "\nscope: group\nrefs: " + refs
                + "\ntime: " + isoNow() + "\n\n" + body + "\n";
    }

    static String memberText(String status, String note) {
        return "status: " + status + "\nupdated: " + isoNow() + "\nnote: " + note + "\n";
    }

    /** 从消息文件内容里取某头部字段。 */
    static String header(String content, String key) {
        if (content == null) return "";
        String[] lines = content.split("\n");
        for (String ln : lines) {
            if (ln.length() == 0) break;
            if (ln.startsWith(key + ": ")) return ln.substring(key.length() + 2).trim();
        }
        return "";
    }
}
