package org.fourz.RVNKLore.lore;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for the LoreType permission matrix.
 *
 * <p>Admin-only types may only be created by senders holding {@code rvnklore.admin}.
 * Player-writable types may be created by any sender with {@code rvnklore.add};
 * senders additionally holding {@code rvnklore.approve.own} have their submissions
 * auto-approved on creation.
 *
 * <p>FACTION is player-writable but also requires GriefPrevention claim ownership
 * at the entry location — enforced separately in #736.
 * PATH is reserved (stub only).
 */
public final class LoreTypePermission {

    public static final Set<LoreType> ADMIN_ONLY = Collections.unmodifiableSet(
            Set.of(
                    LoreType.CITY,
                    LoreType.LANDMARK,
                    LoreType.EVENT,
                    LoreType.MONUMENT,
                    LoreType.SHRINE,
                    LoreType.TAVERN,
                    LoreType.GUILD,
                    LoreType.HEAD,
                    LoreType.ENCHANTMENT,
                    LoreType.QUEST,
                    LoreType.GENERIC
            )
    );

    /** Types any player with rvnklore.add may create. */
    public static final Set<LoreType> PLAYER_WRITABLE = Collections.unmodifiableSet(
            Set.of(
                    LoreType.PLAYER,
                    LoreType.ITEM,
                    LoreType.FACTION, // additional GP claim check enforced in #736
                    LoreType.PATH     // stub — reserved
            )
    );

    /** Ordered list shown in tab completion for non-admin players. */
    public static final List<LoreType> PLAYER_WRITABLE_TAB = Collections.unmodifiableList(
            Arrays.asList(LoreType.PLAYER, LoreType.ITEM, LoreType.PATH)
    );

    private LoreTypePermission() {}

    public static boolean isAdminOnly(LoreType type) {
        return ADMIN_ONLY.contains(type);
    }

    public static boolean isPlayerWritable(LoreType type) {
        return PLAYER_WRITABLE.contains(type);
    }
}
