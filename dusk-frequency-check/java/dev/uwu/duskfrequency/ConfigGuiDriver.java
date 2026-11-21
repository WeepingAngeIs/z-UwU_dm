package dev.uwu.duskfrequency;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

/** Test-only native-screen input driver for the optional Unyielding settings UI. */
public final class ConfigGuiDriver {
    private static final String ADMIN = "dev.unyielding.quests.client.AdminSettingsScreen";
    private static final String LIST = "dev.unyielding.quests.client.ConfigValuesScreen";
    private static final String VALUE = LIST + "$ValueScreen";
    private static final String KEY = "uwudimension.duskVillageChancePercent";
    private static final String TITLE = "Dusk village generation chance (%)";
    private enum Phase { COMMAND, ADMIN, LIST, FILTERED, VALUE, EDITED, SAVING, DONE }

    private final int expected;
    private final int target;
    private Phase phase = Phase.COMMAND;
    private int ticks;
    private int pause;
    private String detail = "waiting to send /config";
    private String screenName = "none";

    public ConfigGuiDriver(int expected, int target) {
        if (expected < 0 || expected > 100 || target < 0 || target > 100)
            throw new IllegalArgumentException("The native UI fixture accepts percentages from 0 to 100.");
        this.expected = expected;
        this.target = target;
    }

    public boolean complete() { return phase == Phase.DONE; }

    public String status() {
        return phase + " " + expected + "->" + target + ": " + detail + "; screen=" + screenName;
    }

    public void tick(Minecraft client) throws Exception {
        if (complete()) return;
        Screen screen = client.screen;
        screenName = screen == null ? "none" : screen.getClass().getName() + " (" + screen.getTitle().getString() + ")";
        if (++ticks > 1200) throw new IllegalStateException("Native Unyielding config UI timed out: " + status());
        if (pause > 0) { pause--; return; }

        switch (phase) {
            case COMMAND -> {
                if (client.player == null || client.getConnection() == null) return;
                client.player.connection.sendCommand("config");
                advance(Phase.ADMIN, "sent real /config command");
            }
            case ADMIN -> {
                if (!is(screen, ADMIN)) return;
                click(screen, widget(screen, "Config values"));
                advance(Phase.LIST, "clicked native Config values shortcut");
            }
            case LIST -> {
                if (!is(screen, LIST)) return;
                requireRow(screen, expected);
                EditBox filter = field(screen, "Search settings");
                if (!reveal(screen, filter)) return;
                replaceText(screen, filter, KEY);
                click(screen, widget(screen, "Search"));
                advance(Phase.FILTERED, "typed the exact setting key and clicked Search");
            }
            case FILTERED -> {
                if (!is(screen, LIST)) return;
                requireRow(screen, expected);
                if (!field(screen, "Search settings").getValue().equals(KEY))
                    throw new IllegalStateException("The native settings search lost its typed filter.");
                AbstractWidget row = widget(screen, Integer.toString(expected));
                if (!reveal(screen, row)) return;
                click(screen, row);
                advance(Phase.VALUE, "clicked the filtered numeric value row");
            }
            case VALUE -> {
                if (!is(screen, VALUE)) return;
                EditBox value = field(screen, "Value");
                if (!reveal(screen, value)) return;
                if (!value.getValue().equals(Integer.toString(expected)))
                    throw new IllegalStateException("Native value editor did not open the expected percentage.");
                replaceText(screen, value, Integer.toString(target));
                advance(Phase.EDITED, "typed " + target + " in the native Value field; ready to Save");
                pause = 4; // Permit the harness to capture the real editor before Save.
            }
            case EDITED -> {
                if (!is(screen, VALUE))
                    throw new IllegalStateException("The native value editor closed before Save.");
                if (!field(screen, "Value").getValue().equals(Integer.toString(target)))
                    throw new IllegalStateException("The native value field lost the typed percentage.");
                click(screen, widget(screen, "Save"));
                advance(Phase.SAVING, "clicked native Save; awaiting the server response");
            }
            case SAVING -> {
                if (is(screen, VALUE)) {
                    boolean pending = (Boolean) read(screen, "pending");
                    String message = (String) read(screen, "message");
                    if (!pending && !message.isBlank())
                        throw new IllegalStateException("Native config Save was rejected: " + message);
                    return;
                }
                if (!is(screen, LIST)) return;
                requireRow(screen, target);
                CompoundTag snapshot = (CompoundTag) read(screen, "data");
                if (!snapshot.getString("message").startsWith("Saved."))
                    throw new IllegalStateException("The config list reopened without a server Save acknowledgement.");
                AbstractWidget row = widget(screen, Integer.toString(target));
                if (!reveal(screen, row)) return;
                advance(Phase.DONE, "native Save acknowledged; filtered row displays " + target);
            }
            case DONE -> { }
        }
    }

    private void advance(Phase next, String message) {
        phase = next;
        detail = message;
        pause = 2;
    }

    private static boolean is(Screen screen, String name) {
        return screen != null && screen.getClass().getName().equals(name);
    }

    private static AbstractWidget widget(Screen screen, String label) {
        AbstractWidget found = null;
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget candidate && !(candidate instanceof EditBox)
                    && candidate.visible && candidate.active && candidate.getMessage().getString().equals(label)) {
                if (found != null) throw new IllegalStateException("Ambiguous native widget label: " + label);
                found = candidate;
            }
        }
        if (found == null) throw new IllegalStateException("Native widget not found: " + label);
        return found;
    }

    private static EditBox field(Screen screen, String hint) {
        EditBox found = null;
        for (var child : screen.children()) {
            if (child instanceof EditBox candidate && candidate.getMessage().getString().equals(hint)) {
                if (found != null) throw new IllegalStateException("Ambiguous native field hint: " + hint);
                found = candidate;
            }
        }
        if (found == null) throw new IllegalStateException("Native field not found: " + hint);
        return found;
    }

    /** Scroll through the screen's own input path; rebuilding invalidates old widgets. */
    private boolean reveal(Screen screen, AbstractWidget widget) throws ReflectiveOperationException {
        int top = (Integer) read(screen, "top"), bottom = (Integer) read(screen, "bottom");
        if (widget.getY() >= top && widget.getY() + widget.getHeight() <= bottom) return true;
        double direction = widget.getY() < top ? 4 : -4;
        screen.mouseScrolled(screen.width / 2.0, (top + bottom) / 2.0, 0, direction);
        detail = "scrolling the native form to reveal " + widget.getMessage().getString();
        pause = 2;
        return false;
    }

    private static void click(Screen screen, AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        if (!screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT))
            throw new IllegalStateException("Native screen did not handle click: " + widget.getMessage().getString());
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** Use ordinary key/character events, never EditBox.setValue or a packet shortcut. */
    private static void replaceText(Screen screen, EditBox field, String value) {
        click(screen, field);
        screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        int length = field.getValue().length();
        for (int i = 0; i < length; i++) screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char character : value.toCharArray()) screen.charTyped(character, 0);
        if (!field.getValue().equals(value))
            throw new IllegalStateException("Native text input did not produce the requested field value.");
    }

    private static void requireRow(Screen screen, int value) throws ReflectiveOperationException {
        CompoundTag snapshot = (CompoundTag) read(screen, "data");
        for (Tag raw : snapshot.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            if (!row.getString("key").equals(KEY)) continue;
            if (!row.getString("title").equals(TITLE) || !row.getString("value").equals(Integer.toString(value)))
                throw new IllegalStateException("Unexpected native Dusk row metadata: " + row);
            return;
        }
        throw new IllegalStateException("The actual Unyielding config snapshot omitted " + KEY);
    }

    /** Reflection is read-only metadata inspection; every mutation uses native UI input. */
    private static Object read(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException absent) {
                // FormScreen owns common layout fields; subclasses own their snapshots.
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }
}
