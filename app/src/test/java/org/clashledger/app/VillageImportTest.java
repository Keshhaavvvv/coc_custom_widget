package org.clashledger.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class VillageImportTest {
    private static final long EXPORTED = 1793646016000L;
    private String fixture() throws Exception {
        try (InputStream stream=getClass().getResourceAsStream("/village-export.json")) {
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private JSONObject names() throws Exception {
        try (InputStream stream=getClass().getResourceAsStream("/item_names.json")) {
            return new JSONObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    @Test public void importsSuppliedExportShapeAndGoblinSlots() throws Exception {
        VillageImport data=new VillageImport(fixture(),names(),EXPORTED);
        assertEquals(13,data.upgrades.size());
        assertEquals(6,data.builders);
        assertEquals(16,data.townHall);
        assertTrue(data.warnings.isEmpty());
        assertEquals(EXPORTED+1608000L,data.boostUntil);
        assertEquals("Hero Hall",data.upgrades.get(0).name);
        assertEquals(10,data.upgrades.get(0).level);
        assertEquals(70043000L,data.upgrades.get(0).work);
        assertEquals(2,data.upgrades.stream().filter(u -> u.goblin).count());
        assertEquals(7,data.upgrades.stream().filter(Upgrade::isBuilder).count());
        assertEquals(3,data.upgrades.stream().filter(u -> u.group.equals("Builder Base")).count());
    }
    @Test public void snapshotAgeConsumesBoostAndDoesNotRestartTimers() throws Exception {
        VillageImport data=new VillageImport(fixture(),names(),EXPORTED+3600000L);
        Upgrade heroHall=data.upgrades.get(0);
        assertEquals(70043000L-3600000L-9*1608000L,heroHall.remaining(EXPORTED+3600000L,data.boostUntil));
        assertTrue(data.boostUntil<EXPORTED+3600000L);
        assertEquals(70043000L-9*1608000L,heroHall.wallRemaining(EXPORTED,data.boostUntil));
    }
    @Test public void builderBoostNeverAcceleratesBuilderBaseOrResearch() throws Exception {
        VillageImport data=new VillageImport(fixture(),names(),EXPORTED);
        for(Upgrade u:data.upgrades) if(!u.isBuilder()) assertEquals(u.work-60000L,u.remaining(EXPORTED+60000L,data.boostUntil));
    }
    @Test public void freshImportIsReplacementNotAnAccumulatedPotion() throws Exception {
        VillageImport first=new VillageImport(fixture(),names(),EXPORTED);
        VillageImport again=new VillageImport(fixture(),names(),EXPORTED+60000L);
        assertEquals(first.boostUntil,again.boostUntil);
        assertEquals(first.upgrades.get(0).id,again.upgrades.get(0).id);
    }
    @Test public void emptyButRecognizedExportClearsSnapshotAndAbsentBoostMeansInactive() throws Exception {
        VillageImport data=new VillageImport("{\"tag\":\"#TEST123\",\"timestamp\":1793646016,\"buildings\":[]}",names(),EXPORTED);
        assertTrue(data.upgrades.isEmpty()); assertEquals(0,data.boostUntil);
    }
    @Test public void unknownNamesKeepTimerAndFlagWarning() throws Exception {
        VillageImport data=new VillageImport(fixture(),new JSONObject(),EXPORTED);
        assertEquals(13,data.upgrades.size()); assertTrue(data.upgrades.get(0).name.contains("1000071")); assertFalse(data.warnings.isEmpty());
    }
    @Test public void rejectsMissingSchemaNegativeTimerAndFutureSnapshot() throws Exception {
        String[] invalid={"{}", "[]", fixture().replace("70043", "-1"), fixture().replace("1793646016","1794646016"), fixture()+" garbage", fixture().replace("70043", "1.5")};
        for(String text:invalid) {
            try { new VillageImport(text,names(),EXPORTED); fail("Accepted invalid export"); }
            catch(org.json.JSONException | IllegalArgumentException expected) { }
        }
    }
    @Test public void importsResearchBoostAtItsOwnRate() throws Exception {
        JSONObject root=new JSONObject(fixture()); root.getJSONObject("boosts").put("lab_boost",1000);
        VillageImport data=new VillageImport(root.toString(),names(),EXPORTED);
        assertEquals(EXPORTED+1000000L,data.boosts.ends[0]);
        Upgrade lab=data.upgrades.stream().filter(u->u.group.equals("Laboratory")).findFirst().get();
        assertEquals(lab.work-24000,lab.remaining(EXPORTED+1000,data.boostUntil));
    }
    @Test public void importsTownHallLevelsOneThroughEighteenWithoutChangingTimerMath() throws Exception {
        for(int level=1;level<=18;level++) {
            JSONObject root=new JSONObject(fixture());
            root.getJSONArray("buildings").getJSONObject(0).put("lvl",level);
            VillageImport data=new VillageImport(root.toString(),names(),EXPORTED);
            assertEquals(level,data.townHall);
            assertEquals(13,data.upgrades.size());
            assertEquals(70043000L-9*1608000L,data.upgrades.get(0).wallRemaining(EXPORTED,data.boostUntil));
        }
    }
}
