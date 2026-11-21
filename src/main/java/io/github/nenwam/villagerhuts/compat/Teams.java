package io.github.nenwam.villagerhuts.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.nenwam.villagerhuts.Config;
import org.jspecify.annotations.Nullable;

/**
 * Parties (the shareWithTeam setting): everyone in a party can use each other's workers, as if every worker were shared
 * with them. Only its owner can still take its tool, share it or give it away. Server side only. No party mod is
 * hooked up on Fabric yet (FTB Teams is NeoForge-only in the Seed to Stars packs), so only the game tests make
 * parties for now; a party mod plugs in at provider().
 */
public final class Teams {
    /** A party: its id, and its name as players see it. */
    public record Party(UUID id, String name) {}

    private static @Nullable Provider provider;                        // found on first use
    private static final Map<UUID, Party> TEST_PARTIES = new HashMap<>(); // see addPartyForTests

    /** The party this player is in, if any. */
    public static Optional<Party> partyOf(UUID player) {
        if (!Config.SHARE_WITH_TEAM.getAsBoolean()) {
            return Optional.empty();
        }
        Party test = TEST_PARTIES.get(player);
        return test != null ? Optional.of(test) : provider().partyOf(player);
    }

    /** Whether these two players are in the same party (two different players: you're not your own teammate). */
    public static boolean sameParty(UUID player, UUID other) {
        if (player.equals(other)) {
            return false;
        }
        Optional<Party> party = partyOf(player);
        return party.isPresent() && partyOf(other).map(Party::id).equals(party.map(Party::id));
    }

    /** For the game tests (where FTB Teams isn't installed): puts these players in a party together. */
    public static void addPartyForTests(Party party, Set<UUID> members) {
        members.forEach(member -> TEST_PARTIES.put(member, party));
    }

    private interface Provider {
        Optional<Party> partyOf(UUID player);
    }

    private static Provider provider() {
        if (provider == null) {
            provider = player -> Optional.empty();
        }
        return provider;
    }

    private Teams() {}
}
