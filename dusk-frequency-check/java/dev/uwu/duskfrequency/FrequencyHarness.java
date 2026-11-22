package dev.uwu.duskfrequency;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(value="uwu_dusk_frequency_check", dist=Dist.DEDICATED_SERVER)
public final class FrequencyHarness {
    private final FrequencyServer probe = new FrequencyServer();
    private boolean done;
    public FrequencyHarness() {
        CandidateJarProbe.verify();
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ServerTickEvent.Post event) {
        if (done) return;
        try { done = probe.tick(event.getServer()); }
        catch (Throwable failure) {
            done = true;
            System.err.println("DUSK FREQUENCY CHECK FAILED stage="+System.getProperty("uwu.check.frequencyStage"));
            failure.printStackTrace();
        }
        if (done) event.getServer().halt(false);
    }
}
