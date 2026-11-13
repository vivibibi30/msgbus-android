package io.github.vivibibi30.msgbus;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * root 通道：直接 su -c 读写一个「消息目录」（目录由用户在设置里指定），
 * 不依赖任何外部脚本或某个 App 的目录结构；格式按 SPEC.md 自己拼装。
 * 没有 root 时用 SafStore（同一份 SPEC）。
 */
public final class MsgStore implements Store {

    /** 机器无关的默认目录；用户可在 ⋮ → 消息目录 里改。 */
    public static final String DEFAULT_DIR = "/data/local/tmp/msgbus";

    private final Context ctx;
    private final String dir;

    public MsgStore(Context c, String dir) {
        this.ctx = c;
        this.dir = stripSlash(dir);
    }

    public static boolean suAvailable() {
        try {
            return sh("id -u 2>/dev/null").trim().equals("0");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean dirExists(String d) {
        try {
            return sh("[ -d " + q(d) + " ] && echo yes").trim().equals("yes");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String mode() {
        return "root · " + dir;
    }

    private String spool() {
        return dir + "/spool";
    }

    @Override
    public List<Msg> read() throws IOException {
        String out = sh("cd " + q(spool()) + " 2>/dev/null || exit 0; "
                + "for f in *.msg; do echo \"@@@$f\"; cat \"$f\"; done");
        return MessageParser.parse(out);
    }

    @Override
    public void post(String text) throws IOException {
        String[] rb = Store.splitRefs(text);
        if (rb[1].isEmpty()) throw new IOException("refs 之外没有正文");
        File tmp = new File(ctx.getCacheDir(), "post.txt");
        writeFile(tmp, Store.messageText(rb[0], rb[1]));
        String name = Store.fileName();
        String r = sh("mkdir -p " + q(spool())
                + " && cp " + q(tmp.getAbsolutePath()) + " " + q(spool() + "/" + name)
                + " && chmod 644 " + q(spool() + "/" + name) + " && echo OK");
        if (!r.contains("OK")) throw new IOException(fail("post", r));
        setMember(ME, "active", "-");
    }

    @Override
    public void digest(String title, String body) throws IOException {
        List<Msg> all = read();
        StringBuilder ids = new StringBuilder();
        int n = 0;
        String upto = "";
        for (Msg m : all) {
            if (!"group".equals(m.scope) && !"digest".equals(m.scope)) continue;
            n++;
            upto = m.id();
            ids.append(' ').append(q(m.id()));
        }
        if (n == 0) throw new IOException("群里没有需要压缩的群消息");

        File tmp = new File(ctx.getCacheDir(), "digest.txt");
        writeFile(tmp, body);
        String name = Store.fileName();
        String digestText = "from: " + ME + "\nscope: digest\nrefs: -\ncovers: " + n
                + "\nupto: " + upto + "\ntime: " + Store.isoNow()
                + "\nsubject: " + title.replace("\n", " ") + "\n\n" + body + "\n";
        writeFile(tmp, digestText);
        String r = sh("mkdir -p " + q(spool()) + " " + q(dir + "/archive/group")
                + " && cp " + q(tmp.getAbsolutePath()) + " " + q(spool() + "/" + name)
                + " && chmod 644 " + q(spool() + "/" + name) + " && echo OK");
        if (!r.contains("OK")) throw new IOException(fail("digest", r));
        sh("cd " + q(spool()) + " && for f in" + ids + "; do mv \"$f.msg\" "
                + q(dir + "/archive/group") + "/ 2>/dev/null || true; done");
    }

    @Override
    public String members() throws IOException {
        String r = sh("cd " + q(dir + "/members") + " 2>/dev/null || exit 0; "
                + "for f in *; do [ -f \"$f\" ] || continue; "
                + "printf '%-14s %-6s %s %s\\n' \"$f\" "
                + "\"$(sed -n 's/^status: //p' \"$f\" | head -1)\" "
                + "\"$(sed -n 's/^updated: //p' \"$f\" | head -1 | cut -c1-16)\" "
                + "\"$(sed -n 's/^note: //p' \"$f\" | head -1)\"; done");
        return r.trim();
    }

    @Override
    public void setMember(String name, String status, String note) throws IOException {
        String safe = safeName(name);
        if (safe.isEmpty()) throw new IOException("会话名为空");
        String text = Store.memberText(status, note.replace("'", ""));
        File tmp = new File(ctx.getCacheDir(), "member.txt");
        writeFile(tmp, text);
        String r = sh("mkdir -p " + q(dir + "/members") + " && cp " + q(tmp.getAbsolutePath())
                + " " + q(dir + "/members/" + safe) + " && echo OK");
        if (!r.contains("OK")) throw new IOException(fail("setMember", r));
    }

    @Override
    public String cursor() throws IOException {
        return sh("tail -1 " + q(dir + "/seen/" + ME) + " 2>/dev/null").trim();
    }

    @Override
    public void setCursor(String id) throws IOException {
        if (id == null || id.isEmpty()) return;
        String safe = id.replace("'", "");
        sh("mkdir -p " + q(dir + "/seen") + " && { grep -qxF '" + safe + "' "
                + q(dir + "/seen/" + ME) + " 2>/dev/null || printf '%s\\n' '" + safe
                + "' >> " + q(dir + "/seen/" + ME) + " ; }");
    }

    @Override
    public List<Msg> readArchived() throws IOException {
        String out = sh("cd " + q(dir + "/archive/group") + " 2>/dev/null || exit 0; "
                + "for f in *.msg; do echo \"@@@$f\"; cat \"$f\"; done");
        return MessageParser.parse(out);
    }

    @Override
    public void delete(String id, boolean archived) throws IOException {
        String base = archived ? dir + "/archive/group" : spool();
        sh("rm -f " + q(base + "/" + safeName(id) + ".msg"));
    }

    @Override
    public void clear(boolean spool, boolean archive) throws IOException {
        if (spool) sh("rm -f " + q(spool()) + "/*.msg");
        if (archive) sh("rm -f " + q(dir + "/archive/group") + "/*.msg");
    }

    // ---------- 内部 ----------

    private static String stripSlash(String p) {
        String s = p == null ? "" : p.trim();
        while (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String safeName(String n) {
        return n.replace("/", "_").replace("\\", "_").replace("'", "").trim();
    }

    private static String fail(String op, String out) {
        String o = out == null ? "" : out.trim();
        if (o.isEmpty()) o = "无输出（su 未授权或目录不可写？）";
        if (o.length() > 300) o = o.substring(0, 300) + "…";
        return op + " 失败：" + o;
    }

    private static String q(String s) {
        return "'" + (s == null ? "" : s.replace("'", "'\\''")) + "'";
    }

    private static void writeFile(File f, String s) throws IOException {
        OutputStream os = new FileOutputStream(f);
        try {
            os.write(s.getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }

    public static String sh(String cmd) throws IOException {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        String out = drain(p.getInputStream());
        String err = drain(p.getErrorStream());
        try {
            p.waitFor();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return out.isEmpty() ? err : out;
    }

    private static String drain(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }
}
