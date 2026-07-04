package eu.kotori.justTeams.core.storage;

import eu.kotori.justTeams.JustTeams;

public class StorageManager {
   private final IDataStorage storage;

   public StorageManager(JustTeams plugin) {
      this.storage = new DatabaseStorage(plugin);
   }

   public boolean init() {
      return this.storage.init();
   }

   public void shutdown() {
      this.storage.shutdown();
   }

   public IDataStorage getStorage() {
      return this.storage;
   }

   public boolean isConnected() {
      return this.storage.isConnected();
   }
}
