package dev.uwu.pocketcheck;

import dev.uwu.dimension.TimePocket;
import dev.uwu.lotusclientcheck.CandidateJarProbe;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(value = "uwu_pocket_check", dist = Dist.DEDICATED_SERVER)
public final class PocketDedicatedProbe {
    public PocketDedicatedProbe() {
        CandidateJarProbe.verify();
        NeoForge.EVENT_BUS.addListener(PocketDedicatedProbe::started);
    }

    private static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (TimePocket.field(server.overworld()) != null || TimePocket.CLOCK.get().getDefaultInstance().isEmpty())
            throw new AssertionError("Unexpected dedicated startup state");
        var command = server.getCommands();
        if (command.getDispatcher().getRoot().getChild("uwuclock") == null) throw new AssertionError("Missing operator command");
        try {
            var dispatcher = command.getDispatcher();
            if (dispatcher.execute("uwuclock negativeColors false", server.createCommandSourceStack()) != 1 ||
                dispatcher.execute("uwuclock negativeColors true", server.createCommandSourceStack()) != 1)
                throw new AssertionError("Server toggle failed");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) { throw new AssertionError(ex); }
        System.out.println("TIME POCKET DEDICATED PASS");
        server.halt(false);
    }
}
