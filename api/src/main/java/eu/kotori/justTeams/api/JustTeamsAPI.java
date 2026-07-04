package eu.kotori.justTeams.api;

import eu.kotori.justTeams.api.team.ITeamManager;
import eu.kotori.justTeams.api.data.CustomDataManager;

public interface JustTeamsAPI {
    ITeamManager getTeamManager();
    CustomDataManager getCustomDataManager();

    static JustTeamsAPI getInstance() {
        return JustTeamsProvider.get();
    }
}
