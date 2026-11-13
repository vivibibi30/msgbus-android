package io.github.vivibibi30.msgbus;

/** 一条群消息（只读模型，字段与 SPEC.md 的头部一致）。 */
public final class Msg {

    public final String file;     // xxx.msg
    public final String from;
    public final String time;     // ISO8601
    public final String refs;
    public final String subject;
    public final String covers;   // 非空 = 摘要消息，值为被压缩条数
    public final String scope;    // group / digest / direct
    public final String body;

    public Msg(String file, String from, String time, String refs,
               String subject, String covers, String scope, String body) {
        this.file = file;
        this.from = from == null ? "" : from;
        this.time = time == null ? "" : time;
        this.refs = refs == null ? "" : refs;
        this.subject = subject == null ? "" : subject;
        this.covers = covers == null ? "" : covers;
        this.scope = scope == null || scope.isEmpty() ? "group" : scope;
        this.body = body == null ? "" : body;
    }

    /** 文件名去掉 .msg，作为稳定游标（含纳秒，字典序即时间序）。 */
    public String id() {
        return file.endsWith(".msg") ? file.substring(0, file.length() - 4) : file;
    }

    public boolean isDigest() {
        return covers.length() > 0;
    }

    public String firstLine() {
        int i = body.indexOf('\n');
        String s = i < 0 ? body : body.substring(0, i);
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
