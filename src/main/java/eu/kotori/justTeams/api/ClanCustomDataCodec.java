package eu.kotori.justTeams.api;

public interface ClanCustomDataCodec<T> {
   Class<T> getType();

   String serialize(T var1);

   T deserialize(String var1);
}
