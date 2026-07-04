package eu.kotori.justTeams.core.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatInputManager implements Listener {
   private final JustTeams plugin;
   private final Map<UUID, InputData> pendingInput = new ConcurrentHashMap();
   private final Set<AsyncChatEvent> processedEvents = Collections.newSetFromMap(new WeakHashMap());

   public ChatInputManager(JustTeams plugin) {
      this.plugin = plugin;
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
   }

   public void awaitInput(Player player, IRefreshableGUI previousGui, Consumer<String> onInput) {
      this.pendingInput.put(player.getUniqueId(), new InputData(onInput, previousGui));
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent event) {
      if (this.processedEvents.contains(event)) {
         if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getLogger().info("Ignoring re-fired AsyncChatEvent for player " + event.getPlayer().getName());
         }

      } else {
         Player player = event.getPlayer();
         UUID uuid = player.getUniqueId();
         synchronized(uuid.toString().intern()) {
            InputData inputData = (InputData)this.pendingInput.get(uuid);
            if (inputData != null) {
               this.processedEvents.add(event);
               event.setCancelled(true);
               event.viewers().clear();
               String message = PlainTextComponentSerializer.plainText().serialize(event.message());
               event.message(Component.empty());
               event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, msg) -> Component.empty()));
               this.pendingInput.remove(uuid);

               try {
                  inputData.onInput().accept(message);
                  if (inputData.previousGui() != null) {
                     this.plugin.getTaskRunner().runOnEntity(player, () -> inputData.previousGui().refresh());
                  }
               } catch (Exception e) {
                  Logger var10000 = this.plugin.getLogger();
                  String var10001 = player.getName();
                  var10000.severe("Error in chat input callback for " + var10001 + ": " + e.getMessage());
               }

            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.pendingInput.remove(event.getPlayer().getUniqueId());
   }

   public void cancelInput(Player player) {
      this.pendingInput.remove(player.getUniqueId());
   }

   public boolean hasPendingInput(Player player) {
      return this.pendingInput.containsKey(player.getUniqueId());
   }

   public void clearAllPendingInput() {
      this.pendingInput.clear();
   }

   private static class InputData {
      private final Consumer<String> onInput;
      private final IRefreshableGUI previousGui;

      public InputData(Consumer<String> onInput, IRefreshableGUI previousGui) {
         this.onInput = onInput;
         this.previousGui = previousGui;
      }

      public Consumer<String> onInput() {
         return this.onInput;
      }

      public IRefreshableGUI previousGui() {
         return this.previousGui;
      }
   }
}
