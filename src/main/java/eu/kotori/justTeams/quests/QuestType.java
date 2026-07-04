package eu.kotori.justTeams.quests;

public enum QuestType {
   KILL_PLAYERS,
   KILL_MOBS,
   BREAK_BLOCKS,
   PLACE_BLOCKS,
   TRAVEL_DISTANCE,
   CUSTOM;

   // $FF: synthetic method
   private static QuestType[] $values() {
      return new QuestType[]{KILL_PLAYERS, KILL_MOBS, BREAK_BLOCKS, PLACE_BLOCKS, TRAVEL_DISTANCE, CUSTOM};
   }
}
