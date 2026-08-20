package org.fourz.RVNKLore.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;

/**
 * Helpers for sign-on-lectern designations.
 *
 * <p>A sign carrying a recognised tag on any face of a lectern designates that lectern. Two tags
 * exist and they point in opposite directions:</p>
 *
 * <ul>
 *   <li>{@code [Library]} — <b>ingestion</b>. Books <i>placed on</i> the lectern are catalogued as
 *       lore. See {@code HandlerSignLibrary} and {@code LecternBookLoreHandler}.</li>
 *   <li>{@code [Tome]} — <b>dispensing</b> (#1888). Interacting <i>takes</i> a copy of a named lore
 *       book. See {@code HandlerSignTome}.</li>
 * </ul>
 *
 * <p>The scanning logic is tag-agnostic — {@link #findTaggedSign} does the fiddly part (all six
 * neighbours, both sign faces, lenient when a mount cannot be resolved) and the tag is a parameter.
 * The {@code [Library]} wrappers are kept so existing callers are untouched.</p>
 *
 * <p><b>The physical sign IS the designation.</b> There is no registry and nothing to drift:
 * removing the sign un-designates the lectern.</p>
 */
public final class LecternSignUtil {

    /** Sign line-0 tag that designates a lore-cataloguing (ingesting) lectern. */
    public static final String LIBRARY_TAG = "[Library]";

    /** Sign line-0 tag that designates a book-dispensing (quest-giver) lectern (#1888). */
    public static final String TOME_TAG = "[Tome]";

    private static final BlockFace[] ADJACENT = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private LecternSignUtil() {}

    /**
     * Finds a {@code [Library]} sign mounted on the given lectern. Scans the six adjacent
     * blocks; a neighbour qualifies only if it is a sign whose front or back line 0 is
     * {@code [Library]} and which is mounted on this lectern (or whose mount cannot be
     * resolved — stays lenient for unusual sign types).
     *
     * @param lectern the lectern block to test
     * @return the designating sign block, or null if the lectern is not designated
     */
    public static Block findLibrarySign(Block lectern) {
        return findTaggedSign(lectern, LIBRARY_TAG);
    }

    /** @return true if the lectern has an attached {@code [Library]} sign. */
    public static boolean isLibraryLectern(Block lectern) {
        return findLibrarySign(lectern) != null;
    }

    /** @return the {@code [Tome]} sign designating this lectern as a dispenser, or null (#1888). */
    public static Block findTomeSign(Block lectern) {
        return findTaggedSign(lectern, TOME_TAG);
    }

    /** @return true if the lectern has an attached {@code [Tome]} sign (#1888). */
    public static boolean isTomeLectern(Block lectern) {
        return findTomeSign(lectern) != null;
    }

    /**
     * Finds a sign carrying {@code tag} on line 0 that is mounted on the given lectern.
     *
     * <p>Scans the six neighbours; a neighbour qualifies only if it is a sign whose front <b>or</b>
     * back line 0 matches, and which is mounted on this lectern — or whose mount cannot be resolved,
     * which stays lenient for unusual sign types rather than silently failing to designate.</p>
     *
     * @param lectern the lectern block to test
     * @param tag     the line-0 tag, compared case-insensitively
     * @return the designating sign block, or null
     */
    public static Block findTaggedSign(Block lectern, String tag) {
        if (lectern == null || lectern.getType() != Material.LECTERN || tag == null) return null;
        for (BlockFace face : ADJACENT) {
            Block neighbor = lectern.getRelative(face);
            BlockState state = neighbor.getState();
            if (!(state instanceof Sign sign)) continue;
            if (!hasTag(sign, tag)) continue;
            Block attached = getAttachedBlock(neighbor);
            if (attached == null || attached.equals(lectern)) {
                return neighbor;
            }
        }
        return null;
    }

    /** @return true if either face of the sign carries {@code tag} on line 0. */
    private static boolean hasTag(Sign sign, String tag) {
        return tagOnSide(sign, Side.FRONT, tag) || tagOnSide(sign, Side.BACK, tag);
    }

    /**
     * Reads a line from one face of a sign.
     *
     * @param sign the sign
     * @param side which face
     * @param index line index 0-3
     * @return the line with colour stripped and trimmed, or null when unreadable
     */
    public static String readLine(Sign sign, Side side, int index) {
        try {
            String line = ChatColor.stripColor(sign.getSide(side).getLine(index));
            return line == null ? null : line.trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Reads a line from whichever face carries {@code tag} on line 0.
     *
     * <p>A sign can be written on either face, so the payload (e.g. the book name on line 1) must be
     * read from the <i>same</i> face that carries the tag — reading a fixed side would return the
     * blank reverse of a back-written sign.</p>
     *
     * @return the trimmed line, or null when no tagged face has content there
     */
    public static String readLineFromTaggedSide(Sign sign, String tag, int index) {
        for (Side side : new Side[] { Side.FRONT, Side.BACK }) {
            if (tagOnSide(sign, side, tag)) {
                String v = readLine(sign, side, index);
                if (v != null && !v.isEmpty()) return v;
            }
        }
        return null;
    }

    /** Payload lines on a tagged sign: everything below the tag on line 0. */
    public static final int NAME_FIRST_LINE = 1;
    public static final int NAME_LAST_LINE = 3;

    /**
     * Reads a payload name spanning lines 1-3 of whichever face carries {@code tag} (#2021).
     *
     * <p>A sign line holds roughly fifteen rendered characters, so a name like
     * {@code RAVENFORGE WAYBILL} (18) cannot be typed onto one line at all — the client stops at
     * {@code RAVENFORGE WAYBI}. Joining the three payload lines with single spaces lifts the
     * ceiling to about forty-five characters and costs nothing: a one-line name yields exactly
     * itself, because the empty lines below contribute nothing to the join.</p>
     *
     * <p>Use this instead of {@code readLineFromTaggedSide(sign, tag, 1)} anywhere a lore item
     * name is resolved. Resolution is by exact name, so the join must preserve internal spacing
     * and case — hence single spaces and no case folding.</p>
     *
     * @param sign the sign to read
     * @param tag  the line-0 tag identifying which face carries the payload
     * @return the joined name, or null when no tagged face has anything below the tag
     */
    public static String readNameFromTaggedSide(Sign sign, String tag) {
        for (Side side : new Side[] { Side.FRONT, Side.BACK }) {
            if (!tagOnSide(sign, side, tag)) continue;
            StringBuilder joined = new StringBuilder();
            for (int i = NAME_FIRST_LINE; i <= NAME_LAST_LINE; i++) {
                String part = readLine(sign, side, i);
                if (part == null || part.isEmpty()) continue;
                if (joined.length() > 0) joined.append(' ');
                joined.append(part);
            }
            if (joined.length() > 0) return joined.toString();
        }
        return null;
    }

    /**
     * Joins the payload lines of a {@link org.bukkit.event.block.SignChangeEvent} (#2021).
     *
     * <p>The event carries the lines the player just typed; the block's own state is not committed
     * until the event resolves, so a sign being created cannot be read through {@link Sign}. Same
     * join rule as {@link #readNameFromTaggedSide} so create-time and read-time agree — if they
     * ever diverged, a sign would designate under one name and dispense under another.</p>
     *
     * @param lines the event's four lines, any of which may be null
     * @return the joined name, empty when nothing was typed below the tag
     */
    public static String joinNameLines(String[] lines) {
        if (lines == null) return "";
        StringBuilder joined = new StringBuilder();
        for (int i = NAME_FIRST_LINE; i <= NAME_LAST_LINE && i < lines.length; i++) {
            String part = lines[i] == null ? "" : ChatColor.stripColor(lines[i]).trim();
            if (part.isEmpty()) continue;
            if (joined.length() > 0) joined.append(' ');
            joined.append(part);
        }
        return joined.toString();
    }

    /**
     * Word-wraps a name across the three payload lines so it stays legible (#2021).
     *
     * <p>Writing a long name onto line 1 alone stores it correctly but renders clipped, and a
     * quest-giver nobody can read defeats the reason the name is on the sign. Wrapping is the
     * write-side twin of {@link #readNameFromTaggedSide}: what is written is what reads back.</p>
     *
     * <p>A single word longer than one line is left to clip — there is nowhere else for it to go,
     * and breaking mid-word would corrupt the name on the round trip.</p>
     *
     * @param name the full name
     * @return exactly three lines, blank-padded
     */
    public static String[] wrapNameOntoLines(String name) {
        String[] out = { "", "", "" };
        if (name == null || name.isBlank()) return out;

        int line = 0;
        StringBuilder current = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= SIGN_LINE_CHARS) {
                current.append(' ').append(word);
            } else {
                if (line == out.length - 1) {
                    // Out of lines: keep the remainder on the last one rather than dropping it.
                    current.append(' ').append(word);
                } else {
                    out[line++] = current.toString();
                    current = new StringBuilder(word);
                }
            }
        }
        out[line] = current.toString();
        return out;
    }

    /** Rendered characters that reliably fit on one sign line. */
    private static final int SIGN_LINE_CHARS = 15;

    private static boolean tagOnSide(Sign sign, Side side, String tag) {
        String line0 = readLine(sign, side, 0);
        return line0 != null && line0.equalsIgnoreCase(tag);
    }

    /**
     * Resolves the block a sign is mounted on.
     *
     * @param signBlock a block whose state is a sign
     * @return the supporting block, or null if the sign type is unrecognised
     */
    public static Block getAttachedBlock(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        if (data instanceof WallSign wallSign) {
            return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        }
        if (data instanceof WallHangingSign wallHanging) {
            return signBlock.getRelative(wallHanging.getFacing().getOppositeFace());
        }
        if (data instanceof HangingSign) {
            // Ceiling hanging sign hangs from the block directly above it.
            return signBlock.getRelative(BlockFace.UP);
        }
        if (data instanceof org.bukkit.block.data.type.Sign) {
            // Standing/floor sign sits on the block directly below it.
            return signBlock.getRelative(BlockFace.DOWN);
        }
        return null;
    }
}
