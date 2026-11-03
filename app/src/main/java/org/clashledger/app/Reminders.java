package org.clashledger.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public final class Reminders {
    public static final String CHECK="org.clashledger.REMINDER_CHECK", TEST="org.clashledger.REMINDER_TEST", DISMISS="org.clashledger.REMINDER_DISMISS";
    private static SharedPreferences state(Context c) { return c.getSharedPreferences("reminder_schedule",Context.MODE_PRIVATE); }
    private static PendingIntent alarm(Context c,boolean test) {
        return PendingIntent.getBroadcast(c,test?41:40,new Intent(c,ReminderReceiver.class).setAction(test?TEST:CHECK),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean allowed(Context c) {
        return (Build.VERSION.SDK_INT<33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
            && c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    public static boolean exact(Context c) { return Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms(); }
    public static synchronized void sync(Context c, boolean deliverDue) {
        AlarmManager manager=c.getSystemService(AlarmManager.class);
        manager.cancel(alarm(c,false));
        ReminderSettings settings=new ReminderSettings(c);
        if(!settings.enabled || !allowed(c)) {
            manager.cancel(alarm(c,true)); state(c).edit().remove("armed").apply(); return;
        }
        ensureChannel(c,settings);
        Ledger ledger=new Ledger(c); long now=System.currentTimeMillis();
        Map<String,Long> previous=new HashMap<>();
        try {
            JSONObject saved=new JSONObject(state(c).getString("armed","{}"));
            java.util.Iterator<String> keys=saved.keys();
            while(keys.hasNext()) { String key=keys.next(); previous.put(key,saved.getLong(key)); }
        } catch(org.json.JSONException ignored) { /* Rebuild an invalid schedule from the saved village. */ }
        ReminderPlan plan=new ReminderPlan(ReminderPlan.events(ledger.upgrades,ledger.playerTag,ledger.boostUntil,now,settings.categories,settings.potion,ledger.boosts),previous,now,deliverDue);
        // Persist before delivery so another receiver cannot repeat the same completion alert.
        state(c).edit().putString("armed",new JSONObject(plan.armed).toString()).commit();
        for(ReminderPlan.Event event:plan.due) post(c,settings,event.key,event.title,event.detail);
        if(plan.next!=Long.MAX_VALUE) schedule(c,plan.next,false);
    }
    private static void schedule(Context c,long at,boolean test) {
        AlarmManager manager=c.getSystemService(AlarmManager.class);
        try {
            if(exact(c)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c,test));
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c,test));
        } catch(SecurityException denied) {
            // Permission can change between the check and the scheduling call.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c,test));
        }
    }
    public static void test(Context c) { schedule(c,System.currentTimeMillis()+10000,true); }
    public static void deliverTest(Context c) {
        ReminderSettings settings=new ReminderSettings(c);
        if(settings.enabled&&allowed(c))post(c,settings,"test","Your village is calling","Test reminder · This is how upgrade alerts will appear.");
    }
    public static void ensureChannel(Context c,ReminderSettings settings) {
        String name=settings.mode.equals("alarm")?"Upgrade alarms":settings.mode.equals("silent")?"Quiet upgrade reminders":"Upgrade notifications";
        NotificationChannel channel=new NotificationChannel(settings.channelId(),name+(settings.vibration?" · vibration":""),settings.mode.equals("silent")?NotificationManager.IMPORTANCE_DEFAULT:NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Upgrade completion and boost expiry reminders. You can customize sound and vibration here.");
        channel.enableVibration(settings.vibration);
        if(settings.vibration)channel.setVibrationPattern(new long[]{0,300,150,300});
        boolean alarm=settings.mode.equals("alarm");
        channel.setSound(settings.mode.equals("silent")?null:RingtoneManager.getDefaultUri(alarm?RingtoneManager.TYPE_ALARM:RingtoneManager.TYPE_NOTIFICATION),new AudioAttributes.Builder().setUsage(alarm?AudioAttributes.USAGE_ALARM:AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private static void post(Context c,ReminderSettings settings,String key,String title,String detail) {
        if(!allowed(c))return;
        ensureChannel(c,settings);
        PendingIntent open=PendingIntent.getActivity(c,50,new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent dismiss=new Intent(c,ReminderReceiver.class).setAction(DISMISS).setData(android.net.Uri.parse("clashledger://reminder/"+android.net.Uri.encode(key))).putExtra("key",key);
        PendingIntent stop=PendingIntent.getBroadcast(c,51,dismiss,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        boolean alarm=settings.mode.equals("alarm");
        Notification.Builder builder=new Notification.Builder(c,settings.channelId())
            .setSmallIcon(R.drawable.ic_notification).setColor(0xFFF2C46C).setContentTitle(title).setContentText(detail)
            .setStyle(new Notification.BigTextStyle().bigText(detail)).setContentIntent(open).setAutoCancel(true)
            .setCategory(alarm?Notification.CATEGORY_ALARM:Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setShowWhen(true)
            .addAction(new Notification.Action.Builder(null,alarm?"Stop alarm":"Dismiss",stop).build());
        if(alarm)builder.setTimeoutAfter(60000);
        Notification notification=builder.build();
        if(alarm)notification.flags|=Notification.FLAG_INSISTENT;
        try { c.getSystemService(NotificationManager.class).notify(key,0,notification); }
        catch(SecurityException ignored) { /* A revoked notification permission should never crash a timer. */ }
    }
    public static void dismiss(Context c,String key) { if(key!=null)c.getSystemService(NotificationManager.class).cancel(key,0); }
    private Reminders() { }
}
