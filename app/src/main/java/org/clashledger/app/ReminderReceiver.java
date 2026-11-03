package org.clashledger.app;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String action=intent.getAction();
        if(Reminders.DISMISS.equals(action)) { Reminders.dismiss(context,intent.getStringExtra("key")); return; }
        if(Reminders.TEST.equals(action)) { Reminders.deliverTest(context); return; }
        if(Reminders.CHECK.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
            || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
            || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            Reminders.sync(context,true); UpgradeWidget.refresh(context);
        }
    }
}
