package io.github.vivibibi30.msgbus;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * 选通道。两个实现都只依赖「一个消息目录」，与具体 App、机器路径无关：
 *   root：su 直接读写用户指定的绝对路径
 *   SAF ：用户选的目录，DocumentsContract 直读直写
 */
public final class Stores {

    public static final String PREF = "msgbus";
    private static final String KEY_ROOT_DIR = "root_dir";
    private static final String KEY_TREE_URI = "tree_uri";
    private static final String KEY_MODE = "mode";

    private Stores() {
    }

    public static String rootDir(SharedPreferences sp) {
        String d = sp.getString(KEY_ROOT_DIR, "");
        return d.isEmpty() ? MsgStore.DEFAULT_DIR : d;
    }

    public static Store get(Context c, SharedPreferences sp) {
        String mode = sp.getString(KEY_MODE, "auto");
        String uri = sp.getString(KEY_TREE_URI, "");
        boolean su = MsgStore.suAvailable();
        String dir = rootDir(sp);

        if ("saf".equals(mode) && !uri.isEmpty()) return new SafStore(c, Uri.parse(uri));
        if ("root".equals(mode)) return su ? new MsgStore(c, dir) : null;
        if (su && MsgStore.dirExists(dir)) return new MsgStore(c, dir);
        if (!uri.isEmpty()) return new SafStore(c, Uri.parse(uri));
        if (su) return new MsgStore(c, dir);
        return null;
    }

    public static String describe(SharedPreferences sp) {
        String mode = sp.getString(KEY_MODE, "auto");
        String uri = sp.getString(KEY_TREE_URI, "");
        if ("root".equals(mode)) return "root · " + rootDir(sp);
        if ("saf".equals(mode)) return "SAF · " + uri;
        return "自动 · " + rootDir(sp) + (uri.isEmpty() ? "" : " / SAF " + uri);
    }
}
