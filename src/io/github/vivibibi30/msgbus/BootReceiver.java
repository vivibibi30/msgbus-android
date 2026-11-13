package io.github.vivibibi30.msgbus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** 开机后如果提醒开关是开的，把前台服务拉起来（否则重启就静默失效）。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences sp = c.getSharedPreferences(Stores.PREF, Context.MODE_PRIVATE);
        if (!sp.getBoolean("notify_on", false)) return;
        Intent s = new Intent(c, PollService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
        else c.startService(s);
    }
}
