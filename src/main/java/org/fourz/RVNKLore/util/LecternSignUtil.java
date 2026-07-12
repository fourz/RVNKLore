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
 * Helpers for the "[Library]" lore-lectern designation: a sign carrying the
 * {@code [Library]} tag on any face of a lectern marks that lectern so books placed on
 * it are catalogued as lore. See {@code HandlerSignLibrary} (the sign gate) and
 * {@code LecternBookLoreHandler} (the book-placement trigger).
 */
public final class LecternSignUtil {

    /** Sign line-0 tag that designates a lore-cataloguing lectern. */
    public static final String LIBRARY_TAG = "[Library]";

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
        if (lectern == null || lectern.getType() != Material.LECTERN) return null;
        for (BlockFace face : ADJACENT) {
            Block neighbor = lectern.getRelative(face);
            BlockState state = neighbor.getState();
            if (!(state instanceof Sign sign)) continue;
            if (!hasLibraryTag(sign)) continue;
            Block attached = getAttachedBlock(neighbor);
            if (attached == null || attached.equals(lectern)) {
                return neighbor;
            }
        }
        return null;
    }

    /** @return true if the lectern has an attached {@code [Library]} sign. */
    public static boolean isLibraryLectern(Block lectern) {
        return findLibrarySign(lectern) != null;
    }

    /** @return true if either face of the sign carries the {@code [Library]} tag on line 0. */
    private static boolean hasLibraryTag(Sign sign) {
        return tagOnSide(sign, Side.FRONT) || tagOnSide(sign, Side.BACK);
    }

    private static boolean tagOnSide(Sign sign, Side side) {
        try {
            String line0 = ChatColor.stripColor(sign.getSide(side).getLine(0));
            return line0 != null && line0.trim().equalsIgnoreCase(LIBRARY_TAG);
        } catch (Throwable ignored) {
            // Older/newer sign API shape — treat as no tag rather than fail the placement.
            return false;
        }
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
