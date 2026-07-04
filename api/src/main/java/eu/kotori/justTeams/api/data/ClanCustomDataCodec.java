package eu.kotori.justTeams.api.data;

public interface ClanCustomDataCodec<T> {
   Class<T> getType();

   String serialize(T var1);

   T deserialize(String var1);
}
