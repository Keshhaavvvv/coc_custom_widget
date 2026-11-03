package org.clashledger.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpgradeTest {
    private static final long H = 3600000L;
    private Upgrade builder(long hours) { return new Upgrade("1","Archer Tower","Builders",21,hours*H,0); }
    @Test public void unboostedTimePassesNormally() {
        Upgrade u = builder(24); assertEquals(23*H,u.remaining(H,0)); assertEquals(23*H,u.wallRemaining(H,0));
    }
    @Test public void potionSavesNineHoursAfterOneHour() {
        Upgrade u = builder(24); assertEquals(15*H,u.wallRemaining(0,H)); assertEquals(14*H,u.remaining(H,H)); assertEquals(14*H,u.wallRemaining(H,H));
    }
    @Test public void shortUpgradeFinishesDuringBoost() {
        Upgrade u = builder(5); assertEquals(H/2,u.wallRemaining(0,H)); assertEquals(0,u.remaining(H/2,H)); assertEquals(0,u.wallRemaining(H,H));
    }
    @Test public void exactBoostBoundary() { Upgrade u = builder(10); assertEquals(H,u.wallRemaining(0,H)); assertEquals(0,u.remaining(H,H)); }
    @Test public void labsAndPetsAreNeverBuilderBoosted() {
        for(String group : new String[]{"Laboratory","Pets"}) { Upgrade u = new Upgrade("1","Dragon",group,12,24*H,0); assertEquals(23*H,u.remaining(H,H)); assertEquals(24*H,u.wallRemaining(0,H)); }
    }
    @Test public void stackedPotionsExtendDurationNotMultiplier() { Upgrade u=builder(30); assertEquals(12*H,u.wallRemaining(0,2*H)); assertEquals(10*H,u.remaining(2*H,2*H)); }
    @Test public void recordingPotionMidUpgradePreservesElapsedWork() {
        Upgrade u=builder(24); u.settle(2*H,0); assertEquals(22*H,u.work); assertEquals(13*H,u.wallRemaining(2*H,3*H));
    }
    @Test public void endingPotionEarlyPreservesEarnedBoost() {
        Upgrade u=builder(24); u.settle(H/2,H); assertEquals(19*H,u.work); assertEquals(19*H,u.wallRemaining(H/2,H/2));
    }
    @Test public void newUpgradeDuringBoostUsesOnlyRemainingBoost() {
        Upgrade u = new Upgrade("1","Tower","Builders",2,10*H,H/2); assertEquals(11*H/2,u.wallRemaining(H/2,H));
    }
    @Test public void predictedFinishRemainsConstantAcrossExpiration() {
        Upgrade u=builder(24); long finish=u.wallRemaining(0,H);
        for(long now : new long[]{H/2,H,2*H}) assertEquals(finish,now+u.wallRemaining(now,H));
    }
    @Test public void completeProgressIsClampedAndClockRollbackDoesNotAddWork() {
        Upgrade u=builder(1); assertEquals(1000,u.progress(2*H,0)); assertEquals(H,u.remaining(-H,0)); assertEquals(0,u.progress(-H,0));
    }
}
