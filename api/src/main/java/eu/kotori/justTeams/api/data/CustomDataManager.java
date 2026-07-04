package eu.kotori.justTeams.api.data;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CustomDataManager {
   private final Map<Class<?>, ClanCustomDataCodec<?>> codecs = new HashMap();
   private final Logger logger;

   public CustomDataManager(Logger logger) {
      this.logger = logger;
      this.registerDefaultCodecs();
   }

   private void registerDefaultCodecs() {
      this.registerCodec(new ClanCustomDataCodec<String>() {
         public Class<String> getType() {
            return String.class;
         }

         public String serialize(String obj) {
            return obj;
         }

         public String deserialize(String data) {
            return data;
         }
      });
      this.registerCodec(new ClanCustomDataCodec<Integer>() {
         public Class<Integer> getType() {
            return Integer.class;
         }

         public String serialize(Integer obj) {
            return String.valueOf(obj);
         }

         public Integer deserialize(String data) {
            try {
               return Integer.parseInt(data);
            } catch (NumberFormatException var3) {
               return null;
            }
         }
      });
      this.registerCodec(new ClanCustomDataCodec<Boolean>() {
         public Class<Boolean> getType() {
            return Boolean.class;
         }

         public String serialize(Boolean obj) {
            return String.valueOf(obj);
         }

         public Boolean deserialize(String data) {
            return Boolean.parseBoolean(data);
         }
      });
   }

   public <T> void registerCodec(ClanCustomDataCodec<T> codec) {
      if (this.codecs.containsKey(codec.getType())) {
         this.logger.warning("Overwriting existing codec for type: " + codec.getType().getName());
      }

      this.codecs.put(codec.getType(), codec);
   }

   public <T> ClanCustomDataCodec<T> getCodec(Class<T> type) {
      return (ClanCustomDataCodec)this.codecs.get(type);
   }

   public <T> String serialize(T obj) {
      if (obj == null) {
         return null;
      } else {
         @SuppressWarnings("unchecked")
         ClanCustomDataCodec<T> codec = (ClanCustomDataCodec<T>)this.getCodec((Class)obj.getClass());
         if (codec == null) {
            throw new IllegalArgumentException("No codec registered for type: " + obj.getClass().getName());
         } else {
            return codec.serialize(obj);
         }
      }
   }
}
