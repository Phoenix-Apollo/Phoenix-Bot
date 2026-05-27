package Botcode.Panel;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * Handles the {@code /panel} slash command.
 *
 * <p>Posts (or re-posts) the interactive operations console in the current channel. The message is
 * public so all server members can see it and use the buttons. Only admins should run this command
 * in practice — restrict it via Discord's slash command permissions tab if needed.
 */
public class PanelCommand extends ListenerAdapter {

  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    if (!event.getName().equals("panel")) {
      return;
    }

    if (!event.isFromGuild() || !(event.getGuildChannel() instanceof TextChannel channel)) {
      event
          .reply("This command can only be used in a server text channel.")
          .setEphemeral(true)
          .queue();
      return;
    }

    event.deferReply(true).queue(hook -> upsertPanel(channel, hook));
  }

  /**
   * Reposts the panel so it becomes the latest message in the channel.
   */
  private void upsertPanel(TextChannel channel, InteractionHook hook) {
    Runnable postNew =
        () ->
            channel
                .sendMessageEmbeds(PanelEmbeds.mainPanel())
                .addComponents(PanelButtons.mainPanelRows())
                .queue(
                    msg -> {
                      PanelStateStore.save(channel.getIdLong(), msg.getIdLong());
                      hook.editOriginal("Posted fresh panel in " + channel.getAsMention() + ".")
                          .queue();
                    },
                    err -> hook.editOriginal("Failed to post panel: " + err.getMessage()).queue());

    PanelStateStore.PanelState state = PanelStateStore.loadForChannel(channel.getIdLong());
    if (state == null || state.messageId <= 0) {
      postNew.run();
      return;
    }

    channel
        .retrieveMessageById(state.messageId)
        .queue(
            msg -> msg.delete().queue(ok -> postNew.run(), err -> postNew.run()),
            err -> postNew.run());
  }
}
