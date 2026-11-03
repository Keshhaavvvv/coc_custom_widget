package org.clashledger.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure scheduling decisions: only timers armed while running can produce completion alerts. */
public final class ReminderPlan {
    public static final class Event {
        public final String key, title, detail;
        public final long remaining;
        public Event(String key, String title, String detail, long remaining) {
            this.key=key; this.title=title; this.detail=detail; this.remaining=remaining;
        }
    }
    public final Map<String,Long> armed = new HashMap<>();
    public final List<Event> due = new ArrayList<>();
    public long next = Long.MAX_VALUE;
    public ReminderPlan(List<Event> current, Map<String,Long> previous, long now, boolean deliverDue) {
        for (Event event : current) {
            if (event.remaining > 0) {
                long at=now+event.remaining;
                armed.put(event.key,at); next=Math.min(next,at);
            } else if (deliverDue && previous.containsKey(event.key) && previous.get(event.key)<=now) {
                due.add(event);
            }
        }
    }
    public static List<Event> events(List<Upgrade> upgrades, String village, long boostUntil, long now, Set<String> categories, boolean potion) {
        return events(upgrades,village,boostUntil,now,categories,potion,upgrades.isEmpty()?new BoostState():upgrades.get(0).boosts);
    }
    public static List<Event> events(List<Upgrade> upgrades,String village,long boostUntil,long now,Set<String> categories,boolean potion,BoostState s) {
        List<Event> result=new ArrayList<>();
        for(Upgrade u:upgrades) if(categories.contains(u.group)) {
            String key=village+"|"+u.id+"|"+u.group+"|"+u.name+"|"+u.level;
            result.add(new Event(key,u.name+" is ready",u.group+" · Level "+u.level+(u.goblin?" · Goblin":""),u.wallRemaining(now,boostUntil)));
        }
        if(potion && boostUntil>0) result.add(new Event(village+"|potion|"+boostUntil,"Builder Potion finished","Builder Potion window ended; other boosts may still be active.",Math.max(0,boostUntil-now)));
        if(potion) {
            for(int i=0;i<s.ends.length;i++)if(s.ends[i]>0)result.add(new Event(village+"|"+BoostState.KEYS[i]+"|"+s.ends[i],BoostState.NAMES[i]+" finished","Boost window ended.",Math.max(0,s.ends[i]-now)));
            if(s.eventEnd>0&&s.eventRate>1)result.add(new Event(village+"|event|"+s.eventEnd,"Event adjustment finished","Manual event speed window ended.",Math.max(0,s.eventEnd-now)));
        }
        return result;
    }
}
