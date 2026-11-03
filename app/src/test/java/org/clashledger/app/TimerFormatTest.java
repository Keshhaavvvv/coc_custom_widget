package org.clashledger.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimerFormatTest {
    @Test public void clockAlwaysIncludesSecondsAndTotalHours() {
        assertEquals("27:05:12",TimerFormat.format((24*3600L+3*3600+5*60+12)*1000,"clock"));
        assertEquals("1:00:00",TimerFormat.format(3600000,"clock"));
        assertEquals("0:00:12",TimerFormat.format(12000,"clock"));
    }
    @Test public void positiveFractionsNeverShowReadyOrZeroSecondsPrematurely() {
        assertEquals("0:00:01",TimerFormat.format(1,"clock"));
        for(String style:new String[]{"compact","words","clock"}) {
            assertEquals("Ready",TimerFormat.format(0,style));
            assertEquals("Ready",TimerFormat.format(-1,style));
        }
    }
    @Test public void clockSecondsCarryAcrossMinuteHourAndDayBoundaries() {
        assertEquals("0:01:00",TimerFormat.format(59999,"clock"));
        assertEquals("1:00:00",TimerFormat.format(3599999,"clock"));
        assertEquals("24:00:00",TimerFormat.format(86399999,"clock"));
        assertEquals("48:00:00",TimerFormat.format(172800000,"clock"));
    }
    @Test public void wordFormatsUseRoundedMinutesWithoutASecondCountdown() {
        long time=(24*3600L+3*3600+5*60+12)*1000;
        assertEquals("1d 3h 6m",TimerFormat.format(time,"compact"));
        assertEquals("1 day 3 hr 6 min",TimerFormat.format(time,"words"));
        assertEquals("1m",TimerFormat.format(1,"compact"));
        assertEquals("1 min",TimerFormat.format(1,"words"));
    }
}
