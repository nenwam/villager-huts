package io.github.nenwam.villagerhuts.compat;

import java.util.Optional;
import java.util.UUID;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;

/** FTB Teams' side of Teams. Only loaded when FTB Teams is installed. */
final class FtbTeams {
    // A player's own team doesn't count (everyone has one): only a party they've made or joined.
    static Optional<Teams.Party> partyOf(UUID player) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return Optional.empty();
        }
        return api.getManager().getTeamForPlayerID(player)
                .filter(Team::isPartyTeam)
                .map(team -> new Teams.Party(team.getId(), team.getName().getString()));
    }

    private FtbTeams() {}
}
