package Botcode.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class Eventlistener extends ListenerAdapter {

    private final Set<Long> usersCreatingChannels = new HashSet<>();

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        System.out.println("Debug: GuildVoiceUpdateEvent received.");

        if (event.getChannelJoined() == null || usersCreatingChannels.contains(event.getMember().getIdLong())) {
            System.out.println("Debug: Channel joined is null or user is already creating a channel.");
            return; // Don't proceed if there's no channel joined or user already creating channel
        }

        VoiceChannel joinedChannel = (VoiceChannel) event.getChannelJoined();

        if (joinedChannel.getName().equals("Join to Create")) {
            Member member = event.getMember();
            if (member != null && !usersCreatingChannels.contains(member.getIdLong())) {
                usersCreatingChannels.add(member.getIdLong()); // Mark user as creating channel
                createTempVoiceChannel(member, joinedChannel.getParentCategory());
            }
        }
    }

    private void createTempVoiceChannel(Member member, Category category) {
        String channelName = member.getEffectiveName() + "'s Channel";

        category.createVoiceChannel(channelName).queue(createdChannel -> {
            usersCreatingChannels.remove(member.getIdLong()); // Remove user from the creation list

            createdChannel.getManager().setName(channelName).queue(); // Rename the channel

            // Move the member to the newly created channel
            member.getGuild().moveVoiceMember(member, createdChannel).queue();

            // Set channel user limit, bitrate, etc. if needed
            // You can also manage permissions here
        });
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User user = event.getUser();
        String ping = user.getAsMention();
        Guild guild = event.getGuild();

        TextChannel channel = null;
        List<TextChannel> channelsByName = guild.getTextChannelsByName("public-chat", true); // Replace "public-chat" with the actual channel name
        if (!channelsByName.isEmpty()) {
            channel = channelsByName.get(0); // Assuming there's only one channel with that name
        }

        if (channel == null) {
            return;
        }

        String message = "Welcome to Phoenix Industries " + ping + "!\n";

        Category category = channel.getParentCategory(); // Retrieve the category

        // Include the category information in the message
        if (category != null) {
            message += "You are in the " + category.getName() + " category.";
        }

        channel.sendMessage(message).queue();
    }
}