package eu.kotori.justTeams.team;

public enum TeamRole {
   OWNER,
   CO_OWNER,
   MANAGER,
   MEMBER;

   
   private static TeamRole[] $values() {
      return new TeamRole[]{OWNER, CO_OWNER, MANAGER, MEMBER};
   }
}
