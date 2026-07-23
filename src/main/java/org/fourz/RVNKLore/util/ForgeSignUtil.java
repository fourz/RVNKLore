package org.fourz.RVNKLore.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;

/**
 * Helpers for the "[Forge]" anvil designation: a sign carrying the {@code [Forge]} tag
 * on any face of an anvil marks that anvil so a player right-clicking it with an item in
 * hand publishes that item into the RVNKLore catalog. Mirrors {@link LecternSignUtil}
 * (the {@code [Library]} lectern designation) but targets anvils.
 *
 * <p>See {@code HandlerSignForge} (the sign gate) and {@code ForgeAnvilLoreHandler}
 * (the right-click trigger). Reuses {@link LecternSignUtil#getAttachedBlock(Block)} for
 * sign-mount resolution.</p>
 */
public final class ForgeSignUtil {

    /** Sign line-0 tag that designates a forge anvil. */
    public static final String FORGE_TAG = "[Forge]";

    private static final BlockFace[] ADJACENT = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private ForgeSignUtil() {}

    /** @return true if the material is any of the three vanilla anvil variants. */
    public static boolean isAnvil(Material material) {
        return material == Material.ANVIL
            || material == Material.CHIPPED_ANVIL
            || material == Material.DAMAGED_ANVIL;
    }

    /**
     * Finds a {@code [Forge]} sign mounted on the given anvil. Scans the six adjacent
     * blocks; a neighbour qualifies only if it is a sign whose front or back line 0 is
     * {@code [Forge]} and which is mounted on this anvil (or whose mount cannot be
     * resolved — stays lenient for unusual sign types).
     *
     * @param anvil the anvil block to test
     * @return the designating sign block, or null if the anvil is not designated
     */
    public static Block findForgeSign(Block anvil) {
        if (anvil == null || !isAnvil(anvil.getType())) return null;
        for (BlockFace face : ADJACENT) {
            Block neighbor = anvil.getRelative(face);
            BlockState state = neighbor.getState();
            if (!(state instanceof Sign sign)) continue;
            if (!hasForgeTag(sign)) continue;
            Block attached = LecternSignUtil.getAttachedBlock(neighbor);
            if (attached == null || attached.equals(anvil)) {
                return neighbor;
            }
        }
        return null;
    }

    /** @return true if the anvil has an attached {@code [Forge]} sign. */
    public static boolean isForgeAnvil(Block anvil) {
        return findForgeSign(anvil) != null;
    }

    /** @return true if either face of the sign carries the {@code [Forge]} tag on line 0. */
    private static boolean hasForgeTag(Sign sign) {
        return tagOnSide(sign, Side.FRONT) || tagOnSide(sign, Side.BACK);
    }

    private static boolean tagOnSide(Sign sign, Side side) {
        try {
            String line0 = ChatColor.stripColor(sign.getSide(side).getLine(0));
            return line0 != null && line0.trim().equalsIgnoreCase(FORGE_TAG);
        } catch (Throwable ignored) {
            // Older/newer sign API shape — treat as no tag rather than fail the placement.
            return false;
        }
    }
}
