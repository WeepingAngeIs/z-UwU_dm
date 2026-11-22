package dev.uwu.lotusclientcheck;

import net.minecraft.world.phys.Vec3;

/** Exercises the same move samples and interpolated coordinates used by all companion effects. */
final class CompanionMotionProbe {
    static void verify() throws ReflectiveOperationException {
        Class<?> type = Class.forName("dev.uwu.dimension.RevivalBlossomMotion");
        var constructor = type.getDeclaredConstructor(Vec3.class, Vec3.class, int.class);
        var follow = type.getDeclaredMethod("follow", Vec3.class, Vec3.class, int.class, float.class);
        var sample = type.getDeclaredMethod("sample", float.class);
        var reset = type.getDeclaredMethod("reset", Vec3.class, Vec3.class, int.class, int.class);
        constructor.setAccessible(true);
        follow.setAccessible(true);
        sample.setAccessible(true);
        reset.setAccessible(true);

        var motion = constructor.newInstance(Vec3.ZERO, Vec3.ZERO, 0);
        Vec3 firstOwner = new Vec3(.20, 0, 0);
        Vec3 secondOwner = new Vec3(.40, 0, 0);
        follow.invoke(motion, firstOwner, firstOwner, 1, 1F);
        double first = ((Vec3) sample.invoke(motion, 1F)).x;
        require(first > 0 && first < .20, "first step must leave a trailing distance");
        follow.invoke(motion, secondOwner, secondOwner, 2, 1F);
        Vec3 second = (Vec3) sample.invoke(motion, 1F);
        require(second.x - first > first && second.x < .40, "companion must accelerate toward a moving owner");
        require(near((Vec3) sample.invoke(motion, .5F), new Vec3(first, 0, 0).lerp(second, .5)), "render interpolation");
        follow.invoke(motion, secondOwner, secondOwner, 2, 1F);
        require(near((Vec3) sample.invoke(motion, 1F), second), "multiple consumers advanced the same tick");

        for (int tick = 3; tick <= 10; tick++) {
            Vec3 owner = new Vec3(tick * .20, 0, 0);
            follow.invoke(motion, owner, owner, tick, 1F);
        }
        double atStop = ((Vec3) sample.invoke(motion, 1F)).x;
        Vec3 rest = new Vec3(2, 0, 0);
        follow.invoke(motion, rest, rest, 11, 1F);
        double coasting = ((Vec3) sample.invoke(motion, 1F)).x;
        require(atStop < coasting && coasting < rest.x, "companion should coast when the player stops");
        for (int tick = 12; tick <= 64; tick++) follow.invoke(motion, rest, rest, tick, 1F);
        require(near((Vec3) sample.invoke(motion, 1F), rest), "companion did not settle at its resting offset");

        for (int tick = 65; tick <= 75; tick++) {
            Vec3 owner = rest.add((tick - 64) * 1.10, (tick - 64) * .55, 0);
            follow.invoke(motion, owner, owner, tick, 1F);
            Vec3 lag = ((Vec3) sample.invoke(motion, 1F)).subtract(owner);
            require(lag.horizontalDistance() <= 3.500001 && Math.abs(lag.y) <= .160001, "follow lag exceeded limits");
        }
        Vec3 destination = new Vec3(400, 90, -220);
        follow.invoke(motion, destination, destination, 76, 1F);
        require(near((Vec3) sample.invoke(motion, 0F), destination)
                && near((Vec3) sample.invoke(motion, 1F), destination), "discontinuous movement left old render history");
        Vec3 shortTeleport = destination.add(.25, 0, 0);
        reset.invoke(motion, shortTeleport, shortTeleport, 76, 0);
        require(near((Vec3) sample.invoke(motion, .25F), shortTeleport), "explicit position correction did not clear interpolation");

        reset.invoke(motion, shortTeleport, shortTeleport, 76, 3);
        for (int tick = 77; tick <= 79; tick++) {
            Vec3 owner = shortTeleport.add((tick - 76) * 2, 0, 0);
            follow.invoke(motion, owner, owner, tick, 1F);
            require(near((Vec3) sample.invoke(motion, 1F), owner), "remote relocation was damped a second time");
        }
        Vec3 nextOwner = shortTeleport.add(6.2, 0, 0);
        follow.invoke(motion, nextOwner, nextOwner, 80, 1F);
        require(((Vec3) sample.invoke(motion, 1F)).x < shortTeleport.x + 6.2, "momentum did not resume after relocation");

        Vec3 eastAnchor = new Vec3(1, 2, 0);
        Vec3 westAnchor = new Vec3(-1, 2, 0);
        var turning = constructor.newInstance(eastAnchor, Vec3.ZERO, 0);
        follow.invoke(turning, westAnchor, Vec3.ZERO, 1, 1F);
        Vec3 afterTurn = (Vec3) sample.invoke(turning, 1F);
        require(afterTurn.x < 1 && afterTurn.x > .75, "turn snapped instead of carrying momentum around the owner");
        for (int tick = 2; tick <= 64; tick++) follow.invoke(turning, westAnchor, Vec3.ZERO, tick, 1F);
        require(near((Vec3) sample.invoke(turning, 1F), westAnchor), "turn did not settle at the original offset");

        Vec3 sprintOffset = new Vec3(-1.05, 2, -.55);
        var sprinting = constructor.newInstance(sprintOffset, Vec3.ZERO, 0);
        for (int tick = 1; tick <= 32; tick++) {
            Vec3 owner = new Vec3(0, 0, .28 * tick);
            follow.invoke(sprinting, owner.add(sprintOffset), owner, tick, 1F);
        }
        Vec3 preTurn = (Vec3) sample.invoke(sprinting, 1F);
        Vec3 turnOwner = new Vec3(.24, 0, 9.10);
        Vec3 turnGoal = turnOwner.add(-.55, 2, 1.05);
        follow.invoke(sprinting, turnGoal, turnOwner, 33, 1F);
        Vec3 turnSample = (Vec3) sample.invoke(sprinting, 1F);
        require(turnSample.subtract(preTurn).horizontalDistance() < .45
                        && turnSample.subtract(turnGoal).horizontalDistance() > 2.5,
                "a sharp sprint turn must preserve momentum when its goal moves far from the current blossom");
        System.out.println("LOTUS COMPANION MOMENTUM PASS acceleration, coast, turn, settle, interpolation and relocation");
    }

    private static boolean near(Vec3 actual, Vec3 expected) { return actual.distanceToSqr(expected) < 1e-10; }

    private static void require(boolean valid, String problem) {
        if (!valid) throw new AssertionError(problem);
    }

    private CompanionMotionProbe() {}
}
