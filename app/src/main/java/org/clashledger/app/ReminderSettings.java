package org.clashledger.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ReminderSettings {
    public boolean enabled, vibration, potion;
    public String mode;
    public Set<String> categories;
    private final SharedPreferences prefs;
    public ReminderSettings(Context context) {
        prefs=context.getSharedPreferences("reminders",Context.MODE_PRIVATE);
        enabled=prefs.getBoolean("enabled",false);
        vibration=prefs.getBoolean("vibration",true);
        potion=prefs.getBoolean("potion",true);
        mode=prefs.getString("mode","notification");
        categories=new HashSet<>(prefs.getStringSet("categories",new HashSet<>(Arrays.asList(Upgrade.GROUPS))));
    }
    public void save() {
        prefs.edit().putBoolean("enabled",enabled).putBoolean("vibration",vibration).putBoolean("potion",potion)
            .putString("mode",mode).putStringSet("categories",new HashSet<>(categories)).apply();
    }
    public String channelId() { return "upgrade_"+mode+(vibration?"_vibrate":"_still")+"_v1"; }
}
