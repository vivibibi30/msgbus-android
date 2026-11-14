package io.github.vivibibi30.msgbus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话群主界面。布局全部代码构建（不加 res/，避开 aapt2 -I 的限制）。
 * 顶栏由 App 自己画（清单用 NoActionBar 主题，避免系统标题栏叠加成两个标题）；
 * 沉浸式：内容延伸到状态栏/导航栏下，用 insets 给根布局补 padding；配色分亮暗两套。
 */
@SuppressWarnings("deprecation")
public class MainActivity extends Activity {

    private static final int REQ_NOTIFY = 1;
    private static final int REQ_TREE = 2;

    private static final int[] AVATARS = {
            0xFF3D5AFE, 0xFF00897B, 0xFFEF6C00, 0xFF8E24AA,
            0xFF00838F, 0xFF5D4037, 0xFF546E7A, 0xFFAD1457};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Msg> msgs = new ArrayList<Msg>();

    private ScrollView scroll;
    private LinearLayout feed;
    private TextView subView;
    private EditText input;

    private SharedPreferences prefs;
    private Store store;
    private volatile boolean busy = false;
    private volatile boolean advancedThisRound = false;
    private volatile String cursor = "";
    private int maxBubble;

    // 由 palette() 按亮/暗模式赋值
    private boolean night;
    private int BG, SURFACE, SURFACE_ALT, BUBBLE_OTHER, BUBBLE_ME;
    private int TEXT, ME_TEXT, SUB, ACCENT, CHIP, DIVIDER, HINT;

    @Override
    protected void onCreate(Bundle b) {
        // 主题必须在 super.onCreate 之前定：亮/暗各一套 NoActionBar（清单里不能写 Theme.Material.*，
        // 链接用的 android-api16.jar 不认识；NoActionBar 变体从 API 14 就有）
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);   // 双保险
        palette();
        prefs = getSharedPreferences(Stores.PREF, Context.MODE_PRIVATE);
        maxBubble = (int) (getResources().getDisplayMetrics().widthPixels * 0.70f);

        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(BG));
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            // 30+ 自己接管 insets（系统栏 + 输入法）；30 以下交给 adjustResize，避免双重 padding
            w.setDecorFitsSystemWindows(false);
        }
        statusBarIcons();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(appBar());

        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(true);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feed.setPadding(dp(12), dp(4), dp(12), dp(10));
        scroll.addView(feed, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        root.addView(inputBar());
        setContentView(root);
        applySystemBars(root);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
        }
        load();
        if (!prefs.getBoolean("onboarded", false)) onboarding();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PollService.appVisible = true;
        advancedThisRound = false;
        load();
    }

    @Override
    protected void onPause() {
        PollService.appVisible = false;
        super.onPause();
    }

    // ---------- 配色 ----------

    private void palette() {
        night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            BG = 0xFF0E1116;
            SURFACE = 0xFF151A21;
            SURFACE_ALT = 0xFF1E252F;
            BUBBLE_OTHER = 0xFF1C222B;
            BUBBLE_ME = 0xFF1F3A5F;
            TEXT = 0xFFE7EAEE;
            ME_TEXT = 0xFFEAF2FF;
            SUB = 0xFF97A0AC;
            ACCENT = 0xFF4C8DFF;
            CHIP = 0xFF20262F;
            DIVIDER = 0xFF2A323D;
            HINT = 0xFF6E7681;
        } else {
            BG = 0xFFF4F6F9;
            SURFACE = 0xFFFFFFFF;
            SURFACE_ALT = 0xFFE9EDF2;
            BUBBLE_OTHER = 0xFFE9EDF2;
            BUBBLE_ME = 0xFF3F7FE8;
            TEXT = 0xFF1A1C1F;
            ME_TEXT = 0xFFFFFFFF;
            SUB = 0xFF6B7280;
            ACCENT = 0xFF2F6FE0;
            CHIP = 0xFFE4E9EF;
            DIVIDER = 0xFFD7DCE3;
            HINT = 0xFF9AA1AC;
        }
    }

    /** 沉浸式：状态栏/导航栏透明，图标颜色跟随亮暗模式。 */
    private void statusBarIcons() {
        View decor = getWindow().getDecorView();
        int f = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) {
            if (night) f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (night) f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(f);
    }

    /** 内容延伸到系统栏下面，再按 insets 给根布局补 padding（不遮挡内容）。 */
    private void applySystemBars(final View root) {
        if (Build.VERSION.SDK_INT < 30) return;   // 低版本由 windowSoftInputMode=adjustResize + 系统栏默认行为处理
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                android.graphics.Insets bars = ins.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets ime = ins.getInsets(WindowInsets.Type.ime());
                // 键盘弹出时底部让位给键盘，且不少于导航栏高度
                int bottom = Math.max(bars.bottom, ime.bottom);
                if (v.getPaddingTop() != bars.top || v.getPaddingBottom() != bottom) {
                    v.setPadding(0, bars.top, 0, bottom);
                }
                return ins;
            }
        });
        root.requestApplyInsets();
    }

    // ---------- 顶部栏 ----------

    private View appBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SURFACE);
        bar.setPadding(dp(16), dp(10), dp(6), dp(10));
        bar.setElevation(dp(2));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("会话群");
        title.setTextColor(TEXT);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);

        subView = new TextView(this);
        subView.setText("");
        subView.setTextColor(SUB);
        subView.setTextSize(11);
        subView.setSingleLine(true);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subView.setPadding(0, dp(1), 0, 0);
        titles.addView(subView);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 常用操作放顶栏（原来全塞在 ⋮ 里，点两次才到）
        bar.addView(barButton("生成", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commandDialog();
            }
        }));
        bar.addView(barButton("添加", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSession();
            }
        }));
        bar.addView(barButton("刷新", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        }));
        bar.addView(iconButton("⋮", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu();
            }
        }));
        return bar;
    }

    /** 顶栏常用按钮：文字比图标更明确，44dp 触区。 */
    private View barButton(String text, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(SUB);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), 0, dp(6), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        tv.setLayoutParams(lp);
        tv.setOnClickListener(l);
        return tv;
    }

    private View iconButton(String text, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(SUB);
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(round(0x00000000, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        tv.setLayoutParams(lp);
        tv.setOnClickListener(l);
        return tv;
    }

    // ---------- 底部输入条 ----------

    private View inputBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);
        bar.setBackgroundColor(SURFACE);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));

        input = new EditText(this);
        input.setHint("发消息…（首行 refs: 可挂引用）");
        input.setHintTextColor(HINT);
        input.setTextColor(TEXT);
        input.setTextSize(14);
        input.setBackground(round(SURFACE_ALT, 18));
        input.setPadding(dp(14), dp(9), dp(14), dp(9));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(1);
        input.setMaxLines(4);
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView send = new TextView(this);
        send.setText("↑");
        send.setTextColor(0xFFFFFFFF);
        send.setTextSize(19);
        send.setGravity(Gravity.CENTER);
        send.setBackground(circle(ACCENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(42), dp(42));
        slp.leftMargin = dp(8);
        send.setLayoutParams(slp);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSend();
            }
        });
        bar.addView(send);
        return bar;
    }

    // ---------- 读与绘制 ----------

    private void load() {
        if (busy) return;
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Msg> got = null;
                String cur = "";
                String mode = null;
                Exception err = null;
                try {
                    store = Stores.get(MainActivity.this, prefs);
                    if (store == null) {
                        err = new IllegalStateException("未找到存储：⋮ → 消息目录");
                    } else {
                        mode = store.mode();
                        got = store.read();
                        cur = store.cursor();
                    }
                } catch (Exception e) {
                    err = e;
                }
                final List<Msg> data = got;
                final String cur2 = cur;
                final String mode2 = mode;
                final Exception e2 = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (e2 != null) {
                            subView.setText(e2.getMessage() == null ? e2.toString() : e2.getMessage());
                            return;
                        }
                        cursor = cur2;
                        render(data, mode2);
                    }
                });
            }
        }).start();
    }

    private void render(List<Msg> data, String mode) {
        msgs.clear();
        msgs.addAll(data);

        int unread = 0;
        for (Msg m : msgs) {
            if (!m.from.equals(Store.ME) && m.id().compareTo(cursor) > 0) unread++;
        }
        String tail = shortMode(mode);
        if (msgs.isEmpty()) subView.setText("还没有消息" + tail);
        else if (unread > 0) subView.setText(unread + " 条未读 · 共 " + msgs.size() + " 条" + tail);
        else subView.setText("共 " + msgs.size() + " 条 · 已读" + tail);

        feed.removeAllViews();
        if (msgs.isEmpty()) {
            feed.addView(emptyState());
            scroll.post(new Runnable() {
                @Override
                public void run() {
                    scroll.fullScroll(View.FOCUS_UP);
                }
            });
            return;
        }
        boolean dividerDrawn = false;
        String lastDay = "";
        for (Msg m : msgs) {
            String day = m.time.length() >= 10 ? m.time.substring(0, 10) : "";
            if (!day.isEmpty() && !day.equals(lastDay)) {
                feed.addView(chip(day, 10));
                lastDay = day;
            }
            if (!dividerDrawn && unread > 0 && !m.from.equals(Store.ME)
                    && m.id().compareTo(cursor) > 0) {
                feed.addView(unreadDivider());
                dividerDrawn = true;
            }
            feed.addView(m.isDigest() ? systemCard(m) : bubbleRow(m));
        }
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });

        if (unread > 0 && !advancedThisRound) {
            advancedThisRound = true;
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (msgs.isEmpty()) return;
                    final String lastId = msgs.get(msgs.size() - 1).id();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (store != null) store.setCursor(lastId);
                            } catch (Exception ignored) {
                            }
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    cursor = lastId;
                                    load();          // 重绘：未读分隔线跟着消失
                                }
                            });
                        }
                    }).start();
                }
            }, 2500);
        }
    }

    /** 通道名只说 root / SAF，完整路径留在 ⋮ → 消息目录 里看。 */
    private String shortMode(String mode) {
        if (mode == null || mode.isEmpty()) return "";
        if (mode.startsWith("root")) return " · root";
        if (mode.startsWith("SAF")) return " · SAF";
        return " · " + mode;
    }

    /** 空列表占位：告诉人下一步做什么，而不是一片白。 */
    private View emptyState() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(72), dp(24), dp(24));

        TextView big = new TextView(this);
        big.setText("还没有消息");
        big.setTextColor(SUB);
        big.setTextSize(15);
        big.setGravity(Gravity.CENTER);
        box.addView(big);

        TextView hint = new TextView(this);
        hint.setText("让另一个会话或程序往这个目录写一条消息，\n这里就会出现；也可以点右上角 ⋮ → 添加会话。");
        hint.setTextColor(HINT);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(3), 1f);
        hint.setPadding(0, dp(10), 0, 0);
        box.addView(hint);

        TextView go = action("看 3 步上手 →", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onboarding();
            }
        });
        go.setGravity(Gravity.CENTER);
        go.setPadding(0, dp(16), 0, 0);
        box.addView(go);
        return box;
    }

    /** 普通消息：头像 + 气泡；自己在右侧。 */
    private View bubbleRow(final Msg m) {
        boolean me = m.from.equals(Store.ME);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(me ? (Gravity.CENTER_VERTICAL | Gravity.END)
                : (Gravity.CENTER_VERTICAL | Gravity.START));
        row.setPadding(0, dp(5), 0, dp(5));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackground(round(me ? BUBBLE_ME : BUBBLE_OTHER, 14));
        bubble.setPadding(dp(12), dp(9), dp(12), dp(8));

        if (!me) {
            TextView name = new TextView(this);
            name.setText(m.from);
            name.setTextColor(avatarColor(m.from));
            name.setTextSize(11);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            bubble.addView(name);
        }

        TextView body = new TextView(this);
        body.setText(m.body.isEmpty() ? "（空）" : m.body);
        body.setTextColor(me ? ME_TEXT : TEXT);
        body.setTextSize(14.5f);
        body.setLineSpacing(dp(2), 1f);
        body.setMaxWidth(maxBubble);
        body.setTextIsSelectable(true);
        bubble.addView(body);

        if (!m.refs.isEmpty() && !m.refs.equals("-")) {
            TextView refs = new TextView(this);
            refs.setText("↪ " + m.refs);
            refs.setTextColor(me ? (night ? 0xFFBFD4F5 : 0xCCFFFFFF) : SUB);
            refs.setTextSize(10.5f);
            refs.setTypeface(Typeface.MONOSPACE);
            refs.setMaxWidth(maxBubble);
            refs.setPadding(0, dp(3), 0, 0);
            bubble.addView(refs);
        }

        TextView time = new TextView(this);
        time.setText(hhmm(m.time));
        time.setTextColor(me ? (night ? 0xFFA9C3E8 : 0xCCFFFFFF) : HINT);
        time.setTextSize(9.5f);
        time.setGravity(Gravity.END);
        time.setPadding(0, dp(3), 0, 0);
        bubble.addView(time);

        if (me) {
            row.addView(spacer());
            row.addView(bubble);
            row.addView(avatar(m.from));
        } else {
            row.addView(avatar(m.from));
            row.addView(bubble);
            row.addView(spacer());
        }

        bubble.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                messageActions(m);
                return true;
            }
        });
        return row;
    }

    /** 摘要：居中的系统卡片。 */
    private View systemCard(final Msg m) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.setPadding(0, dp(8), 0, dp(8));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CHIP, 12));
        card.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView head = new TextView(this);
        head.setText("摘要 · 压缩 " + m.covers + " 条" + (m.subject.isEmpty() ? "" : " · " + m.subject));
        head.setTextColor(ACCENT);
        head.setTextSize(11);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);

        TextView body = new TextView(this);
        body.setText(m.body.isEmpty() ? "（空）" : m.body);
        body.setTextColor(TEXT);
        body.setTextSize(13);
        body.setMaxWidth(maxBubble + dp(20));
        body.setPadding(0, dp(4), 0, 0);
        body.setTextIsSelectable(true);
        card.addView(body);

        TextView meta = new TextView(this);
        meta.setText(m.from + " · " + hhmm(m.time));
        meta.setTextColor(HINT);
        meta.setTextSize(9.5f);
        meta.setPadding(0, dp(4), 0, 0);
        card.addView(meta);

        wrap.addView(card);
        wrap.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                messageActions(m);
                return true;
            }
        });
        return wrap;
    }

    private View chip(String text, float size) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.setPadding(0, dp(10), 0, dp(6));
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(SUB);
        tv.setTextSize(size);
        tv.setBackground(round(CHIP, 9));
        tv.setPadding(dp(10), dp(3), dp(10), dp(3));
        wrap.addView(tv);
        return wrap;
    }

    private View unreadDivider() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));

        View left = new View(this);
        left.setBackgroundColor(DIVIDER);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(1), 1f));

        TextView tv = new TextView(this);
        tv.setText("  未读  ");
        tv.setTextColor(ACCENT);
        tv.setTextSize(10);
        row.addView(tv);

        View right = new View(this);
        right.setBackgroundColor(DIVIDER);
        row.addView(right, new LinearLayout.LayoutParams(0, dp(1), 1f));
        return row;
    }

    private View avatar(String name) {
        TextView tv = new TextView(this);
        tv.setText(name.isEmpty() ? "?" : name.substring(0, 1));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(circle(avatarColor(name)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.leftMargin = dp(8);
        lp.rightMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    // ---------- 小工具 ----------

    private int avatarColor(String name) {
        return AVATARS[Math.abs(name.hashCode()) % AVATARS.length];
    }

    private String hhmm(String iso) {
        return iso.length() >= 16 ? iso.substring(11, 16) : iso;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    // ---------- 上手 ----------

    /** 首次启动与「⋮ → 上手说明」共用的一页。 */
    private void onboarding() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(8));

        box.addView(step("1", "选消息目录",
                "这个 App 只干一件事：读写一个目录里的消息文件。"
                        + "有 root：用 su 读写你指定的绝对路径；没有 root：点下面选一个文件夹（如 Download/msgbus）。"
                        + "两种方式写出的格式完全一样，换通道不丢消息。"));
        box.addView(action("选择文件夹（SAF）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTree();
            }
        }));

        box.addView(step("2", "给会话起名字",
                "群里的「会话」= 消息头部 from: 那个字符串，由发送方自己填，不是账号也不受验证。"
                        + "名字要和你在命令里写给它的完全一致（大小写、前缀都算），否则群里会当成两个人。"
                        + "建议带前缀，如 yg-文件管理。添加后会在 members/ 里登记并发一条【入群】公告。"));
        box.addView(action("添加会话", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSession();
            }
        }));

        box.addView(step("3", "让会话发言 / 收消息",
                "没有推送：post 等于在共享目录里留一张便条，对方下次跑 new 才看到——不是实时汇报，也不会叫醒谁。"
                        + "最省事的做法：点下面「生成并复制一条命令」，填三格（以谁的名义发 / 消息正文 / 引用），"
                        + "把生成的命令发给对方执行——它跑完就自己出现在群里。"));
        box.addView(action("生成并复制一条命令（填 3 格）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commandDialog();
            }
        }));

        // 把 refs 讲清楚：它是「让人自己去看」的指针，不是消息编号，也不是聊天内容
        TextView refsTitle = new TextView(this);
        refsTitle.setText("refs 填什么（选填，留空就写 -）");
        refsTitle.setTextColor(TEXT);
        refsTitle.setTextSize(13);
        refsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        refsTitle.setPadding(0, dp(16), 0, dp(4));
        box.addView(refsTitle);

        TextView refsBody = new TextView(this);
        refsBody.setText("· commit 短 sha：088fcd29\n"
                + "· 文件 / 下载路径：/sdcard/Download/msgbus-0.13.0.apk\n"
                + "· 仓库相对路径：projects/ime/README.md\n"
                + "· 没有可指的：留空（App 会写成 -）\n\n"
                + "refs 是「想让我自己去看，就看这个」，不是消息编号，也不要粘贴聊天内容。");
        refsBody.setTextColor(SUB);
        refsBody.setTextSize(12);
        refsBody.setLineSpacing(dp(2), 1f);
        box.addView(refsBody);

        TextView note = new TextView(this);
        note.setText("说明：消息就是一个目录里的文本文件（spool / seen / archive / members 四个子目录），"
                + "没有服务器、没有账号、没有推送。App 只是它的阅读器；删除 App 不会删消息。");
        note.setTextColor(SUB);
        note.setTextSize(11);
        note.setLineSpacing(dp(2), 1f);
        note.setPadding(0, dp(14), 0, 0);
        box.addView(note);

        box.addView(action("写一条示例消息（验证读写通路）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sampleMessage();
            }
        }));

        sc.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("会话群 · 3 步上手")
                .setView(sc)
                .setPositiveButton("知道了", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        prefs.edit().putBoolean("onboarded", true).apply();
                    }
                })
                .show();
    }

    /** 表单输入框：圆角底 + 内边距，单行/多行统一外观（默认 EditText 只有一条下划线，很难看出边界）。 */
    private EditText field(String hint, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(HINT);
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setBackground(round(SURFACE_ALT, 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (lines <= 1) {
            e.setSingleLine(true);
            e.setMaxLines(1);
        } else {
            e.setMinLines(lines);
            e.setMaxLines(lines + 2);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        return e;
    }

    /** 表单小标题：统一 12sp、次要色、和输入框留白一致。 */
    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(SUB);
        t.setTextSize(12);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(0, dp(12), 0, dp(5));
        return t;
    }

    private View step(String num, String title, String desc) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(14), 0, dp(4));
        TextView t = new TextView(this);
        t.setText(num + ". " + title);
        t.setTextColor(TEXT);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(SUB);
        d.setTextSize(12);
        d.setLineSpacing(dp(2), 1f);
        d.setPadding(0, dp(4), 0, 0);
        box.addView(d);
        return box;
    }

    private TextView action(String text, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ACCENT);
        tv.setTextSize(13);
        tv.setPadding(0, dp(6), 0, dp(2));
        tv.setOnClickListener(l);
        return tv;
    }

    /** 三格填完直接生成并复制，不再「生成 → 再点复制」两步。 */
    private void commandDialog() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), dp(4));

        TextView hdr = new TextView(this);
        hdr.setText("三格都是你自己填的值，不用粘任何聊天记录。点「生成并复制」后命令进剪贴板。");
        hdr.setTextColor(SUB);
        hdr.setTextSize(11);
        hdr.setLineSpacing(dp(2), 1f);
        hdr.setPadding(0, dp(4), 0, dp(2));
        box.addView(hdr);

        box.addView(label("① 以谁的名义发（消息里的 from:，须与该会话自己发时一致）"));
        final EditText name = field("如 yg-文件管理", 1);
        name.setText(prefs.getString("my_name", ""));
        box.addView(name);

        box.addView(label("② 消息正文（给群里留的一句话，不是复制别人的消息）"));
        final EditText text = field("例：开始接 msgbus 群，负责发布准备", 3);
        box.addView(text);

        box.addView(label("③ 引用（选填）：能让人自己去看的提交号或文件路径"));
        final EditText refs = field("例：088fcd29 或 /sdcard/Download/x.apk；留空则不写", 1);
        box.addView(refs);

        TextView tip = new TextView(this);
        tip.setText("发给对方执行即可；它跑完会自己往群里发一条，不需要你替它说话。");
        tip.setTextColor(SUB);
        tip.setTextSize(11);
        tip.setLineSpacing(dp(2), 1f);
        tip.setPadding(0, dp(12), 0, dp(2));
        box.addView(tip);

        sc.addView(box);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("生成给对方的命令")
                .setView(sc)
                .setNegativeButton("取消", null)
                .setPositiveButton("生成并复制", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        String r = refs.getText().toString().trim();
                        String b = text.getText().toString().trim();
                        if (n.isEmpty()) n = "yg-会话名";
                        if (b.isEmpty()) b = "（这里写一句话）";
                        prefs.edit().putString("my_name", n).apply();
                        String cmd = buildCommand(n, r, b);
                        copy(cmd);
                        showCommand(cmd);
                    }
                })
                .create();
        dlg.show();
        if (dlg.getWindow() != null) {
            // 键盘弹出时压缩对话框，别把输入框顶出屏幕
            dlg.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private String buildCommand(String name, String refs, String body) {
        return CmdBuilder.build(Stores.rootDir(prefs), findMsgSh(), name, refs, body);
    }

    private String findMsgSh() {
        // 不写死任何本机路径：从当前消息目录往上推，再退到两个通用位置
        String dir = Stores.rootDir(prefs);
        String[] cands = {
                dir + "/../../tools/msgbus/msg.sh",
                dir + "/../tools/msgbus/msg.sh",
                dir + "/msg.sh",
                "/sdcard/msgbus/msg.sh"};
        for (String c : cands) {
            try {
                if (MsgStore.sh("[ -f " + MsgStore.quote(c) + " ] && echo Y").trim().equals("Y")) return c;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void showCommand(final String cmd) {
        ScrollView sc = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(cmd);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(TEXT);
        tv.setBackground(round(SURFACE_ALT, 8));
        tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        tv.setTextIsSelectable(true);
        sc.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("已复制，粘给对方即可")
                .setView(sc)
                .setPositiveButton("好", null)
                .show();
    }

    private void sampleMessage() {
        runOp(new Op() {
            @Override
            public void run() throws Exception {
                if (store == null) store = Stores.get(MainActivity.this, prefs);
                if (store == null) throw new IllegalStateException("先选消息目录");
                store.post("refs: -\n示例消息：这条由 App 写入，用来确认读写通路正常。");
            }
        }, new Runnable() {
            @Override
            public void run() {
                advancedThisRound = true;
                prefs.edit().putBoolean("onboarded", true).apply();
                load();
            }
        });
    }

    // ---------- 菜单 ----------

    private void menu() {
        final String notifyLabel = prefs.getBoolean("notify_on", false) ? "关闭后台提醒" : "开启后台提醒";
        // 生成 / 添加 / 刷新 已提到顶栏，这里不再重复
        final String[] items = {"上手说明", "聊天记录（归档）", notifyLabel, "成员管理",
                "通知档位", "消息目录", "写一条示例消息", "清理数据"};
        new AlertDialog.Builder(this)
                .setTitle("会话群")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        switch (i) {
                            case 0: onboarding(); break;
                            case 1: archiveDialog(); break;
                            case 2: toggleNotify(); break;
                            case 3: membersDialog(); break;
                            case 4: notifyLevel(); break;
                            case 5: dirDialog(); break;
                            case 6: sampleMessage(); break;
                            case 7: clearDialog(); break;
                            default: load(); break;
                        }
                    }
                }).show();
    }

    private void membersDialog() {
        if (store == null) {
            Toast.makeText(this, "还没有存储通道", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] raw = new String[1];
                try {
                    raw[0] = store.members();
                } catch (Exception e) {
                    raw[0] = "读失败：" + e.getMessage();
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        showMembersList(raw[0]);
                    }
                });
            }
        }).start();
    }

    private void showMembersList(String text) {
        final List<String> names = new ArrayList<String>();
        List<String> lines = new ArrayList<String>();
        for (String ln : text.split("\n")) {
            if (ln.trim().isEmpty()) continue;
            names.add(ln.trim().split("\\s+")[0]);
            lines.add(ln.trim());
        }
        lines.add(0, "＋ 添加会话");
        new AlertDialog.Builder(this)
                .setTitle("成员 · 共 " + names.size() + " 条记录（点一下改状态或删除）")
                .setItems(lines.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        if (i == 0) addSession();
                        else toggleMember(names.get(i - 1));
                    }
                })
                .setNegativeButton("关闭", null)
                .setNeutralButton("清理所有 left", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        cleanLeft();
                    }
                })
                .show();
    }

    private void confirmDeleteMember(final String name) {
        if (Store.ME.equals(name)) {
            Toast.makeText(this, "「" + Store.ME + "」是 App 自己的身份，不能删；要停用请改成 left",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除成员记录？")
                .setMessage("只删 members/" + name + " 这条名单记录；它发过的消息仍然保留在群里。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        runOp(new Op() {
                            @Override
                            public void run() throws Exception {
                                store.deleteMember(name);
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                load();
                            }
                        });
                    }
                })
                .show();
    }

    private void cleanLeft() {
        if (store == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int removed = 0;
                String err = null;
                try {
                    for (String ln : store.members().split("\n")) {
                        String[] p = ln.trim().split("\\s+");
                        if (p.length >= 2 && "left".equals(p[1]) && !Store.ME.equals(p[0])) {
                            store.deleteMember(p[0]);
                            removed++;
                        }
                    }
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final int n = removed;
                final String e2 = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                e2 != null ? "清理失败：" + e2 : "已清理 " + n + " 条 left 记录",
                                Toast.LENGTH_SHORT).show();
                        load();
                    }
                });
            }
        }).start();
    }

    private void toggleMember(final String name) {
        final String[] choices = {"设为 active（回归）", "设为 left（退出）", "复制名字",
                "删除这条记录（不删它发过的消息）"};
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(choices, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        if (i == 2) {
                            copy(name);
                            return;
                        }
                        if (i == 3) {
                            confirmDeleteMember(name);
                            return;
                        }
                        final boolean active = i == 0;
                        runOp(new Op() {
                            @Override
                            public void run() throws Exception {
                                store.setMember(name, active ? "active" : "left",
                                        active ? "app 恢复" : "app 标记退出");
                                store.post("refs: -\n【" + (active ? "回归" : "退出") + "】" + name);
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                load();
                            }
                        });
                    }
                })
                .show();
    }

    private void addSession() {
        final EditText et = field("会话名，如 yg-新项目", 1);
        new AlertDialog.Builder(this)
                .setTitle("添加会话到群")
                .setMessage("写 members/<名字> 并在群里发【入群】公告；不删任何历史消息。")
                .setView(et)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final String name = et.getText().toString().trim();
                        if (name.isEmpty()) return;
                        runOp(new Op() {
                            @Override
                            public void run() throws Exception {
                                store.setMember(name, "active", "app 添加");
                                store.post("refs: -\n【入群】" + name
                                        + "\n（机主在 App 里添加，等它自己 post 第一条）");
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                load();
                            }
                        });
                    }
                })
                .show();
    }

    private void toggleNotify() {
        boolean on = !prefs.getBoolean("notify_on", false);
        prefs.edit().putBoolean("notify_on", on).apply();
        Intent i = new Intent(this, PollService.class);
        if (on) {
            i.setAction("start");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } else {
            i.setAction("stop");
            startService(i);
            stopService(i);
        }
        Toast.makeText(this, on ? "已开启后台提醒（每 15 秒）" : "已关闭后台提醒",
                Toast.LENGTH_SHORT).show();
    }

    private void notifyLevel() {
        final String[] labels = {"静音（只在通知栏）", "默认（响一下）", "横幅（响 + 弹出）"};
        String cur = prefs.getString("notify_level", "def");
        int checked = cur.equals("low") ? 0 : (cur.equals("high") ? 2 : 1);
        new AlertDialog.Builder(this)
                .setTitle("通知档位（当前：" + labels[checked] + "）")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        prefs.edit().putString("notify_level",
                                i == 0 ? "low" : (i == 2 ? "high" : "def")).apply();
                        d.dismiss();
                        Toast.makeText(MainActivity.this, "已设为：" + labels[i], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void dirDialog() {
        // 注意：AlertDialog 里 setMessage 与 setItems 同时用会让列表不渲染（实测），
        // 说明写进标题与条目文案，只用 setItems。
        final String[] items = {"选文件夹（SAF：内部存储 / Download / SD 卡）",
                "浏览 / 设置 root 目录（可到 /，需 su）",
                "只用 root 通道", "自动（有 root 用 root）"};
        new AlertDialog.Builder(this)
                .setTitle("消息目录 · 当前 " + Stores.describe(prefs))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        if (i == 0) pickTree();
                        else if (i == 1) browseRoot();
                        else {
                            prefs.edit().putString("mode", i == 2 ? "root" : "auto").apply();
                            load();
                        }
                    }
                })
                .show();
    }

    /** 用 su 列目录，从 / 一路点进去选；替代手打绝对路径。 */
    private void browseRoot() {
        if (!MsgStore.suAvailable()) {
            Toast.makeText(this, "没有 su，root 通道不可用；只能走 SAF", Toast.LENGTH_LONG).show();
            return;
        }
        showDir(Stores.rootDir(prefs));
    }

    private void showDir(final String path) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> dirs = new ArrayList<String>();
                String err = null;
                try {
                    String out = MsgStore.sh("cd " + MsgStore.quote(path) + " 2>/dev/null && "
                            + "ls -1p 2>/dev/null | grep '/$' | sed 's#/$##' | head -300");
                    for (String ln : out.split("\n")) {
                        String d = ln.trim();
                        if (!d.isEmpty()) dirs.add(d);
                    }
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final String e2 = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (e2 != null) {
                            Toast.makeText(MainActivity.this, "列目录失败：" + e2, Toast.LENGTH_LONG).show();
                            return;
                        }
                        final boolean hasUp = !"/".equals(path);
                        final int selIdx = hasUp ? 1 : 0;
                        List<String> items = new ArrayList<String>();
                        if (hasUp) items.add("..（上级）");
                        items.add("✅ 用这个目录");
                        items.addAll(dirs);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(path.isEmpty() ? "/" : path)
                                .setItems(items.toArray(new String[0]),
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int i) {
                                                if (i == selIdx) {
                                                    prefs.edit().putString("root_dir", path)
                                                            .putString("mode", "root").apply();
                                                    store = null;
                                                    load();
                                                    Toast.makeText(MainActivity.this,
                                                            "已设为 " + path, Toast.LENGTH_SHORT).show();
                                                    return;
                                                }
                                                if (hasUp && i == 0) {
                                                    int idx = path.lastIndexOf('/');
                                                    showDir(idx <= 0 ? "/" : path.substring(0, idx));
                                                    return;
                                                }
                                                int di = i - selIdx - 1;
                                                if (di < 0 || di >= dirs.size()) return;
                                                showDir(("/".equals(path) ? "/" : path + "/") + dirs.get(di));
                                            }
                                        })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private void rootDirDialog() {
        final EditText et = field("绝对路径，如 /data/local/tmp/msgbus", 1);
        et.setText(Stores.rootDir(prefs));
        new AlertDialog.Builder(this)
                .setTitle("root 通道的消息目录")
                .setMessage("需要 su；App 直接读写该目录下的 spool/ seen/ archive/ members/。")
                .setView(et)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String p = et.getText().toString().trim();
                        if (p.isEmpty()) return;
                        prefs.edit().putString("root_dir", p).putString("mode", "root").apply();
                        store = null;
                        load();
                    }
                })
                .show();
    }

    private void pickTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            Toast.makeText(this, "系统没有文件选择器：" + e, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        prefs.edit().putString("tree_uri", u.toString()).putString("mode", "saf").apply();
        store = null;
        load();
        Toast.makeText(this, "已切到 SAF 通道", Toast.LENGTH_SHORT).show();
    }

    // ---------- 聊天记录（归档）与清理 ----------

    private void archiveDialog() {
        if (store == null) {
            Toast.makeText(this, "还没有存储通道", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text;
                try {
                    List<Msg> old = store.readArchived();
                    if (old.isEmpty()) {
                        text = "（没有归档记录）\\n\\n用「压缩成摘要」后，被压掉的原文会移到 archive/group/，在这里能翻回去。";
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < old.size(); i++) {
                            Msg m = old.get(i);
                            sb.append(i + 1).append(". ").append(m.time.length() >= 16
                                    ? m.time.substring(0, 16).replace('T', ' ') : m.time)
                              .append("  ").append(m.from).append("\n")
                              .append(m.body).append("\\n\\n");
                        }
                        text = "共 " + old.size() + " 条（archive/group）\\n\\n" + sb;
                    }
                } catch (Exception e) {
                    text = "读失败：" + e.getMessage();
                }
                final String t = text;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("聊天记录")
                                .setMessage(t)
                                .setPositiveButton("好", null)
                                .setNeutralButton("清空归档", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        confirmClear(true, false);
                                    }
                                })
                                .show();
                    }
                });
            }
        }).start();
    }

    private void confirmDelete(final Msg m) {
        new AlertDialog.Builder(this)
                .setTitle("删除这条消息？")
                .setMessage(m.from + " · " + m.firstLine() + "\\n\\n只删这个文件，不能撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        runOp(new Op() {
                            @Override
                            public void run() throws Exception {
                                store.delete(m.id(), false);
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                load();
                            }
                        });
                    }
                })
                .show();
    }

    private void clearDialog() {
        final String[] items = {"清空消息（spool）", "清空归档（archive/group）", "两者都清",
                "重置本地设置（游标/通知/目录）"};
        new AlertDialog.Builder(this)
                .setTitle("清理数据")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        if (i == 0) confirmClear(true, false);
                        else if (i == 1) confirmClear(false, true);
                        else if (i == 2) confirmClear(true, true);
                        else resetLocal();
                    }
                })
                .show();
    }

    private void confirmClear(final boolean spool, final boolean archive) {
        String what = spool && archive ? "消息和归档" : (spool ? "消息" : "归档");
        new AlertDialog.Builder(this)
                .setTitle("清空" + what + "？")
                .setMessage("会删除目录里的 .msg 文件，不能撤销；seed/members 与其它会话的记录也会一起消失。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        runOp(new Op() {
                            @Override
                            public void run() throws Exception {
                                store.clear(spool, archive);
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                load();
                                Toast.makeText(MainActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .show();
    }

    private void resetLocal() {
        new AlertDialog.Builder(this)
                .setTitle("重置本地设置？")
                .setMessage("清掉 App 的目录配置、已读游标、通知去重与开关；不删任何消息文件。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        prefs.edit().clear().apply();
                        cursor = "";
                        store = null;
                        load();
                        Toast.makeText(MainActivity.this, "已重置", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // ---------- 消息长按 ----------

    private void messageActions(final Msg m) {
        final List<String> labels = new ArrayList<String>();
        labels.add("复制正文");
        if (!m.refs.isEmpty() && !m.refs.equals("-")) labels.add("复制 refs");
        labels.add("标记此处已读");
        labels.add("压缩成摘要（digest）");
        labels.add("删除这条消息");
        new AlertDialog.Builder(this)
                .setTitle(m.from + " · " + m.firstLine())
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String label = labels.get(which);
                        if (label.equals("复制正文")) copy(m.body);
                        else if (label.equals("复制 refs")) copy(m.refs);
                        else if (label.equals("标记此处已读")) markRead(m);
                        else if (label.equals("删除这条消息")) confirmDelete(m);
                        else digestDialog();
                    }
                })
                .show();
    }

    private void markRead(final Msg m) {
        advancedThisRound = true;
        cursor = m.id();
        runOp(new Op() {
            @Override
            public void run() throws Exception {
                store.setCursor(m.id());
            }
        });
    }

    private void digestDialog() {
        final EditText et = field("第 1 行标题，其余是摘要正文（可先写 refs: …）", 4);
        new AlertDialog.Builder(this)
                .setTitle("把现有群消息压成一条摘要")
                .setView(et)
                .setNegativeButton("取消", null)
                .setPositiveButton("压缩", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String s = et.getText().toString().trim();
                        if (s.isEmpty()) return;
                        String[] parts = s.split("\n", 2);
                        final String title = parts[0].trim();
                        final String body = parts.length > 1 ? parts[1].trim() : title;
                        runOp(new Op() {
                            @Override
                            public void run() throws Exception {
                                store.digest(title, body);
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                load();
                            }
                        });
                    }
                })
                .show();
    }

    private void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("msgbus", s));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    // ---------- 发送 ----------

    private void doSend() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        runOp(new Op() {
            @Override
            public void run() throws Exception {
                if (store == null) throw new IllegalStateException("还没有存储通道");
                store.post(text);
            }
        }, new Runnable() {
            @Override
            public void run() {
                input.setText("");
                advancedThisRound = true;
                load();
            }
        });
    }

    // ---------- 后台任务 ----------

    private interface Op {
        void run() throws Exception;
    }

    private void runOp(final Op op) {
        runOp(op, null);
    }

    private void runOp(final Op op, final Runnable after) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] err = new String[1];
                try {
                    if (store == null) store = Stores.get(MainActivity.this, prefs);
                    op.run();
                } catch (Exception e) {
                    err[0] = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (err[0] != null) {
                            Toast.makeText(MainActivity.this, err[0], Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (after != null) after.run();
                    }
                });
            }
        }).start();
    }
}
