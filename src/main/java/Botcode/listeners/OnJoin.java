package Botcode.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.util.HashMap;
import java.util.Map;

public class OnJoin extends ListenerAdapter {

    private final Map<Long, String> tempChannelNames = new HashMap<>();

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        System.out.println("Debug: GuildVoiceUpdateEvent received.");

        VoiceChannel joinedChannel = (VoiceChannel) event.getChannelJoined();
        Member member = event.getMember();

        if (joinedChannel != null && member != null && joinedChannel.getName().equals("Join to Create")) {
            System.out.println("Debug: Voice channel joined: " + joinedChannel.getName());
            Category parentCategory = joinedChannel.getParentCategory();
            if (parentCategory != null && parentCategory.getName().equals("Public Voice-Comms")) {
                privateMessageUser(member, joinedChannel, "public-events");
            } else if (parentCategory != null && parentCategory.getName().equals("Org Voice Channels")) {
                privateMessageUser(member, joinedChannel, "members-only-events");
            }
        }
    }

    private void privateMessageUser(Member member, VoiceChannel createdChannel, String targetTextChannelName) {
        member.getUser().openPrivateChannel().queue(privateChannel -> {
            privateChannel.sendMessage("Would you like to announce an event/activity in your new voice channel? (yes/no)").queue(
                    response -> {
                        privateChannel.getJDA().addEventListener(new ListenerAdapter() {
                            @Override
                            public void onMessageReceived(MessageReceivedEvent event) {
                                if (event.getAuthor().equals(member.getUser()) && event.getChannel().equals(privateChannel)) {
                                    String message = event.getMessage().getContentRaw().toLowerCase();
                                    if (message.equals("yes")) {
                                        announceEvent(member, createdChannel, targetTextChannelName);
                                    }
                                    privateChannel.getJDA().removeEventListener(this);
                                }
                            }
                        });
                    }
            );
        });
    }

    private void announceEvent(Member member, VoiceChannel voiceChannel, String targetTextChannelName) {
        String tempChannelName = tempChannelNames.get(voiceChannel.getIdLong()); // Get the name from the map
        if (tempChannelName == null) {
            tempChannelName = voiceChannel.getName();
        }

        Category eventsCategory = voiceChannel.getGuild().getCategoriesByName("Events & Operations", true).stream().findFirst().orElse(null);
        if (eventsCategory != null) {
            TextChannel targetTextChannel = eventsCategory.getTextChannels().stream()
                    .filter(channel -> channel.getName().equals(targetTextChannelName))
                    .findFirst().orElse(null);

            if (targetTextChannel != null) {
                targetTextChannel.sendMessage("An " + tempChannelName + " Event/Activity has started by " + member.getAsMention() + "!").queue();
            }
        }
    }
}
