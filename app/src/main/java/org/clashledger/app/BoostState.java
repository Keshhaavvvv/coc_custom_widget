package org.clashledger.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Absolute boost windows. Imported work already includes duration discounts. */
public final class BoostState {
    public static final String[] KEYS={"lab_boost","pet_boost","builder_consumable","clocktower_boost","lab_consumable"};
    public static final String[] NAMES={"Research Potion","Pet Potion","Builder Bite","Clock Tower","Study Soup"};
    public static final int[] RATES={24,24,2,10,4};
    public final long[] ends=new long[5];
    public long eventStart,eventEnd;
    public double eventRate=1;
    public String eventGroup="Builders";
    public boolean repeatHelpers;
    public int apprenticeLevel,labLevel;
    public long apprenticeReady,labReady;
    public double rate(String group,long time,long builderEnd) {
        double speed=1;
        if(group.equals("Builders")) speed=Math.max(time<builderEnd?10:1,time<ends[2]?2:1);
        if(group.equals("Laboratory")) speed=(time<ends[0]?24:1)+(time<ends[4]?3:0);
        if(group.equals("Pets")) speed=(time<ends[1]?24:1)+(time<ends[4]?3:0);
        if(group.equals("Builder Base")||group.equals("Star Laboratory")) speed=time<ends[3]?10:1;
        if(time>=eventStart&&time<eventEnd&&group.equals(eventGroup)) speed*=eventRate;
        return speed;
    }
    public double work(String group,long start,long end,long builderEnd) {
        double result=0;
        while(start<end) {
            long next=end;
            for(long boundary:new long[]{builderEnd,ends[0],ends[1],ends[2],ends[3],ends[4],eventStart,eventEnd})
                if(boundary>start)next=Math.min(next,boundary);
            result+=(next-start)*rate(group,start,builderEnd); start=next;
        }
        return result;
    }
    public String summary(long now,long builderEnd) {
        java.util.List<String> active=new java.util.ArrayList<>();
        if(builderEnd>now)active.add("Builder 10× · "+Upgrade.duration(builderEnd-now));
        for(int i=0;i<ends.length;i++)if(ends[i]>now)active.add(NAMES[i]+" "+RATES[i]+"× · "+Upgrade.duration(ends[i]-now));
        if(eventEnd>now&&eventRate>1)active.add(eventGroup+" event "+eventRate+"× · "+Upgrade.duration(eventEnd-now));
        return active.isEmpty()?"No active boosts":String.join("\n",active);
    }
    public JSONObject json() throws JSONException {
        JSONObject o=new JSONObject(); for(int i=0;i<ends.length;i++)o.put(KEYS[i],ends[i]);
        return o.put("eventStart",eventStart).put("eventEnd",eventEnd).put("eventRate",eventRate).put("eventGroup",eventGroup)
            .put("repeatHelpers",repeatHelpers).put("apprenticeLevel",apprenticeLevel).put("labLevel",labLevel)
            .put("apprenticeReady",apprenticeReady).put("labReady",labReady);
    }
    public static BoostState read(JSONObject o) {
        BoostState s=new BoostState(); if(o==null)return s;
        for(int i=0;i<s.ends.length;i++)s.ends[i]=o.optLong(KEYS[i]);
        s.eventStart=o.optLong("eventStart");s.eventEnd=o.optLong("eventEnd");s.eventRate=o.optDouble("eventRate",1);s.eventGroup=o.optString("eventGroup","Builders");
        s.repeatHelpers=o.optBoolean("repeatHelpers");s.apprenticeLevel=o.optInt("apprenticeLevel");s.labLevel=o.optInt("labLevel");
        s.apprenticeReady=o.optLong("apprenticeReady");s.labReady=o.optLong("labReady");return s;
    }
}
