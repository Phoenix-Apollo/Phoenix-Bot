package Botcode.Panel;

import net.dv8tion.jda.api.sharding.ShardManager;

/**
 * Single entry point for the Panel module.
 *
 * <p>Call {@link #register(ShardManager)} once from {@code CommsBot} to activate the entire panel
 * feature. To disable the panel completely, remove that one call and delete this package — nothing
 * else in the codebase will be affected.
 *
 * <p>The {@code /panel} slash command must be registered separately in {@code CommsBot}:
 *
 * <pre>
 *   jda.upsertCommand("panel", "Post the interactive operations console").queue();
 * </pre>
 */
public class PanelModule {

  /**
   * Registers the panel command handler and interaction handler with JDA.
   *
   * @param shardManager the active shard manager from {@code CommsBot}
   */
  public static void register(ShardManager shardManager) {
    shardManager.addEventListener(new PanelCommand());
    shardManager.addEventListener(new PanelInteractionHandler());
    shardManager.addEventListener(new PanelAutoPublisher());
    System.out.println("[PanelModule] Interactive operations panel registered.");
  }
}
