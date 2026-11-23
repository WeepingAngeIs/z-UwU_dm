package dev.uwu.pocketcheck;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.TimePocket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(value = "uwu_pocket_check", dist = Dist.DEDICATED_SERVER)
public final class PocketDedicatedProbe {
    private static final String FREEZE = "uwu uwuzi freezeseconds";
    private static final String CHARM = "uwu cupid charmseconds";

    public PocketDedicatedProbe() {
        CandidateJarProbe.verify();
        NeoForge.EVENT_BUS.addListener(PocketDedicatedProbe::started);
    }

    private static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (TimePocket.field(server.overworld()) != null || TimePocket.CLOCK.get().getDefaultInstance().isEmpty())
            throw new AssertionError("Unexpected dedicated startup state");
        var console = server.createCommandSourceStack();
        try {
            run(console, FREEZE);
            run(console, CHARM);
            run(console, "uwuclock negativeColors false");
            run(console, "uwuclock negativeColors true");
            if ("write".equals(System.getProperty("uwu.pocket.stage"))) {
                requireTimers(4, 40);
                reject(console, FREEZE + " 0");
                reject(console, FREEZE + " 1.5");
                reject(console, FREEZE + " 107374183");
                reject(console.withPermission(1), FREEZE + " 7");
                run(console, CHARM + " 9");
                requireTimers(4, 9);
                run(console, FREEZE + " 107374182");
                requireTimers(107374182, 9);
                run(console, FREEZE + " 7");
                requireTimers(7, 9);
                System.out.println("UWUZI DEDICATED WRITE PASS");
            } else if ("restart".equals(System.getProperty("uwu.pocket.stage"))) {
                requireTimers(7, 9);
                System.out.println("UWUZI DEDICATED RESTART PASS");
            } else {
                throw new AssertionError("Unknown dedicated server test stage");
            }
        } catch (CommandSyntaxException ex) { throw new AssertionError(ex); }
        server.halt(false);
    }

    private static void requireTimers(int freeze, int charm) {
        if (AcquisitionPolicy.uwuziFreezeSeconds() != freeze || AcquisitionPolicy.cupidBowCharmSeconds() != charm)
            throw new AssertionError("Unexpected settings, expected " + freeze + " and " + charm);
    }

    private static void run(CommandSourceStack source, String command) throws CommandSyntaxException {
        if (source.getServer().getCommands().getDispatcher().execute(command, source) != 1)
            throw new AssertionError(command);
    }

    private static void reject(CommandSourceStack source, String command) {
        try { run(source, command); }
        catch (CommandSyntaxException expected) { return; }
        throw new AssertionError("Unexpectedly accepted " + command);
    }
}

