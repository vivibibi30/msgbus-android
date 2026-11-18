package io.github.vivibibi30.msgbus;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SAF 通道：用户在设置里选一个目录（ACTION_OPEN_DOCUMENT_TREE + 持久授权），
 * 直读直写 spool/seen/archive/members，不需要 root。
 * 除了系统 DocumentsContract 没有别的依赖；写文件按 SPEC.md 自行拼装。
 */
public final class SafStore implements Store {

    private final ContentResolver cr;
    private final Uri tree;
    private final String rootDoc;

    public SafStore(Context c, Uri tree) {
        this.cr = c.getContentResolver();
        this.tree = tree;
        this.rootDoc = DocumentsContract.getTreeDocumentId(tree);
    }

    @Override
    public String mode() {
        return "SAF · " + tree;
    }

    // ---------- 读 ----------

    @Override
    public List<Msg> read() throws IOException {
        String spool = findChild(rootDoc, "spool");
        if (spool == null) return new ArrayList<Msg>();   // 只读，不顺手建目录
        StringBuilder sb = new StringBuilder();
        for (String[] f : children(spool)) {
            if (!f[1].endsWith(".msg")) continue;
            sb.append("@@@").append(f[1]).append('\n').append(read(f[0])).append('\n');
        }
        return MessageParser.parse(sb.toString());
    }

    @Override
    public String cursor() throws IOException {
        String seen = findChild(rootDoc, "seen");
        if (seen == null) return "";
        String me = findChild(seen, ME);
        if (me == null) return "";
        String all = read(me).trim();
        if (all.isEmpty()) return "";
        String[] lines = all.split("\n");
        return lines[lines.length - 1].trim();
    }

    @Override
    public String members() throws IOException {
        String dir = findChild(rootDoc, "members");
        if (dir == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String[] f : children(dir)) {
            String t = read(f[0]);
            String status = Store.header(t, "status");
            String updated = Store.header(t, "updated");
            String note = Store.header(t, "note");
            if (updated.length() > 16) updated = updated.substring(0, 16);
            sb.append(f[1]).append('\t').append(status).append('\t')
              .append(updated).append('\t').append(note).append('\n');
        }
        return sb.toString().trim();
    }

    // ---------- 写 ----------

    @Override
    public void post(String text, String attachments) throws IOException {
        String[] rb = Store.splitRefs(text);
        if (rb[1].isEmpty()) throw new IOException("refs 之外没有正文");
        String spool = ensureDir(rootDoc, "spool");
        createIn(spool, Store.fileName(), Store.messageText(rb[0], rb[1], attachments));
        setMember(ME, "active", "-");
    }

    @Override
    public void digest(String title, String body) throws IOException {
        String spool = ensureDir(rootDoc, "spool");
        List<String[]> kept = new ArrayList<String[]>();
        for (String[] f : children(spool)) {
            if (!f[1].endsWith(".msg")) continue;
            String content = read(f[0]);
            String scope = Store.header(content, "scope");
            if ("group".equals(scope)) kept.add(new String[]{f[0], f[1]});
        }
        if (kept.isEmpty()) throw new IOException("群里没有需要压缩的群消息");
        Collections.sort(kept, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return a[1].compareTo(b[1]);
            }
        });
        String upto = kept.get(kept.size() - 1)[1];
        String text = "from: " + ME + "\nscope: digest\nrefs: -\ncovers: " + kept.size()
                + "\nupto: " + (upto.endsWith(".msg") ? upto.substring(0, upto.length() - 4) : upto)
                + "\ntime: " + Store.isoNow() + "\nsubject: " + title.replace("\n", " ")
                + "\n\n" + body + "\n";
        createIn(spool, Store.fileName(), text);

        String arch = ensureDir(rootDoc, "archive");
        String group = ensureDir(arch, "group");
        for (String[] f : kept) {
            createIn(group, f[1], read(f[0]));
            delete(f[0]);
        }
    }

    @Override
    public void setMember(String name, String status, String note) throws IOException {
        String safe = name.replace("/", "_").replace("\\", "_").trim();
        if (safe.isEmpty()) throw new IOException("会话名为空");
        String dir = ensureDir(rootDoc, "members");
        String existing = findChild(dir, safe);
        String text = Store.memberText(status, note);
        if (existing != null) write(existing, text);
        else createIn(dir, safe, text);
    }

    @Override
    public void deleteMember(String name) throws IOException {
        String dir = findChild(rootDoc, "members");
        if (dir == null) return;
        String safe = name.replace("/", "_").replace("\\", "_").trim();
        String doc = findChild(dir, safe);
        if (doc != null) delete(doc);
    }

    @Override
    public void setCursor(String id) throws IOException {
        if (id == null || id.isEmpty()) return;
        String seen = ensureDir(rootDoc, "seen");
        String me = findChild(seen, ME);
        String old = me == null ? "" : read(me);
        if (old.contains(id)) return;
        String text = old + (old.isEmpty() || old.endsWith("\n") ? "" : "\n") + id + "\n";
        if (me != null) write(me, text);
        else createIn(seen, ME, text);
    }

    @Override
    public List<Msg> readArchived() throws IOException {
        String arch = findChild(rootDoc, "archive");
        if (arch == null) return new ArrayList<Msg>();
        String group = findChild(arch, "group");
        if (group == null) return new ArrayList<Msg>();
        StringBuilder sb = new StringBuilder();
        for (String[] f : children(group)) {
            if (!f[1].endsWith(".msg")) continue;
            sb.append("@@@").append(f[1]).append('\n').append(read(f[0])).append('\n');
        }
        return MessageParser.parse(sb.toString());
    }

    @Override
    public void delete(String id, boolean archived) throws IOException {
        String dir;
        if (archived) {
            String a = findChild(rootDoc, "archive");
            if (a == null) return;
            dir = findChild(a, "group");
            if (dir == null) return;
        } else {
            dir = findChild(rootDoc, "spool");
            if (dir == null) return;
        }
        String name = id.endsWith(".msg") ? id : id + ".msg";
        String doc = findChild(dir, name);
        if (doc != null) delete(doc);
    }

    @Override
    public void clear(boolean spool, boolean archive) throws IOException {
        if (spool) {
            String d = findChild(rootDoc, "spool");
            if (d != null) for (String[] f : children(d)) if (f[1].endsWith(".msg")) delete(f[0]);
        }
        if (archive) {
            String a = findChild(rootDoc, "archive");
            String d = a == null ? null : findChild(a, "group");
            if (d != null) for (String[] f : children(d)) if (f[1].endsWith(".msg")) delete(f[0]);
        }
    }

    // ---------- DocumentsContract 辅助 ----------

    private Uri docUri(String docId) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, docId);
    }

    private List<String[]> children(String parentDoc) throws IOException {
        List<String[]> out = new ArrayList<String[]>();
        Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDoc);
        Cursor c = null;
        try {
            c = cr.query(uri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c == null) return out;
            while (c.moveToNext()) {
                out.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
            }
        } finally {
            if (c != null) c.close();
        }
        Collections.sort(out, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return a[1].compareTo(b[1]);
            }
        });
        return out;
    }

    private String findChild(String parentDoc, String name) throws IOException {
        for (String[] f : children(parentDoc)) {
            if (name.equals(f[1])) return f[0];
        }
        return null;
    }

    private String ensureDir(String parentDoc, String name) throws IOException {
        String id = findChild(parentDoc, name);
        if (id != null) return id;
        Uri created = DocumentsContract.createDocument(cr, docUri(parentDoc),
                DocumentsContract.Document.MIME_TYPE_DIR, name);
        if (created == null) throw new IOException("无法创建目录 " + name);
        return DocumentsContract.getDocumentId(created);
    }

    private void createIn(String parentDoc, String name, String text) throws IOException {
        Uri created = DocumentsContract.createDocument(cr, docUri(parentDoc), "text/plain", name);
        if (created == null) throw new IOException("无法创建文件 " + name);
        writeUri(created, text);
    }

    private String read(String docId) throws IOException {
        InputStream is = cr.openInputStream(docUri(docId));
        if (is == null) throw new IOException("读不到 " + docId);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            is.close();
        }
    }

    private void write(String docId, String text) throws IOException {
        writeUri(docUri(docId), text);
    }

    private void writeUri(Uri uri, String text) throws IOException {
        OutputStream os = cr.openOutputStream(uri, "wt");
        if (os == null) throw new IOException("写不进去：" + uri);
        try {
            os.write(text.getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }

    private void delete(String docId) throws IOException {
        DocumentsContract.deleteDocument(cr, docUri(docId));
    }
}
