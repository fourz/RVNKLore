package org.fourz.RVNKLore.command;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.search.LoreSearchService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Subcommand for sharing a lore entry as a server-wide broadcast.
 *
 * Usage: /lore share <entry-name>
 *
 * Broadcasts: [Lore] ★ TYPE · Name  —  description...  [Read More]
 * [Read More] is a clickable suggest command for /lore get <name>.
 * Requires rvnklore.share and a 60-second per-player cooldown (configurable).
 */
public class LoreShareSubCommand implements SubCommand {
    private static final int MAX_DESCRIPTION_LENGTH = 100;

    private final RVNKLore plugin;
    private final LogManager logger;
    private final TabCompletionUtil tabCompletionUtil;
    private final Map<UUID, Long> shareCooldowns = new ConcurrentHashMap<>();

    public LoreShareSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreShareSubCommand");
        this.tabCompletionUtil = new TabCompletionUtil(new LoreSearchService(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "▶ Usage: /lore share <entry-name>");
            return true;
        }

        String entryName = String.join(" ", args).trim();

        // Find entry
        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(entryName);
        if (entry == null) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Lore entry not found: " + entryName);
            return true;
        }

        if (!entry.isApproved()) {
            sender.sendMessage(org.bukkit.ChatColor.YELLOW + "⚠ That entry has not been approved yet.");
            return true;
        }

        if (!LoreCommandUtil.canSeeEntry(sender, entry)) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Lore entry not found: " + entryName);
            return true;
        }

        // Cooldown check (players only — console has no cooldown)
        if (sender instanceof Player) {
            UUID uuid = ((Player) sender).getUniqueId();
            long cooldownMs = plugin.getConfig().getInt("share.cooldown-seconds", 60) * 1000L;
            long now = System.currentTimeMillis();
            Long lastShare = shareCooldowns.get(uuid);
            if (lastShare != null && now - lastShare < cooldownMs) {
                long remaining = (cooldownMs - (now - lastShare)) / 1000;
                sender.sendMessage(org.bukkit.ChatColor.YELLOW + "⚠ Please wait " + remaining + "s before sharing again.");
                return true;
            }
            shareCooldowns.put(uuid, now);
        }

        // Build broadcast component
        TextComponent message = buildBroadcast(entry, sender.getName());

        // Broadcast to all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(message);
        }

        logger.info(sender.getName() + " shared lore entry '" + entry.getName() + "'");
        return true;
    }

    private TextComponent buildBroadcast(LoreEntry entry, String sharedBy) {
        String typeLabel = entry.getType().name();
        String name = entry.getName();
        String desc = entry.getDescription() != null ? entry.getDescription() : "";
        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            desc = desc.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
        }

        // Prefix: [Lore] ★ TYPE · Name  —  desc
        TextComponent prefix = new TextComponent(
            ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "Lore" + ChatColor.DARK_GRAY + "] "
            + ChatColor.YELLOW + "★ " + ChatColor.AQUA + typeLabel
            + ChatColor.GRAY + " · " + ChatColor.WHITE + name
            + ChatColor.GRAY + "  —  " + ChatColor.GRAY + ChatColor.ITALIC + desc + "  "
        );

        // Clickable [Read More]
        TextComponent readMore = new TextComponent(
            ChatColor.GOLD + "[" + ChatColor.YELLOW + "Read More" + ChatColor.GOLD + "]"
        );
        readMore.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/lore get " + name));
        readMore.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(ChatColor.GRAY + "Click to read: " + ChatColor.YELLOW + name).create()));

        prefix.addExtra(readMore);
        return prefix;
    }

    @Override
    public String getDescription() {
        return "Share a lore entry as a server broadcast with a clickable link";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.share") || LoreCommandUtil.isAdmin(sender);
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            return tabCompletionUtil.completeLoreEntryNames(String.join(" ", args));
        }
        return java.util.Collections.emptyList();
    }

    /** Grammar and worked examples served by {@code /lore help <verb>} (#1981). */
    @Override
    public String getUsage() {
        return "/lore share <entry-name>";
    }

    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/lore share Sol Sanctum",
                "  posts a clickable link to chat");
    }
}
