package org.clashledger.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReminderPlanTest {
    private static final long NOW=1_000_000L;
    private ReminderPlan.Event event(String key,long remaining) { return new ReminderPlan.Event(key,"Ready","Details",remaining); }
    @Test public void enablesOnlyFutureTimersWithoutFloodingOldCompletions() {
        ReminderPlan p=new ReminderPlan(Arrays.asList(event("old",0),event("future",60000)),Map.of(),NOW,false);
        assertTrue(p.due.isEmpty()); assertEquals(Map.of("future",NOW+60000),p.armed); assertEquals(NOW+60000,p.next);
    }
    @Test public void dueTimerDeliversOnceEvenIfReceiverRunsAgain() {
        ReminderPlan p=new ReminderPlan(List.of(event("one",0)),Map.of("one",NOW-1),NOW,true);
        assertEquals(1,p.due.size()); assertTrue(p.armed.isEmpty());
        assertTrue(new ReminderPlan(List.of(event("one",0)),p.armed,NOW+100,true).due.isEmpty());
    }
    @Test public void recordingPotionReschedulesEarlierWithoutSendingStaleAlert() {
        ReminderPlan p=new ReminderPlan(List.of(event("one",60000)),Map.of("one",NOW+3600000),NOW,false);
        assertEquals(Long.valueOf(NOW+60000),p.armed.get("one")); assertTrue(p.due.isEmpty());
    }
    @Test public void extendingTimerDoesNotDeliverItsPreviouslyDueReminder() {
        ReminderPlan p=new ReminderPlan(List.of(event("one",60000)),Map.of("one",NOW-1000),NOW,true);
        assertTrue(p.due.isEmpty()); assertEquals(NOW+60000,p.next);
    }
    @Test public void deletionCategoryDisableAndVillageSwitchRemoveOldAlarms() {
        ReminderPlan p=new ReminderPlan(List.of(),Map.of("gone",NOW-1000),NOW,true);
        assertTrue(p.armed.isEmpty()); assertTrue(p.due.isEmpty()); assertEquals(Long.MAX_VALUE,p.next);
    }
    @Test public void manualCompletionDoesNotTriggerPrematureAlarm() {
        ReminderPlan p=new ReminderPlan(List.of(event("one",0)),Map.of("one",NOW+60000),NOW,true);
        assertTrue(p.due.isEmpty());
    }
    @Test public void lateWakeOrRebootDeliversAllPreviouslyArmedCompletions() {
        ReminderPlan p=new ReminderPlan(Arrays.asList(event("a",0),event("b",0)),Map.of("a",NOW-300000,"b",NOW-60000),NOW,true);
        assertEquals(2,p.due.size()); assertTrue(p.armed.isEmpty());
    }
    @Test public void completedSnapshotReplacementDropsPriorSchedule() {
        ReminderPlan p=new ReminderPlan(List.of(event("one",0)),Map.of("one",NOW-1),NOW,false);
        assertTrue(p.due.isEmpty()); assertTrue(p.armed.isEmpty());
    }
    @Test public void categoriesAndPotionAreIndependentAndBoostChangesIdentity() {
        Upgrade builder=new Upgrade("a","Tower","Builders",2,3600000,NOW);
        Upgrade pet=new Upgrade("b","Diggy","Pets",8,3600000,NOW);
        List<ReminderPlan.Event> events=ReminderPlan.events(Arrays.asList(builder,pet),"#A",NOW+60000,NOW,Set.of("Pets"),true);
        assertEquals(2,events.size()); assertEquals(3600000,events.get(0).remaining); assertEquals(60000,events.get(1).remaining);
        assertNotEquals(events.get(1).key,ReminderPlan.events(List.of(),"#A",NOW+120000,NOW,Set.of(),true).get(0).key);
    }
    @Test public void everyVillageHasSeparateReminderKeys() {
        Upgrade u=new Upgrade("a","Tower","Builders",2,60000,NOW);
        String a=ReminderPlan.events(List.of(u),"#A",0,NOW,Set.of("Builders"),false).get(0).key;
        String b=ReminderPlan.events(List.of(u),"#B",0,NOW,Set.of("Builders"),false).get(0).key;
        assertNotEquals(a,b);
    }
}
