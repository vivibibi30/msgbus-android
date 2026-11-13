package io.github.vivibibi30.msgbus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;

/**
 * 后台提醒：每 15 秒读一次 spool（拉模型，没有推送），有新消息时发通知。
 * 通道分三档（静音 / 默认 / 横幅），由设置决定；本服务自身用静音通道常驻。
 */
public class PollService extends Service {

    static final String CH_LOW = "msgbus-low";
    static final String CH_DEF = "msgbus-def";
    static final String CH_HIGH = "msgbus-high";

    private static final int FOREGROUND_ID = 1001;
    private static final int NEW_ID = 1002;
    // 自适应间隔：App 在前台 15s；后台亮屏 60s；熄屏 5 分钟
    private static final long FAST = 15000L;
    private static final long IDLE = 60000L;
    private static final long SLEEP = 300000L;

    /** 由 MainActivity 的 onResume/onPause 设置（同进程静态量）。 */
    static volatile boolean appVisible = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private SharedPreferences prefs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Store s = Stores.get(PollService.this, prefs);
                        if (s != null) check(s.read());
                    } catch (Exception ignored) {
                        // 读失败（未授权、目录被删）就不打扰
                    }
                }
            }).start();
            ui.postDelayed(this, interval());
        }
    };

    private long interval() {
        if (appVisible) return FAST;
        boolean screen = true;
        try {
            android.os.PowerManager pm =
                    (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            screen = pm == null || pm.isInteractive();
        } catch (Exception ignored) {
        }
        return screen ? IDLE : SLEEP;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs = getSharedPreferences(Stores.PREF, Context.MODE_PRIVATE);
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        channels();
        startForeground(FOREGROUND_ID, foreground());
        if (!running) {
            running = true;
            ui.post(tick);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        ui.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- 判新 ----------

    private void check(List<Msg> msgs) {
        if (msgs.isEmpty()) return;
        Msg last = msgs.get(msgs.size() - 1);
        String notified = prefs.getString("notified_id", "");
        if (last.id().equals(notified)) return;

        Msg newest = null;
        int fresh = 0;
        for (Msg m : msgs) {
            if (m.id().compareTo(notified) > 0 && !Store.ME.equals(m.from)) {
                fresh++;
                newest = m;
            }
        }
        prefs.edit().putString("notified_id", last.id()).apply();
        if (fresh == 0 || notified.isEmpty() || newest == null) return;  // 首次不补历史

        String text = fresh == 1
                ? newest.from + "：" + newest.firstLine()
                : fresh + " 条新消息 · 最新 " + newest.from + "：" + newest.firstLine();
        notifyNew(text);
    }

    // ---------- 通知 ----------

    private void channels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mk(nm, CH_LOW, NotificationManager.IMPORTANCE_LOW);
        mk(nm, CH_DEF, NotificationManager.IMPORTANCE_DEFAULT);
        mk(nm, CH_HIGH, NotificationManager.IMPORTANCE_HIGH);
    }

    private void mk(NotificationManager nm, String id, int importance) {
        NotificationChannel ch = new NotificationChannel(id, "会话群-" + id.substring(7), importance);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
    }

    private String channelForLevel() {
        String lv = prefs.getString("notify_level", "def");
        if ("low".equals(lv)) return CH_LOW;
        if ("high".equals(lv)) return CH_HIGH;
        return CH_DEF;
    }

    private Notification foreground() {
        return new Notification.Builder(this, CH_LOW)
                .setContentTitle("会话群")
                .setContentText("正在盯消息目录（前台 15s / 后台 60s / 熄屏 5min）")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentIntent(open())
                .setOngoing(true)
                .build();
    }

    private void notifyNew(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification n = new Notification.Builder(this, channelForLevel())
                .setContentTitle("会话群")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setAutoCancel(true)
                .setContentIntent(open())
                .build();
        nm.notify(NEW_ID, n);
    }

    private PendingIntent open() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 0, i, flags);
    }
}
