package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.RVNKLore.util.LecternSignUtil;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists sign-designated lecterns and where they are.
 *
 * <p>Usage: {@code /lore lectern [list] [world]}</p>
 *
 * <h3>Why this scans instead of reading a registry</h3>
 * {@link LecternSignUtil} states the rule this command is built to respect: <i>the physical sign IS
 * the designation. There is no registry and nothing to drift.</i> A stored table of lectern
 * locations would start disagreeing with the world the first time somebody broke a sign, and the
 * disagreement would be invisible - which is the failure mode #1614 recorded for portals, where a
 * reverted sign kept its id and the DB row and the world drifted apart silently.
 *
 * <p>So this reads the world. It cannot report a lectern that is not there, and it cannot miss one
 * that is - within the chunks it can see.</p>
 *
 * <h3>The limit, stated in the output</h3>
 * Only LOADED chunks can be scanned; an unloaded chunk has no tile entities in memory to inspect.
 * That means an empty result is not proof of absence, so the footer always prints how many chunks
 * were actually examined. A count of zero examined chunks reads very differently from zero lecterns
 * found in nine hundred, and collapsing the two is how a silent read gets mistaken for a clean one.
 *
 * @since 1.0.124
 */
public class LoreLecternSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private static final int ITEMS_PER_PAGE = 10;

    public LoreLecternSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreLecternSubCommand");
    }

    /** One designated lectern found in the world. */
    private record Found(String world, int x, int y, int z, String tag, String bookName) {}

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Accept both "/lore lectern" and "/lore lectern list [world]" so the verb is optional.
        List<String> rest = new ArrayList<>();
        for (String a : args) {
            if (!a.equalsIgnoreCase("list")) {
                rest.add(a);
            }
        }

        String worldFilter = null;
        int page = 1;
        boolean diag = false;
        rest.removeIf(a -> a.equalsIgnoreCase("--diag"));
        for (String a : args) {
            if (a.equalsIgnoreCase("--diag")) diag = true;
        }
        for (String a : rest) {
            try {
                page = Integer.parseInt(a);
            } catch (NumberFormatException e) {
                worldFilter = a;
            }
        }

        List<World> worlds = new ArrayList<>();
        if (worldFilter == null) {
            worlds.addAll(Bukkit.getWorlds());
        } else {
            World w = Bukkit.getWorld(worldFilter);
            if (w == null) {
                sender.sendMessage(ChatColor.RED + "No loaded world named '" + worldFilter + "'.");
                sender.sendMessage(ChatColor.GRAY + "An unloaded world cannot be scanned - load it first.");
                return true;
            }
            worlds.add(w);
        }

        List<Found> found = new ArrayList<>();
        int chunksScanned = 0;
        int tileEntitiesSeen = 0;
        int lecternsSeen = 0;

        for (World world : worlds) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunksScanned++;
                BlockState[] states;
                try {
                    states = chunk.getTileEntities();
                } catch (Exception e) {
                    // A chunk can unload underneath the scan. Skip it rather than abort the sweep.
                    logger.debug("Skipped a chunk in " + world.getName() + ": " + e.getMessage());
                    continue;
                }
                tileEntitiesSeen += states.length;
                for (BlockState state : states) {
                    if (!(state instanceof Lectern)) {
                        continue;
                    }
                    lecternsSeen++;
                    Block lectern = state.getBlock();
                    Found f = describe(world, lectern);
                    if (f != null) {
                        found.add(f);
                    } else if (diag) {
                        dumpNeighbours(sender, world, lectern);
                    }
                }
            }
        }

        if (found.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No designated lecterns in the chunks scanned.");
            sender.sendMessage(ChatColor.GRAY + "Scanned " + chunksScanned + " loaded chunk(s) across "
                + worlds.size() + " world(s): " + tileEntitiesSeen + " block entities, "
                + lecternsSeen + " lectern(s), 0 designated.");
            sender.sendMessage(ChatColor.GRAY + "This is not proof of absence - an unloaded chunk cannot be read.");
            return true;
        }

        found.sort((a, b) -> {
            int w = a.world().compareTo(b.world());
            if (w != 0) return w;
            if (a.x() != b.x()) return Integer.compare(a.x(), b.x());
            if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
            return Integer.compare(a.z(), b.z());
        });

        List<String> lines = new ArrayList<>();
        for (Found f : found) {
            String tagColor = LecternSignUtil.TOME_TAG.equals(f.tag())
                ? ChatColor.LIGHT_PURPLE.toString()
                : ChatColor.AQUA.toString();
            String book = (f.bookName() == null || f.bookName().isEmpty())
                ? ChatColor.RED + "(no book named)"
                : ChatColor.WHITE + f.bookName();
            lines.add(tagColor + f.tag() + ChatColor.GRAY + " " + f.world()
                + " " + ChatColor.YELLOW + f.x() + "," + f.y() + "," + f.z()
                + ChatColor.GRAY + " -> " + book);
        }

        DisplayFactory.displayPaginatedList(
            sender,
            "Designated Lecterns (" + found.size() + ")",
            lines,
            page,
            ITEMS_PER_PAGE,
            entry -> ChatColor.GRAY + "- " + entry
        );

        sender.sendMessage(ChatColor.GRAY + "Scanned " + chunksScanned + " loaded chunk(s) across "
            + worlds.size() + " world(s): " + tileEntitiesSeen + " block entities, "
            + lecternsSeen + " lectern(s). Unloaded chunks were not read.");
        return true;
    }

    /**
     * Prints what each neighbouring sign of an undesignated lectern actually reads as.
     *
     * <p>Exists because "not designated" has several indistinguishable causes: no sign, a sign whose
     * line 0 is not the tag, or a sign mounted on something else. Guessing between them wasted a
     * build cycle once already.</p>
     */
    private void dumpNeighbours(CommandSender sender, World world, Block lectern) {
        sender.sendMessage(ChatColor.GRAY + "diag: lectern " + world.getName() + " "
            + lectern.getX() + "," + lectern.getY() + "," + lectern.getZ()
            + " type=" + lectern.getType());
        org.bukkit.block.BlockFace[] faces = {
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
            org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN
        };
        for (org.bukkit.block.BlockFace face : faces) {
            Block n = lectern.getRelative(face);
            BlockState ns = n.getState();
            if (!(ns instanceof Sign sign)) {
                continue;
            }
            String front = LecternSignUtil.readLine(sign, org.bukkit.block.sign.Side.FRONT, 0);
            String back = LecternSignUtil.readLine(sign, org.bukkit.block.sign.Side.BACK, 0);
            Block attached = LecternSignUtil.getAttachedBlock(n);
            sender.sendMessage(ChatColor.GRAY + "  " + face + " sign front=[" + front
                + "] back=[" + back + "] attached="
                + (attached == null ? "null" : attached.getType() + "@" + attached.getX() + ","
                    + attached.getY() + "," + attached.getZ()));
        }
    }

    /**
     * Describes a lectern if it carries a recognised tag.
     *
     * @return the description, or null when the lectern is an ordinary undesignated one
     */
    private Found describe(World world, Block lectern) {
        Block signBlock = LecternSignUtil.findTomeSign(lectern);
        String tag = LecternSignUtil.TOME_TAG;
        if (signBlock == null) {
            signBlock = LecternSignUtil.findLibrarySign(lectern);
            tag = LecternSignUtil.LIBRARY_TAG;
        }
        if (signBlock == null) {
            return null;
        }

        String bookName = null;
        BlockState signState = signBlock.getState();
        if (signState instanceof Sign sign) {
            // Reads lines 1-3 joined, so a name too long for one sign line still resolves (#2021).
            bookName = LecternSignUtil.readNameFromTaggedSide(sign, tag);
        }

        return new Found(world.getName(), lectern.getX(), lectern.getY(), lectern.getZ(), tag, bookName);
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase();
            if ("list".startsWith(partial)) {
                completions.add("list");
            }
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().toLowerCase().startsWith(partial)) {
                    completions.add(w.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            String partial = args[1].toLowerCase();
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().toLowerCase().startsWith(partial)) {
                    completions.add(w.getName());
                }
            }
        }
        return completions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.lectern.list")
            || sender.hasPermission("rvnklore.sign.tome")
            || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public String getDescription() {
        return "List sign-designated lecterns and their coordinates";
    }
}
