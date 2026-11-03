package org.clashledger.app;

import java.util.Locale;

/** One format per countdown: clock always includes seconds; word formats use minutes. */
public final class TimerFormat {
    private TimerFormat() { }
    public static String format(long ms,String style) {
        if(ms<=0)return "Ready";
        if("clock".equals(style)) {
            long s=(ms+999)/1000;
            return String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60);
        }
        if(!"words".equals(style))return Upgrade.duration(ms);
        long m=Math.max(1,(ms+59999)/60000),d=m/1440,h=m/60%24;
        return (d>0?d+(d==1?" day ":" days "):"")+(h>0?h+" hr ":"")+m%60+" min";
    }
}
