package Botcode.listeners;

import Botcode.AI.AIHook.AIResponder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import Botcode.AI.Personality.EmotionEngine;
import Botcode.AI.Personality.PersonaVoice;
import Botcode.AI.Personality.PersonalityEngine;
import Botcode.AI.Personality.PhraseLearner;
import Botcode.AI.Personality.SentenceGenerator;
import Botcode.AI.Personality.TriggerEngine;
import Botcode.Panel.PanelEmbeds;
import Botcode.Panel.PanelAutoPublisher;
import Botcode.Panel.PanelStateStore;
import Botcode.StarCitizen.MissingDataReportService;
import Botcode.StarCitizen.QueryAliasService;
import Botcode.StarCitizen.SourceOverrideService;
import Botcode.StarCitizen.StarCitizenChatService;
import Botcode.AI.AIUtils.BotConfig;
import Botcode.Utils.ChannelConfig;
import Botcode.AI.AIUtils.ChannelConversationService;
import Botcode.AI.AIUtils.ConversationMemoryService;
import Botcode.Utils.HelpBuilder;
import Botcode.AI.AIUtils.SafetyGuard;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Primary event listener that wires together all runtime bot behaviors.
 *
 * <p>Responsibilities covered by this listener:
 *
 * <ul>
 *   <li>Startup history backfill for passive learning (on {@code ReadyEvent})
 *   <li>Temporary voice-channel creation --” users joining "Join to Create" get a private room
 *   <li>Guild member welcome messages
 *   <li>Message routing: @mention commands, AI conversation, safety filtering, missing-data
 *       reports, source overrides, phrase learning, and channel config
 *   <li>Spontaneous topic interjections and social-mode replies in AI-scoped channels
 *   <li>GIF / expressive-emoji decorations on outgoing replies
 *   <li>Autonomous quality feedback from reactions (and conversational signals
 * </ul>
 *
 * <p>All outgoing text is sanitized through {@link Botcode.AI.AIUtils.SafetyGuard} and styled
 * through {@link Botcode.AI.Personality.PersonaVoice} before being sent.
 */
public class Eventlistener extends ListenerAdapter {

  private final Set<Long> usersCreatingChannels = ConcurrentHashMap.newKeySet();
  // When true, free-response channels only respond to explicit replies (or active
  // sessions/mentions).
  private static final boolean REQUIRE_REPLY_CONTEXT =
      Boolean.parseBoolean(System.getenv().getOrDefault("BOT_REQUIRE_REPLY_CONTEXT", "false"));

  // Topic interjection controls.
  private static final long INTERJECTION_COOLDOWN_SECONDS =
      readLongEnv("BOT_INTERJECTION_COOLDOWN_SECONDS", 180L);
  private static final int INTERJECTION_CHANCE_PERCENT =
      readIntEnv("BOT_INTERJECTION_CHANCE_PERCENT", 12);
  // Conversation context window for SC domain follow-ups (seconds).
  private static final long SC_CONTEXT_TTL_SECONDS =
      readLongEnv("BOT_SC_CONTEXT_TTL_SECONDS", 10 * 60L);
  // In AI-scoped channels, allow the bot to participate like a normal chat member.
  private static final boolean AI_SOCIAL_MODE =
      Boolean.parseBoolean(System.getenv().getOrDefault("BOT_AI_SOCIAL_MODE", "true"));
  private static final int AI_SOCIAL_REPLY_CHANCE_PERCENT =
      readIntEnv("BOT_AI_SOCIAL_REPLY_CHANCE_PERCENT", 68);
  private static final long AI_SOCIAL_COOLDOWN_SECONDS =
      readLongEnv("BOT_AI_SOCIAL_COOLDOWN_SECONDS", 18L);

  private static final Pattern QUOTED_TEXT = Pattern.compile("\"([^\"]{2,120})\"");
  private static final Pattern ALIAS_CORRECTION_PATTERN =
      Pattern.compile(
          "(?i)\\b([a-z0-9][a-z0-9\\s-]{1,63})\\s*=\\s*([a-z0-9][a-z0-9\\s-]{1,95})\\b");
  private static final String[] MISSING_DATA_DATASET_HINTS = {
      "commodity",
      "commodities",
      "trade",
      "routes",
      "mining",
      "ship",
      "ships",
      "missile",
      "missiles",
      "missile rack",
      "missile racks",
      "weapon",
      "weapons",
      "component",
      "components",
      "refinery",
      "salvage",
      "location",
      "locations",
      "mission",
      "missions",
      "armor",
      "item",
      "items"
  };

  // Per-channel cooldown so spontaneous interjections stay occasional.
  private static final Map<Long, Long> lastInterjectionEpochByChannel = new ConcurrentHashMap<>();
  // Per-user SC chat context to resolve follow-up prompts like "what about that one?".
  private static final Map<Long, String> lastScDomainByUser = new ConcurrentHashMap<>();
  private static final Map<Long, Long> lastScDomainEpochByUser = new ConcurrentHashMap<>();
  // Per-channel throttle for social-mode conversational replies.
  private static final Map<Long, Long> lastSocialReplyEpochByChannel = new ConcurrentHashMap<>();
  // Per-channel throttle for GIF reactions/decorations.
  private static final Map<Long, Long> lastGifReplyEpochByChannel = new ConcurrentHashMap<>();
  // Per user+channel latest tracked bot reply, used for autonomous feedback when users do not
  // react.
  private static final Map<String, PendingFeedbackTarget> lastTrackedReplyByUserChannel =
      new ConcurrentHashMap<>();
  // Per-user command cooldowns for sensitive source override workflows.
  private static final Map<Long, Long> lastSourceSubmitEpochByUser = new ConcurrentHashMap<>();
  private static final Map<Long, Long> lastSourceApproveEpochByUser = new ConcurrentHashMap<>();
  private static final Set<Long> historyBackfilledGuilds = ConcurrentHashMap.newKeySet();

  private static final List<String> POLITICS_KEYWORDS =
      List.of(
          "politics",
          "political",
          "election",
          "vote",
          "voting",
          "democrat",
          "republican",
          "left wing",
          "right wing",
          "liberal",
          "conservative",
          "senate",
          "congress",
          "parliament",
          "president",
          "prime minister",
          "campaign",
          "policy debate");

  private static final List<String> SC_TOPIC_KEYWORDS =
      List.of(
          "star citizen",
          "quantum",
          "cargo",
          "hauling",
          "trade route",
          "mining",
          "refinery",
          "salvage",
          "ship",
          "weapons",
          "dps",
          "bounty");

  private static final List<String> POLITICS_REDIRECT_LINES =
      List.of(
          "Friendly neighborhood chaos-bot interruption: politics stays parked outside this channel. Let's keep it on ops, games, and good vibes.",
          "Tiny Deadpool PSA: political debate arc is not in this season. Let's pivot back to Star Citizen, builds, or server stuff.",
          "Plot twist: this is a no-politics zone. Save campaign mode for elsewhere and keep this channel chill.");

  private static final List<String> SC_INTERJECTION_LINES =
      List.of(
          "Quick unsolicited wisdom drop: if cargo is involved, always factor travel time and risk, not just raw profit.",
          "Interrupting politely: if mining feels cursed, check resistance + instability before touching power.",
          "Deadpool tip of the minute: DPS on paper is cute, but projectile speed and engagement range win fights.",
          "Friendly but-in: refinery method choice is basically time vs yield. Pick based on when you actually plan to sell.",
          "Comms gremlin note: best trade route is the one you can run consistently without exploding.");

  private static final List<String> POSITIVE_FEEDBACK_SIGNALS =
      List.of(
          "thanks",
          "thank you",
          "ty",
          "perfect",
          "exactly",
          "that worked",
          "works now",
          "good bot",
          "nice",
          "great",
          "awesome",
          "helpful",
          "fixed it");

  private static final List<String> NEGATIVE_FEEDBACK_SIGNALS =
      List.of(
          "wrong",
          "not right",
          "that is incorrect",
          "didn't work",
          "not what i asked",
          "you missed",
          "bad answer",
          "try again",
          "learn this",
          "learn from this",
          "learn from that",
          "improve this",
          "improve that",
          "improve your answer",
          "make corrections",
          "correct yourself",
          "fix your answer",
          "update that",
          "you are not learning",
          "you're not learning",
          "your not learning",
          "not learning");

  private static final List<String> GIF_REACTION_LINES =
      List.of(
          "That GIF had mercenary-main-character energy.",
          "Live footage of chaos. Officially approved.",
          "Respect. That GIF explains the situation better than my redacted report.",
          "Mood detected. Chaos level: tactical.",
          "GIF diplomacy remains undefeated, as foretold by chimichangas.");

  private static final List<String> EXPRESSIVE_EMOJIS =
      List.of("\u2728", "\uD83D\uDD25", "\uD83D\uDE80", "\uD83D\uDCA5");

  private static final List<String> BOT_GIF_LINKS =
      List.of(
          "https://media.tenor.com/2roX3uxz_68AAAAC/deadpool-wave.gif",
          "https://media.tenor.com/lNMyjjSWLYcAAAAC/ryan-reynolds-deadpool.gif",
          "https://media.tenor.com/Ja2N9W9xLJYAAAAC/thumbs-up-yes.gif");

  /**
   * Runs once per shard when JDA signals READY.
   *
   * <p>When {@link Botcode.AI.AIUtils.BotConfig#AUTONOMOUS_LEARNING_ENABLED} and {@link
   * Botcode.AI.AIUtils.BotConfig#PASSIVE_HISTORY_BACKFILL_ENABLED} are both true, this
   * asynchronously
   * back-fills recent channel history so the bot can bootstrap user preference/topic memory before
   * it receives its first live message.
   */
  @Override
  public void onReady(ReadyEvent event) {
    if (!BotConfig.AUTONOMOUS_LEARNING_ENABLED || !BotConfig.PASSIVE_HISTORY_BACKFILL_ENABLED) {
      return;
    }

    CompletableFuture.runAsync(
        () -> {
          int totalSignals = 0;
          int totalChannels = 0;
          OffsetDateTime cutoff =
              OffsetDateTime.now()
                  .minusDays(Math.max(1, BotConfig.PASSIVE_HISTORY_BACKFILL_MAX_AGE_DAYS));

          for (Guild guild : event.getJDA().getGuilds()) {
            if (!historyBackfilledGuilds.add(guild.getIdLong())) {
              continue;
            }

            int guildChannels = 0;
            for (TextChannel channel : guild.getTextChannels()) {
              if (guildChannels
                  >= Math.max(1, BotConfig.PASSIVE_HISTORY_BACKFILL_MAX_CHANNELS_PER_GUILD)) {
                break;
              }
              if (!channel
                  .getGuild()
                  .getSelfMember()
                  .hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)) {
                continue;
              }

              Long parentCategoryId =
                  channel.getParentCategory() != null
                      ? channel.getParentCategory().getIdLong()
                      : null;
              boolean scoped =
                  ChannelConfig.isAiScopedChannel(parentCategoryId, channel.getIdLong())
                      || ChannelConfig.isFreeChannel(channel.getIdLong());
              if (BotConfig.PASSIVE_HISTORY_BACKFILL_AI_SCOPE_ONLY && !scoped) {
                continue;
              }

              try {
                var messages =
                    channel
                        .getHistory()
                        .retrievePast(
                            Math.max(1, BotConfig.PASSIVE_HISTORY_BACKFILL_MESSAGES_PER_CHANNEL))
                        .complete();

                int channelSignals = 0;
                for (var msg : messages) {
                  if (msg.getAuthor().isBot()) {
                    continue;
                  }
                  if (msg.getTimeCreated().isBefore(cutoff)) {
                    continue;
                  }
                  String content = msg.getContentRaw();
                  if (!shouldRecordPassiveLearning(content)) {
                    continue;
                  }

                  ConversationMemoryService.recordHistoricalSignal(
                      msg.getAuthor().getIdLong(),
                      msg.getAuthor().getName(),
                      content,
                      msg.getTimeCreated().toEpochSecond());
                  channelSignals++;
                }

                if (channelSignals > 0) {
                  totalSignals += channelSignals;
                  totalChannels++;
                }
                guildChannels++;
              } catch (Exception ignored) {
                // Ignore inaccessible/history retrieval failures and continue.
              }
            }
          }

          System.out.println(
              "[Learning] History backfill complete: "
                  + totalSignals
                  + " passive signals from "
                  + totalChannels
                  + " channel(s).");
        });
  }

  /**
   * Watches voice joins and creates a personal channel when users enter Join to Create.
   */
  @Override
  public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
    System.out.println("Debug: GuildVoiceUpdateEvent received.");

    // Ignore non-join transitions and prevent duplicate channel creation requests.
    if (event.getChannelJoined() == null
        || usersCreatingChannels.contains(event.getMember().getIdLong())) {
      System.out.println("Debug: Channel joined is null or user is already creating a channel.");
      return; // Don't proceed if there's no channel joined or user already creating channel
    }

    VoiceChannel joinedChannel = (VoiceChannel) event.getChannelJoined();

    // Only this trigger channel should spawn per-user temporary channels.
    if (joinedChannel.getName().equals("Join to Create")) {
      Member member = event.getMember();
      if (member != null && !usersCreatingChannels.contains(member.getIdLong())) {
        usersCreatingChannels.add(member.getIdLong()); // Mark user as creating channel
        createTempVoiceChannel(member, joinedChannel.getParentCategory());
      }
    }
  }

  /**
   * Creates a temporary voice channel and moves the requesting member into it.
   */
  private void createTempVoiceChannel(Member member, Category category) {
    String channelName = member.getEffectiveName() + "'s Channel";

    category
        .createVoiceChannel(channelName)
        .queue(
            createdChannel -> {
              usersCreatingChannels.remove(
                  member.getIdLong()); // Remove user from the creation list

              createdChannel.getManager().setName(channelName).queue(); // Rename the channel

              // Move the member to the newly created channel
              member.getGuild().moveVoiceMember(member, createdChannel).queue();

              // Set channel user limit, bitrate, etc. if needed
              // You can also manage permissions here
            });
  }

  /**
   * Sends a basic welcome message in the configured public chat channel.
   */
  @Override
  public void onGuildMemberJoin(GuildMemberJoinEvent event) {
    User user = event.getUser();
    String ping = user.getAsMention();
    Guild guild = event.getGuild();

    TextChannel channel = null;
    // Resolve the configured welcome channel by name.
    List<TextChannel> channelsByName =
        guild.getTextChannelsByName(
            "public-chat", true); // Replace "public-chat" with the actual channel name
    if (!channelsByName.isEmpty()) {
      channel = channelsByName.get(0); // Assuming there's only one channel with that name
    }

    // Abort silently when the configured channel does not exist.
    if (channel == null) {
      return;
    }

    String message = "Welcome to Phoenix Industries " + ping + "!\n";

    Category category = channel.getParentCategory(); // Retrieve the category

    channel.sendMessage(message).queue();
  }

  /**
   * Handles incoming messages.
   *
   * <p>Response rules:
   * <ul>
   *   <li>Bot is @mentioned anywhere -†’ always reply (strips the mention first)</li>
   *   <li>Channel is in the Phoenix Industries category (BOT_AI_CATEGORY_ID) -†’ reply freely</li>
   *   <li>Channel is in the free-response list (set via {@code @bot allowchannel}) -†’ reply freely</li>
   *   <li>Active conversation session exists for this user/channel -†’ reply freely</li>
   *   <li>Otherwise -†’ stay silent (no spamming general chat)</li>
   * </ul>
   * <p>
   * Admin commands (require @mention + MANAGE_SERVER or ADMINISTRATOR):
   * <pre>
   *   @bot allowchannel   --” add current channel to free-response list (for channels outside Phoenix category)
   *   @bot denychannel    --” remove current channel from free-response list
   *   @bot channels       --” list all free-response channels (including Phoenix category channels)
   *   @bot learn &lt;intent&gt;: &lt;phrase&gt;   --” teach a phrase
   *   @bot learn &lt;intent&gt; = &lt;phrase&gt;   --” teach a phrase (alt separator)
   *   @bot forget &lt;intent&gt;: &lt;phrase&gt;  --” remove a phrase
   *   @bot forget &lt;intent&gt; = &lt;phrase&gt;  --” remove a phrase (alt separator)
   *   @bot phrases &lt;intent&gt;            --” list learned phrases
   * </pre>
   */
  @Override
  public void onMessageReceived(MessageReceivedEvent event) {

    // Never respond to bots (including itself).
    if (event.getAuthor().isBot()) {
      return;
    }

    // Only handle guild (server) messages.
    if (!event.isFromGuild()) {
      return;
    }

    // Check the bot has permission to send messages in this channel.
    Member selfMember = event.getGuild().getSelfMember();
    if (!selfMember.hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND)) {
      return;
    }

    boolean isMentioned =
        event.getMessage().getMentions().isMentioned(event.getJDA().getSelfUser());

    // Strip the @mention from the message text if present.
    String raw = event.getMessage().getContentRaw();
    String text = raw.replaceAll("<@!?" + event.getJDA().getSelfUser().getId() + ">", "").trim();
    boolean hasGifSignal = containsGifSignal(event, raw);

    // -------------------------------------------------------
    // Admin channel-config commands (require @mention)
    // -------------------------------------------------------
    if (isMentioned) {
      String lower = text.toLowerCase();

      // @bot help
      if (lower.isEmpty() || lower.equals("help")) {
        boolean admin = isAdmin(event.getMember());
        event.getChannel().sendMessageEmbeds(HelpBuilder.build(admin)).queue();
        return;
      }

      // @bot allowchannel
      if (lower.equals("allowchannel") || lower.startsWith("allowchannel ")) {
        handleChannelAllow(event, true);
        return;
      }

      // @bot denychannel
      if (lower.equals("denychannel") || lower.startsWith("denychannel ")) {
        handleChannelAllow(event, false);
        return;
      }

      // @bot channels
      if (lower.equals("channels")) {
        handleChannelList(event);
        return;
      }

      // @bot source <dataset>: <url>
      if (lower.startsWith("source ")) {
        handleSourceSubmit(event, text.substring(7).trim());
        return;
      }

      // @bot approvesource <dataset>
      if (lower.startsWith("approvesource ")) {
        handleSourceApprove(event, text.substring(14).trim());
        return;
      }

      // @bot sources
      if (lower.equals("sources")) {
        handleSourceStatus(event);
        return;
      }

      // @bot learningstats
      if (lower.equals("learningstats") || lower.equals("learning stats")) {
        handleLearningStats(event);
        return;
      }

      // @bot addguichannel
      if (lower.equals("addguichannel") || lower.startsWith("addguichannel ")) {
        handleGuiChannelAllow(event, true);
        return;
      }

      // @bot removeguichannel
      if (lower.equals("removeguichannel") || lower.startsWith("removeguichannel ")) {
        handleGuiChannelAllow(event, false);
        return;
      }

      // @bot guichannels
      if (lower.equals("guichannels")) {
        handleGuiChannelList(event);
        return;
      }

      // @bot learn <intent>: <phrase> or @bot learn <intent> = <phrase>
      if (lower.startsWith("learn ")) {
        String args = text.substring(6).trim();
        if (hasLearnSeparator(args)) {
          handleLearnCommand(event, args, true);
          return;
        }
      }

      // @bot forget <intent>: <phrase> or @bot forget <intent> = <phrase>
      if (lower.startsWith("forget ")) {
        String args = text.substring(7).trim();
        if (hasLearnSeparator(args)) {
          handleLearnCommand(event, args, false);
          return;
        }
      }

      // @bot phrases <intent>
      if (lower.startsWith("phrases")) {
        String intentArg = text.substring(7).trim();
        TriggerEngine.Intent intent = PhraseLearner.parseIntent(intentArg);
        if (intent == null) {
          event
              .getChannel()
              .sendMessage("Unknown intent. Valid intents: " + PhraseLearner.validIntentNames())
              .queue();
        } else {
          event.getChannel().sendMessage(PhraseLearner.formatPhraseList(intent)).queue();
        }
        return;
      }

      // @bot learnme <intent>: <phrase> or @bot learnme <intent> = <phrase>
      if (lower.startsWith("learnme ")) {
        String args = text.substring(8).trim();
        if (hasLearnSeparator(args)) {
          handleUserPhraseCommand(event, args, true);
          return;
        }
      }

      // @bot forgetme <intent>: <phrase> or @bot forgetme <intent> = <phrase>
      if (lower.startsWith("forgetme ")) {
        String args = text.substring(9).trim();
        if (hasLearnSeparator(args)) {
          handleUserPhraseCommand(event, args, false);
          return;
        }
      }

      // @bot myphrases <intent>
      if (lower.startsWith("myphrases")) {
        String intentArg = text.substring(9).trim();
        TriggerEngine.Intent intent = PhraseLearner.parseIntent(intentArg);
        if (intent == null) {
          event
              .getChannel()
              .sendMessage("Unknown intent. Valid intents: " + PhraseLearner.validIntentNames())
              .queue();
        } else {
          event
              .getChannel()
              .sendMessage(
                  PhraseLearner.formatUserPhraseList(event.getAuthor().getIdLong(), intent))
              .queue();
        }
        return;
      }

      // @bot memory me
      if (lower.equals("memory me")) {
        event
            .getChannel()
            .sendMessage(ConversationMemoryService.memorySummary(event.getAuthor().getIdLong()))
            .queue();
        return;
      }

      // @bot missing <dataset>: <item> [| notes]
      if (lower.startsWith("missing ")) {
        handleMissingReportCommand(event, text.substring(8).trim());
        return;
      }

      // @bot reportmissing <dataset>: <item> [| notes]
      if (lower.startsWith("reportmissing ")) {
        handleMissingReportCommand(event, text.substring(14).trim());
        return;
      }

      // @bot forget me
      if (lower.equals("forget me")) {
        boolean memoryCleared = ConversationMemoryService.forgetUser(event.getAuthor().getIdLong());
        boolean phrasesCleared = PhraseLearner.forgetUser(event.getAuthor().getIdLong());
        event
            .getChannel()
            .sendMessage(
                (memoryCleared || phrasesCleared)
                    ? "Done --” I forgot your stored memory and personal phrase profile."
                    : "I don't have stored memory for you yet.")
            .queue();
        return;
      }
    }

    boolean passiveRecorded = false;
    if (BotConfig.AUTONOMOUS_LEARNING_ENABLED
        && BotConfig.PASSIVE_LEARNING_ALL_GUILD_MESSAGES
        && shouldRecordPassiveLearning(text)) {
      ConversationMemoryService.recordPassiveSignal(
          event.getAuthor().getIdLong(), event.getAuthor().getName(), text);
      if (BotConfig.AUTO_PERSONAL_PHRASE_LEARNING_ENABLED) {
        PhraseLearner.observeUserStyleMessage(
            event.getAuthor().getIdLong(), TriggerEngine.detectIntent(text), text);
      }
      passiveRecorded = true;
    }

    // Record message in channel conversation tracker for multi-user awareness
    String detectedTopic =
        ConversationMemoryService.getRecentTopics(event.getAuthor().getIdLong()).stream()
            .findFirst()
            .orElse("general");
    ChannelConversationService.recordChannelMessage(
        event.getChannel().getIdLong(),
        event.getAuthor().getIdLong(),
        event.getAuthor().getName(),
        text,
        detectedTopic);

    // -------------------------------------------------------
    // Normal conversation
    // Respond if: (a) @mentioned, (b) in a free-response channel,
    // or (c) an active conversation session already exists for this user/channel.
    // -------------------------------------------------------
    Long parentCategoryId = null;
    if (event.getChannel() instanceof TextChannel tc && tc.getParentCategory() != null) {
      parentCategoryId = tc.getParentCategory().getIdLong();
    }

    boolean isFreeChannel = ChannelConfig.isFreeChannel(event.getChannel().getIdLong());
    boolean isAiScopedChannel =
        ChannelConfig.isAiScopedChannel(parentCategoryId, event.getChannel().getIdLong());
    boolean isActiveSession =
        ConversationMemoryService.hasActiveSession(
            event.getChannel().getIdLong(), event.getAuthor().getIdLong());
    if (!isMentioned && !isFreeChannel && !isAiScopedChannel && !isActiveSession) {
      return;
    }

    // Optional strict mode: in free-response channels, require explicit reply context
    // unless this is a mention or already-active conversation session.
    boolean isReplyToBot = isReplyToBot(event);
    if (REQUIRE_REPLY_CONTEXT && !isMentioned && !isActiveSession && !isReplyToBot) {
      return;
    }

    if (text.isEmpty() && !hasGifSignal) {
      return; // bare @mention already handled above
    }

    long userId = event.getAuthor().getIdLong();
    boolean directBotContext = isMentioned || isReplyToBot || isActiveSession;

    if (tryLearnQueryAlias(event, text, directBotContext)) {
      return;
    }

    String aliasedText = QueryAliasService.applyAliases(text);
    String resolvedPrompt = ConversationMemoryService.resolveFollowUpPrompt(userId, aliasedText);

    // Implicit quality feedback lets the bot self-tune from normal conversation
    // (e.g., "perfect" or "that's wrong") even when users do not react with emojis.
    applyImplicitQualityFeedback(event, aliasedText);

    // Passive learning should continue even when the bot stays silent.
    if (!passiveRecorded && shouldRecordPassiveLearning(aliasedText)) {
      ConversationMemoryService.recordPassiveSignal(
          event.getAuthor().getIdLong(), event.getAuthor().getName(), aliasedText);
      if (BotConfig.AUTO_PERSONAL_PHRASE_LEARNING_ENABLED) {
        PhraseLearner.observeUserStyleMessage(
            event.getAuthor().getIdLong(), TriggerEngine.detectIntent(aliasedText), aliasedText);
      }
    }

    if (hasGifSignal && tryGifReactionReply(event, directBotContext, isAiScopedChannel)) {
      return;
    }

    // In free AI channels, detect user-to-user chatter and avoid jumping in unless
    // policy/topic interjection rules trigger.
    if (!directBotContext && isLikelyPeerToPeerChat(event, aliasedText)) {
      if (tryPoliticsRedirect(event, aliasedText)) {
        return;
      }
      if (isAiScopedChannel && trySocialChannelReply(event, aliasedText)) {
        return;
      }
      // Check for multi-user conversation context and contextual interjection
      if (isAiScopedChannel && tryMultiUserConversationInterjection(event, aliasedText)) {
        return;
      }
      tryTopicInterjection(event, aliasedText);
      return;
    }

    // Non-peer messages in AI channels can still trigger occasional policy/topic interjections.
    if (!directBotContext && tryPoliticsRedirect(event, aliasedText)) {
      return;
    }

    // Natural-language missing-data detector for long-running AI channels.
    if (tryHandleNaturalMissingReport(event, aliasedText)) {
      return;
    }

    // Hard safety gate for disallowed harmful-instruction requests.
    if (SafetyGuard.isDisallowed(aliasedText)) {
      event
          .getChannel()
          .sendMessage("Sorry, I can't assist with that.")
          .queue(
              sentMessage -> {
                ConversationMemoryService.touchSession(
                    event.getChannel().getIdLong(), event.getAuthor().getIdLong());
              });
      return;
    }

    // Skip low-signal acknowledgements to keep conversation natural.
    if (isLowSignalAck(aliasedText)) {
      if (directBotContext) {
        ConversationMemoryService.touchSession(
            event.getChannel().getIdLong(), event.getAuthor().getIdLong());
      }
      return;
    }

    // If user says they're bored, proactively offer useful/fun options.
    if (isBoredPrompt(aliasedText)) {
      String boredReply =
          "Bored? Say less. Pick your chaos pack:\n"
              + "--¢ `best way to make money` --” I'll give you a profit plan\n"
              + "--¢ `best mining location` --” data-backed recommender\n"
              + "--¢ `top trade routes 96 scu` --” fast route shortlist\n"
              + "--¢ `ship <name>` or `weapon <name>` --” nerd mode stats dump\n"
              + "\nI can also freestyle if you just want banter.";
      event
          .getChannel()
          .sendMessage(boredReply)
          .queue(
              sentMessage ->
                  ConversationMemoryService.touchSession(
                      event.getChannel().getIdLong(), event.getAuthor().getIdLong()));
      return;
    }

    if (isMiningGuiFeedbackPrompt(aliasedText)) {
      String miningFlowReply =
          "Noted and patched. Mining GUI now supports head count + modules-per-head flow for multi-head ships. "
              + "For MOLE: pick up to 3 active heads, then set modules per head (up to 3), for up to 9 total module slots. "
              + "Run `panel -> Mining` and walk the updated setup flow.";
      event
          .getChannel()
          .sendMessage(miningFlowReply)
          .queue(
              sentMessage -> {
                ConversationMemoryService.recordBotReplyContext(
                    userId, "mining", "mole heads modules", responseAsksFollowUp(miningFlowReply));
                ConversationMemoryService.touchSession(event.getChannel().getIdLong(), userId);
              });
      return;
    }

    // Context carry-over: if the user is already in direct bot context and asks a
    // follow-up without naming the domain again, infer the previous SC domain.
    // Follow-up continuity is now centralized in ConversationMemoryService.resolveFollowUpPrompt.

    // Special-case weather asks so they don't get generic fallback replies.
    if (isWeatherQuestion(aliasedText)) {
      String weatherReply =
          "I can't check live weather yet in this build. "
              + "If you want, tell me the city/system and I can add a weather module next.";
      event
          .getChannel()
          .sendMessage(weatherReply)
          .queue(
              sentMessage -> {
                // Keep continuity active after direct-capability responses.
                ConversationMemoryService.touchSession(
                    event.getChannel().getIdLong(), event.getAuthor().getIdLong());
              });
      return;
    }

    // Follow-up continuity: if user is already in direct bot context and recently talked
    // about mining, treat risk/style follow-ups as mining recommender refinements.
    if (directBotContext
        && isMiningPreferenceFollowUp(aliasedText)
        && hasRecentTopic(event.getAuthor().getIdLong(), "mining")) {
      String miningFollowupPrompt = "best place to mine " + aliasedText;
      String miningFollowupReply = StarCitizenChatService.tryRespond(miningFollowupPrompt);
      if (miningFollowupReply != null) {
        TriggerEngine.Intent intent = TriggerEngine.detectIntent(aliasedText);
        EmotionEngine.Emotion emotion = EmotionEngine.detectEmotion(aliasedText);
        ConversationMemoryService.recordUserMessage(
            event.getAuthor().getIdLong(),
            event.getAuthor().getName(),
            aliasedText,
            intent,
            emotion);
        event
            .getChannel()
            .sendMessage(miningFollowupReply)
            .queue(
                sentMessage ->
                    ConversationMemoryService.touchSession(
                        event.getChannel().getIdLong(), event.getAuthor().getIdLong()));
        return;
      }
    }

    // Centralized deterministic/premium-ready AI facade.
    AIResponder.AIResponse ai = AIResponder.resolve(resolvedPrompt);
    if (ai.hasText()) {
      TriggerEngine.Intent intent = TriggerEngine.detectIntent(aliasedText);
      EmotionEngine.Emotion emotion = EmotionEngine.detectEmotion(aliasedText);
      ConversationMemoryService.recordUserMessage(
          userId, event.getAuthor().getName(), aliasedText, intent, emotion);
      String safeAiReply =
          withExpressiveFlair(
              sanitizeOutgoingReply(ai.text),
              event.getChannel().getIdLong(),
              ai.source != AIResponder.Source.STAR_CITIZEN);
      StarCitizenChatService.ShipEmbedData shipEmbedData =
          (ai.source == AIResponder.Source.STAR_CITIZEN && BotConfig.SHIP_EMBED_REPLIES_ENABLED)
              ? StarCitizenChatService.getShipEmbedDataForPrompt(resolvedPrompt)
              : null;

      if (shipEmbedData != null) {
        var shipEmbed =
            PanelEmbeds.shipResult(
                shipEmbedData.shipName,
                shipEmbedData.info,
                shipEmbedData.stats,
                shipEmbedData.loadout,
                shipEmbedData.imageUrl);
        String lead = extractShipLeadLine(safeAiReply);
        event
            .getChannel()
            .sendMessage(lead)
            .setEmbeds(shipEmbed)
            .queue(
                sentMessage ->
                    recordAiContextAndSession(
                        ai,
                        resolvedPrompt,
                        safeAiReply,
                        userId,
                        event,
                        aliasedText,
                        sentMessage.getIdLong()));
        return;
      }

      event
          .getChannel()
          .sendMessage(safeAiReply)
          .queue(
              sentMessage ->
                  recordAiContextAndSession(
                      ai,
                      resolvedPrompt,
                      safeAiReply,
                      userId,
                      event,
                      aliasedText,
                      sentMessage.getIdLong()));
      return;
    }

    // Run through personality pipeline.
    TriggerEngine.Intent intent = TriggerEngine.detectIntent(aliasedText);
    EmotionEngine.Emotion emotion = EmotionEngine.detectEmotion(aliasedText);
    PersonalityEngine.PersonalityProfile profile = PersonalityEngine.decideProfile(intent, emotion);

    // Persist lightweight per-user memory for future follow-up chats.
    ConversationMemoryService.recordUserMessage(
        userId, event.getAuthor().getName(), aliasedText, intent, emotion);

    // generateTracked returns [fullReply, rawPhrase] so we can track for reactions.
    String[] result =
        SentenceGenerator.generateTrackedForUser(
            intent, emotion, profile, userId, event.getChannel().getIdLong(), aliasedText);
    String reply = result[0];
    String phrase = result[1];

    // Add a small personalized opener when user memory exists,
    // but NOT when the intent is already a greeting/farewell --” those
    // responses already open naturally and a bolted-on prefix causes
    // awkward double-greetings like "Welcome back! Hey there!".
    String memoryPrefix =
        ConversationMemoryService.buildMemoryPrefix(event.getAuthor().getIdLong());
    boolean isNaturalOpener =
        intent == TriggerEngine.Intent.GREETING
            || intent == TriggerEngine.Intent.HOW_ARE_YOU
            || intent == TriggerEngine.Intent.FAREWELL
            || intent == TriggerEngine.Intent.HYPE;
    if (!isActiveSession && !memoryPrefix.isBlank() && !isNaturalOpener) {
      reply = memoryPrefix + " " + reply;
    }

    final String finalReply =
        withExpressiveFlair(sanitizeOutgoingReply(reply), event.getChannel().getIdLong(), true);
    event
        .getChannel()
        .sendMessage(finalReply)
        .queue(
            sentMessage -> {
              PhraseLearner.trackReply(sentMessage.getIdLong(), intent, phrase);
              rememberTrackedReplyTarget(
                  event.getChannel().getIdLong(), userId, sentMessage.getIdLong());
              String subject =
                  !ConversationMemoryService.getRecentTopics(userId).isEmpty()
                      ? ConversationMemoryService.getRecentTopics(userId).get(0)
                      : "conversation";
              ConversationMemoryService.recordBotReplyContext(
                  userId, "casual", subject, responseAsksFollowUp(finalReply));
              ConversationMemoryService.touchSession(event.getChannel().getIdLong(), userId);
            });
  }

  // ---------------------------------------------------------------------------
  // Channel config command handlers
  // ---------------------------------------------------------------------------

  /**
   * Adds or removes the current channel from the free-response list (admin only).
   */
  private void handleChannelAllow(MessageReceivedEvent event, boolean allow) {
    if (!isAdmin(event.getMember())) {
      event.getChannel().sendMessage("Only admins can change channel settings.").queue();
      return;
    }
    long channelId = event.getChannel().getIdLong();
    if (allow) {
      boolean added = ChannelConfig.allowChannel(channelId);
      event
          .getChannel()
          .sendMessage(
              added
                  ? "-... Got it! I'll now respond freely in **#"
                    + event.getChannel().getName()
                    + "**."
                  : "This channel is already in my free-response list.")
          .queue();
    } else {
      boolean removed = ChannelConfig.denyChannel(channelId);
      event
          .getChannel()
          .sendMessage(
              removed
                  ? "Done. I'll only respond to @mentions in **#"
                    + event.getChannel().getName()
                    + "** from now on."
                  : "This channel wasn't in my free-response list.")
          .queue();
    }
  }

  /**
   * Lists all configured free-response channels (AI category, AI channel IDs, and allowlisted
   * channels).
   */
  private void handleChannelList(MessageReceivedEvent event) {
    Set<Long> ids = ChannelConfig.getFreeChannels();
    Set<Long> aiIds = ChannelConfig.getAiChannels();
    Long aiCategoryId = ChannelConfig.getAiCategoryId();

    StringBuilder msg = new StringBuilder();

    // Show the default category scope
    if (aiCategoryId != null) {
      msg.append("**Default AI Scope:** <#")
          .append(aiCategoryId)
          .append("> (Phoenix Industries category --” all channels within this category)\n\n");
    }

    // Show any explicit AI channels
    if (!aiIds.isEmpty()) {
      String aiList =
          aiIds.stream()
              .map(id -> "<#" + id + ">")
              .collect(java.util.stream.Collectors.joining(", "));
      msg.append("**AI Scoped Channels:** ").append(aiList).append("\n\n");
    }

    // Show free-response channels added via @bot allowchannel
    if (ids.isEmpty()) {
      msg.append("No additional free-response channels configured yet.");
      if ((aiCategoryId != null || !aiIds.isEmpty()) && msg.length() <= 20) {
        msg.insert(0, "");
      }
    } else {
      String list =
          ids.stream()
              .map(id -> "<#" + id + ">")
              .collect(java.util.stream.Collectors.joining(", "));
      msg.append("**Additional Free-Response Channels:** ").append(list);
    }

    event.getChannel().sendMessage(msg.toString().trim()).queue();
  }

  /**
   * Adds or removes the current channel from the GUI panel display list (admin only).
   */
  private void handleGuiChannelAllow(MessageReceivedEvent event, boolean allow) {
    if (!isAdmin(event.getMember())) {
      event.getChannel().sendMessage("Only admins can manage GUI channels.").queue();
      return;
    }
    long channelId = event.getChannel().getIdLong();
    if (allow) {
      boolean added = ChannelConfig.addGuiChannel(channelId);
      if (added && event.getChannel() instanceof TextChannel textChannel) {
        PanelAutoPublisher.refreshChannelPanel(textChannel);
      }
      event
          .getChannel()
          .sendMessage(
              added
                  ? "-... Got it! The GUI panel will now display in **#"
                    + event.getChannel().getName()
                    + "**."
                  : "This channel is already in my GUI panel list.")
          .queue();
    } else {
      boolean removed = ChannelConfig.removeGuiChannel(channelId);
      if (removed) {
        PanelStateStore.clear(channelId);
      }
      event
          .getChannel()
          .sendMessage(
              removed
                  ? "Done. The GUI panel will no longer display in **#"
                    + event.getChannel().getName()
                    + "**."
                  : "This channel wasn't in my GUI panel list.")
          .queue();
    }
  }

  /**
   * Lists all channels currently configured to display the GUI panel.
   */
  private void handleGuiChannelList(MessageReceivedEvent event) {
    Set<Long> guiIds = ChannelConfig.getGuiChannels();

    StringBuilder msg = new StringBuilder();

    if (guiIds.isEmpty()) {
      msg.append("No GUI panel channels configured yet.");
    } else {
      String list =
          guiIds.stream()
              .map(id -> "<#" + id + ">")
              .collect(java.util.stream.Collectors.joining(", "));
      msg.append("**GUI Panel Channels:** ").append(list);
    }

    event.getChannel().sendMessage(msg.toString()).queue();
  }

  /**
   * Displays autonomous/community learning diagnostics to an admin.
   */
  private void handleLearningStats(MessageReceivedEvent event) {
    if (!isAdmin(event.getMember())) {
      event.getChannel().sendMessage("Only admins can view learning diagnostics.").queue();
      return;
    }
    StringBuilder cfg = new StringBuilder();
    cfg.append("\n\n**Learning Modes**\n")
        .append("--¢ Autonomous feedback: ")
        .append(BotConfig.AUTONOMOUS_LEARNING_ENABLED)
        .append("\n")
        .append("--¢ Passive guild learning: ")
        .append(BotConfig.PASSIVE_LEARNING_ALL_GUILD_MESSAGES)
        .append("\n")
        .append("--¢ Passive history backfill: ")
        .append(BotConfig.PASSIVE_HISTORY_BACKFILL_ENABLED)
        .append("\n")
        .append("--¢ Auto personal phrase adaptation: ")
        .append(BotConfig.AUTO_PERSONAL_PHRASE_LEARNING_ENABLED);
    event.getChannel().sendMessage(PhraseLearner.formatLearningStats() + cfg).queue();
  }

  /**
   * Accepts a user-submitted data source URL and queues it for admin approval.
   */
  private void handleSourceSubmit(MessageReceivedEvent event, String args) {
    if (!isAuthorizedSourceSubmit(event.getMember(), event.getAuthor().getIdLong())) {
      event
          .getChannel()
          .sendMessage("Only admins or trusted operators can submit source overrides.")
          .queue();
      return;
    }
    if (!acquireCooldown(
        lastSourceSubmitEpochByUser,
        event.getAuthor().getIdLong(),
        BotConfig.SOURCE_OVERRIDE_SUBMIT_COOLDOWN_SECONDS)) {
      event
          .getChannel()
          .sendMessage("Slow down a bit - source submissions are rate-limited.")
          .queue();
      return;
    }
    if (args == null || args.isBlank() || !args.contains(":")) {
      event.getChannel().sendMessage("Format: `@bot source <dataset>: <https-url>`").queue();
      return;
    }
    int colon = args.indexOf(':');
    String dataset = args.substring(0, colon).trim();
    String url = args.substring(colon + 1).trim();
    SourceOverrideService.SubmitResult result =
        SourceOverrideService.submitSource(
            dataset, url, event.getAuthor().getIdLong(), event.getAuthor().getAsTag());
    if (result.accepted()) {
      event
          .getChannel()
          .sendMessage("-... " + result.message() + " Ticket: `" + result.ticketId() + "`")
          .queue();
    } else {
      event.getChannel().sendMessage("- " + result.message()).queue();
    }
  }

  /**
   * Approves the latest pending source override for a dataset (admin only).
   */
  private void handleSourceApprove(MessageReceivedEvent event, String dataset) {
    if (!isAdmin(event.getMember())) {
      event.getChannel().sendMessage("Only admins can approve sources.").queue();
      return;
    }
    if (!acquireCooldown(
        lastSourceApproveEpochByUser,
        event.getAuthor().getIdLong(),
        BotConfig.SOURCE_OVERRIDE_APPROVE_COOLDOWN_SECONDS)) {
      event.getChannel().sendMessage("Slow down a bit - approvals are rate-limited.").queue();
      return;
    }
    String msg = SourceOverrideService.approveLatest(dataset, event.getAuthor().getAsTag());
    event.getChannel().sendMessage(msg).queue();
  }

  /**
   * Displays all pending and approved source overrides (admin only).
   */
  private void handleSourceStatus(MessageReceivedEvent event) {
    if (!isAdmin(event.getMember())) {
      event.getChannel().sendMessage("Only admins can view source override status.").queue();
      return;
    }
    event.getChannel().sendMessage(SourceOverrideService.statusSummary()).queue();
  }

  // ---------------------------------------------------------------------------
  // Learn / Forget command handler
  // ---------------------------------------------------------------------------

  /**
   * Shared handler for learn (add=true) and forget (add=false) commands. Requires the calling
   * member to have MANAGE_SERVER or ADMINISTRATOR.
   */
  private void handleLearnCommand(MessageReceivedEvent event, String args, boolean add) {
    if (!isAdmin(event.getMember())) {
      event.getChannel().sendMessage("Only admins can teach me new phrases.").queue();
      return;
    }

    // Expect format: <intent>: <phrase text> OR <intent> = <phrase text>
    int separator = findLearnSeparatorIndex(args);
    if (separator < 0) {
      event
          .getChannel()
          .sendMessage(
              "Format: `@bot "
                  + (add ? "learn" : "forget")
                  + " <intent>: <phrase>` or `@bot "
                  + (add ? "learn" : "forget")
                  + " <intent> = <phrase>`\n"
                  + "Valid intents: "
                  + PhraseLearner.validIntentNames())
          .queue();
      return;
    }

    String intentName = args.substring(0, separator).trim();
    String phrase = args.substring(separator + 1).trim();

    if (phrase.isEmpty()) {
      event.getChannel().sendMessage("Phrase cannot be empty.").queue();
      return;
    }
    if (add && !SafetyGuard.isSafeLearningPhrase(phrase)) {
      event
          .getChannel()
          .sendMessage("I can't learn that phrase. Keep training phrases safe and clean.")
          .queue();
      return;
    }

    TriggerEngine.Intent intent = PhraseLearner.parseIntent(intentName);
    if (intent == null) {
      if (add && args.indexOf('=') >= 0) {
        boolean learnedAlias = QueryAliasService.learnAliasPair(intentName, phrase);
        if (learnedAlias) {
          event
              .getChannel()
              .sendMessage(
                  "-... Got it. Learned alias pair `"
                      + intentName.toLowerCase(Locale.ROOT)
                      + "` <-> `"
                      + phrase.toLowerCase(Locale.ROOT)
                      + "`.")
              .queue();
          return;
        }
      }
      event
          .getChannel()
          .sendMessage(
              "Unknown intent `"
                  + intentName
                  + "`. Valid intents: "
                  + PhraseLearner.validIntentNames()
                  + "\nTip: use `@bot <term> = <meaning>` to teach an alias pair.")
          .queue();
      return;
    }

    if (add) {
      boolean added = PhraseLearner.addPhrase(intent, phrase);
      event
          .getChannel()
          .sendMessage(
              added
                  ? "-... Got it! I learned: *\""
                    + phrase
                    + "\"* for `"
                    + intent.name().toLowerCase()
                    + "`."
                  : "I already know that phrase for `" + intent.name().toLowerCase() + "`.")
          .queue();
    } else {
      boolean removed = PhraseLearner.removePhrase(intent, phrase);
      event
          .getChannel()
          .sendMessage(
              removed
                  ? "Removed: *\"" + phrase + "\"* from `" + intent.name().toLowerCase() + "`."
                  : "I don't have that phrase for `" + intent.name().toLowerCase() + "`.")
          .queue();
    }
  }

  /**
   * User-level personal phrase learning commands (no admin required). Supports both ':' and '='
   * separators.
   */
  private void handleUserPhraseCommand(MessageReceivedEvent event, String args, boolean add) {
    int separatorIdx = findLearnSeparatorIndex(args);
    if (separatorIdx < 0) {
      event
          .getChannel()
          .sendMessage(
              "Format: `@bot "
                  + (add ? "learnme" : "forgetme")
                  + " <intent>: <phrase>` or `@bot "
                  + (add ? "learnme" : "forgetme")
                  + " <intent> = <phrase>`\n"
                  + "Valid intents: "
                  + PhraseLearner.validIntentNames())
          .queue();
      return;
    }

    String intentName = args.substring(0, separatorIdx).trim();
    String phrase = args.substring(separatorIdx + 1).trim();
    if (phrase.isEmpty()) {
      event.getChannel().sendMessage("Phrase cannot be empty.").queue();
      return;
    }
    if (add && !SafetyGuard.isSafeLearningPhrase(phrase)) {
      event
          .getChannel()
          .sendMessage("I can't learn that phrase. Try a cleaner style line.")
          .queue();
      return;
    }

    TriggerEngine.Intent intent = PhraseLearner.parseIntent(intentName);
    if (intent == null) {
      event
          .getChannel()
          .sendMessage(
              "Unknown intent `"
                  + intentName
                  + "`. Valid intents: "
                  + PhraseLearner.validIntentNames())
          .queue();
      return;
    }

    long userId = event.getAuthor().getIdLong();
    if (add) {
      boolean added = PhraseLearner.addUserPhrase(userId, intent, phrase);
      event
          .getChannel()
          .sendMessage(
              added
                  ? "-... Learned for *your* style: *\""
                    + phrase
                    + "\"* (`"
                    + intent.name().toLowerCase()
                    + "`)."
                  : "I already have that personal phrase for you.")
          .queue();
    } else {
      boolean removed = PhraseLearner.removeUserPhrase(userId, intent, phrase);
      event
          .getChannel()
          .sendMessage(
              removed
                  ? "Removed from your personal phrase profile."
                  : "I don't have that personal phrase saved for you.")
          .queue();
    }
  }

  private boolean hasLearnSeparator(String args) {
    return findLearnSeparatorIndex(args) >= 0;
  }

  private int findLearnSeparatorIndex(String args) {
    int colonIdx = args.indexOf(':');
    int equalsIdx = args.indexOf('=');
    if (colonIdx >= 0 && equalsIdx >= 0) {
      return Math.min(colonIdx, equalsIdx);
    }
    if (colonIdx >= 0) {
      return colonIdx;
    }
    return equalsIdx;
  }

  /**
   * Records a missing-data report and tries a live refresh to confirm/fix immediately.
   *
   * <p>Format: @bot missing <dataset>: <item> [| notes]
   */
  private void handleMissingReportCommand(MessageReceivedEvent event, String args) {
    if (args == null || args.isBlank() || !args.contains(":")) {
      event
          .getChannel()
          .sendMessage(
              "Format: `@bot missing <dataset>: <item> [| notes]`\n"
                  + "Example: `@bot missing mining: XMT XL | module not listed in menu`\n"
                  + "Datasets: "
                  + MissingDataReportService.supportedDatasetsHelp())
          .queue();
      return;
    }

    int colon = args.indexOf(':');
    String dataset = args.substring(0, colon).trim();
    String itemAndNotes = args.substring(colon + 1).trim();

    String item = itemAndNotes;
    String notes = "";
    int pipe = itemAndNotes.indexOf('|');
    if (pipe >= 0) {
      item = itemAndNotes.substring(0, pipe).trim();
      notes = itemAndNotes.substring(pipe + 1).trim();
    }

    if (item.isBlank()) {
      event
          .getChannel()
          .sendMessage("Please include the missing item name after the dataset.")
          .queue();
      return;
    }

    final String datasetFinal = dataset;
    final String itemFinal = item;
    final String notesFinal = notes;

    event
        .getChannel()
        .sendMessage("Noted. Logging your report and checking live data now...")
        .queue(
            status ->
                CompletableFuture.supplyAsync(
                        () ->
                            MissingDataReportService.reportAndAttemptFix(
                                datasetFinal,
                                itemFinal,
                                notesFinal,
                                event.getAuthor().getIdLong(),
                                event.getAuthor().getAsTag(),
                                event.getGuild().getIdLong(),
                                event.getChannel().getIdLong()))
                    .thenAccept(
                        result -> {
                          StringBuilder msg = new StringBuilder();
                          msg.append("Saved report **")
                              .append(result.reportId())
                              .append("** for **")
                              .append(result.dataset())
                              .append("**.\n");

                          if (result.alreadyPresent()) {
                            msg.append("I can already find this as **")
                                .append(result.resolvedName())
                                .append("** in current data.");
                          } else if (result.foundAfterRefresh()) {
                            msg.append("I refreshed and found **")
                                .append(result.resolvedName())
                                .append("**.");
                          } else {
                            if (result.refreshAttempted()) {
                              msg.append(
                                  result.refreshSucceeded()
                                      ? "Refresh completed, but it still looks missing."
                                      : "Live refresh failed, so I logged it for follow-up.");
                            } else {
                              msg.append(
                                  "This dataset cannot be refreshed live yet, but I logged it.");
                            }

                            if (!result.suggestions().isEmpty()) {
                              msg.append("\nClosest matches: ")
                                  .append(String.join(", ", result.suggestions()));
                            }
                            if (result.queuedForManualReview()) {
                              msg.append("\nAdded to manual review queue: ")
                                  .append(result.manualQueuePath());
                            }
                          }

                          status.editMessage(msg.toString().trim()).queue();
                        })
                    .exceptionally(
                        ex -> {
                          String reason =
                              ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                          status
                              .editMessage("I could not process that report right now: " + reason)
                              .queue();
                          return null;
                        }));
  }

  /**
   * Tries to detect and process a natural-language missing-data report embedded in a free-form
   * message (rather than the explicit {@code @bot missing} command format).
   *
   * @return {@code true} if a report was detected and queued, {@code false} otherwise.
   */
  private boolean tryHandleNaturalMissingReport(MessageReceivedEvent event, String text) {
    NaturalMissingRequest req = parseNaturalMissingRequest(text);
    if (req == null) {
      return false;
    }

    event
        .getChannel()
        .sendMessage("Got it --” logging missing data report and checking live sources now...")
        .queue(
            status ->
                CompletableFuture.supplyAsync(
                        () ->
                            MissingDataReportService.reportAndAttemptFix(
                                req.dataset,
                                req.item,
                                req.notes,
                                event.getAuthor().getIdLong(),
                                event.getAuthor().getAsTag(),
                                event.getGuild().getIdLong(),
                                event.getChannel().getIdLong()))
                    .thenAccept(
                        result -> {
                          StringBuilder msg = new StringBuilder();
                          msg.append("Saved report **")
                              .append(result.reportId())
                              .append("** for **")
                              .append(result.dataset())
                              .append("**.");

                          if (result.alreadyPresent()) {
                            msg.append(" I can already find **")
                                .append(result.resolvedName())
                                .append("**.");
                          } else if (result.foundAfterRefresh()) {
                            msg.append(" Refreshed and found **")
                                .append(result.resolvedName())
                                .append("**.");
                          } else {
                            msg.append(
                                result.refreshAttempted()
                                    ? (result.refreshSucceeded()
                                       ? " Refresh completed, but it still looks missing."
                                    : " Refresh failed; report kept for follow-up.")
                                    : " This dataset does not support live refresh yet, but report is saved.");
                            if (!result.suggestions().isEmpty()) {
                              msg.append("\nClosest matches: ")
                                  .append(String.join(", ", result.suggestions()));
                            }
                            if (result.queuedForManualReview()) {
                              msg.append("\nAdded to manual review queue: ")
                                  .append(result.manualQueuePath());
                            }
                          }

                          status.editMessage(msg.toString()).queue();
                        })
                    .exceptionally(
                        ex -> {
                          String reason =
                              ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                          status
                              .editMessage("Could not process missing-data request: " + reason)
                              .queue();
                          return null;
                        }));
    return true;
  }

  /**
   * Attempts to extract a structured missing-data request from freeform user input. Returns
   * {@code null} when no clear signal words or data tokens are present.
   */
  private NaturalMissingRequest parseNaturalMissingRequest(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String lower = text.toLowerCase();

    boolean signal =
        lower.contains("missing data")
            || lower.contains("something missing")
            || lower.contains("is missing")
            || lower.contains("not listed")
            || lower.contains("not in data");
    if (!signal) {
      return null;
    }

    String datasetToken = null;
    int datasetIndex = -1;
    for (String hint : MISSING_DATA_DATASET_HINTS) {
      int idx = lower.indexOf(hint);
      if (idx >= 0 && (datasetIndex < 0 || idx < datasetIndex)) {
        String normalized = MissingDataReportService.normalizeDataset(hint);
        if (normalized != null) {
          datasetToken = hint;
          datasetIndex = idx;
        }
      }
    }
    if (datasetToken == null) {
      return null;
    }

    String dataset = MissingDataReportService.normalizeDataset(datasetToken);
    if (dataset == null) {
      return null;
    }

    String item = "";
    int colon = text.indexOf(':');
    if (colon >= 0 && colon + 1 < text.length()) {
      item = text.substring(colon + 1).trim();
    }
    if (item.isBlank()) {
      Matcher m = QUOTED_TEXT.matcher(text);
      if (m.find()) {
        item = m.group(1).trim();
      }
    }
    if (item.isBlank() && datasetIndex >= 0) {
      int after = datasetIndex + datasetToken.length();
      if (after < text.length()) {
        item = text.substring(after).trim();
        item =
            item.replaceFirst("^(is|are|was|were|missing|data|for|from|the|a|an)\\s+", "").trim();
      }
    }

    if (item.isBlank()) {
      return null;
    }
    int notesSep = item.indexOf('|');
    String notes = text;
    if (notesSep >= 0) {
      notes = item.substring(notesSep + 1).trim();
      item = item.substring(0, notesSep).trim();
    }

    item = item.replaceFirst("(?i)\\s+(please|thanks|thank you).*$", "").trim();
    if (item.length() > 120) {
      item = item.substring(0, 120).trim();
    }
    if (item.isBlank()) {
      return null;
    }

    return new NaturalMissingRequest(dataset, item, notes);
  }

  /**
   * Lightweight data carrier for a natural-language missing-data report parsed out of a message.
   */
  private static class NaturalMissingRequest {

    final String dataset;
    final String item;
    final String notes;

    NaturalMissingRequest(String dataset, String item, String notes) {
      this.dataset = dataset;
      this.item = item;
      this.notes = notes;
    }
  }

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  /**
   * Heuristic: user-to-user chatter should usually not trigger AI responses. We still allow normal
   * bot processing for explicit bot-addressed patterns.
   */
  private boolean isLikelyPeerToPeerChat(MessageReceivedEvent event, String text) {
    if (text == null || text.isBlank()) {
      return true;
    }

    String lower = text.toLowerCase(Locale.ROOT);

    // If they say "bot/phoenix" they're probably talking to the AI.
    if (lower.contains("bot") || lower.contains("phoenix")) {
      return false;
    }

    // Replying to a non-bot message usually means user-to-user dialogue.
    if (isReplyToHuman(event)) {
      return true;
    }

    // Mentioning another human user (not the bot) is likely peer chat.
    long nonBotMentions =
        event.getMessage().getMentions().getUsers().stream()
            .filter(u -> !u.isBot())
            .filter(u -> u.getIdLong() != event.getAuthor().getIdLong())
            .count();
    if (nonBotMentions > 0) {
      return true;
    }

    // Conversational direct-address openings commonly used between users.
    if (lower.startsWith("bro ")
        || lower.startsWith("dude ")
        || lower.startsWith("man ")
        || lower.startsWith("yo ")
        || lower.startsWith("hey ")) {
      // Keep question-style asks available for bot responses.
      return !lower.contains("?");
    }

    return false;
  }

  private boolean isReplyToHuman(MessageReceivedEvent event) {
    if (event.getMessage().getMessageReference() == null) {
      return false;
    }
    var referenced = event.getMessage().getReferencedMessage();
    return referenced != null && !referenced.getAuthor().isBot();
  }

  /**
   * Strict moderation redirect for politics topics in AI channels.
   */
  private boolean tryPoliticsRedirect(MessageReceivedEvent event, String text) {
    if (!containsKeyword(text, POLITICS_KEYWORDS)) {
      return false;
    }
    if (!canInterjectNow(event.getChannel().getIdLong(), false)) {
      return false;
    }

    event.getChannel().sendMessage(pickRandom(POLITICS_REDIRECT_LINES)).queue();
    return true;
  }

  /**
   * Occasional Deadpool-style informational interjection for SC topics.
   */
  private boolean tryTopicInterjection(MessageReceivedEvent event, String text) {
    if (!containsKeyword(text, SC_TOPIC_KEYWORDS)) {
      return false;
    }
    if (!canInterjectNow(event.getChannel().getIdLong(), true)) {
      return false;
    }

    event.getChannel().sendMessage(pickRandom(SC_INTERJECTION_LINES)).queue();
    return true;
  }

  /**
   * Returns {@code true} when it is appropriate to send a spontaneous interjection in the given
   * channel, applying the cooldown and optional random-chance gate.
   *
   * @param randomGate when {@code true} an additional random roll must pass before injecting
   */
  private boolean canInterjectNow(long channelId, boolean randomGate) {
    long now = System.currentTimeMillis() / 1000;
    long last = lastInterjectionEpochByChannel.getOrDefault(channelId, 0L);
    if (now - last < INTERJECTION_COOLDOWN_SECONDS) {
      return false;
    }

    if (randomGate) {
      int roll = ThreadLocalRandom.current().nextInt(100);
      if (roll >= INTERJECTION_CHANCE_PERCENT) {
        return false;
      }
    }

    lastInterjectionEpochByChannel.put(channelId, now);
    return true;
  }

  private boolean containsKeyword(String text, List<String> keywords) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    for (String k : keywords) {
      if (lower.contains(k)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Picks a random element from a list.
   */
  private String pickRandom(List<String> list) {
    return list.get(ThreadLocalRandom.current().nextInt(list.size()));
  }

  /**
   * In AI-scoped channels, occasionally join peer conversation naturally instead of staying silent
   * on every user-to-user message.
   */
  private boolean trySocialChannelReply(MessageReceivedEvent event, String text) {
    if (!AI_SOCIAL_MODE) {
      return false;
    }
    if (text == null || text.isBlank()) {
      return false;
    }
    if (isLowSignalAck(text)) {
      return false;
    }

    long channelId = event.getChannel().getIdLong();
    long now = System.currentTimeMillis() / 1000L;
    long last = lastSocialReplyEpochByChannel.getOrDefault(channelId, 0L);
    if (now - last < AI_SOCIAL_COOLDOWN_SECONDS) {
      return false;
    }

    String lower = text.toLowerCase(Locale.ROOT);
    int chance = AI_SOCIAL_REPLY_CHANCE_PERCENT;
    if (lower.contains("?")) {
      chance += 18;
    }
    if (TriggerEngine.isFollowUpSignal(lower)) {
      chance += 14;
    }
    if (looksLikeOpinionPrompt(lower)) {
      chance += 15;
    }
    if (text.length() >= 90) {
      chance += 8;
    }
    chance = Math.max(10, Math.min(95, chance));

    int roll = ThreadLocalRandom.current().nextInt(100);
    if (roll >= chance) {
      return false;
    }

    long userId = event.getAuthor().getIdLong();
    TriggerEngine.Intent detected = TriggerEngine.detectIntent(text);
    TriggerEngine.Intent intent =
        (detected == TriggerEngine.Intent.NEUTRAL) ? TriggerEngine.Intent.CASUAL_CHAT : detected;
    EmotionEngine.Emotion emotion = EmotionEngine.detectEmotion(text);
    PersonalityEngine.PersonalityProfile profile = PersonalityEngine.decideProfile(intent, emotion);

    ConversationMemoryService.recordUserMessage(
        userId, event.getAuthor().getName(), text, intent, emotion);

    String[] result =
        SentenceGenerator.generateTrackedForUser(intent, emotion, profile, userId, channelId, text);
    String reply = result[0];
    if (!reply.contains("?") && ThreadLocalRandom.current().nextInt(100) < 35) {
      reply = reply + " What's your take?";
    }
    final String finalReply = withExpressiveFlair(sanitizeOutgoingReply(reply), channelId, true);
    final String phrase = result[1];
    final TriggerEngine.Intent finalIntent = intent;

    event
        .getChannel()
        .sendMessage(finalReply)
        .queue(
            sentMessage -> {
              PhraseLearner.trackReply(sentMessage.getIdLong(), finalIntent, phrase);
              rememberTrackedReplyTarget(channelId, userId, sentMessage.getIdLong());
              String subject =
                  !ConversationMemoryService.getRecentTopics(userId).isEmpty()
                      ? ConversationMemoryService.getRecentTopics(userId).get(0)
                      : "conversation";
              ConversationMemoryService.recordBotReplyContext(
                  userId, "casual", subject, responseAsksFollowUp(finalReply));
              ConversationMemoryService.touchSession(channelId, userId);
              lastSocialReplyEpochByChannel.put(channelId, now);
            });
    return true;
  }

  private boolean looksLikeOpinionPrompt(String lower) {
    return lower.contains("what do you think")
        || lower.contains("your take")
        || lower.contains("thoughts")
        || lower.contains("do you agree")
        || lower.contains("fair point")
        || lower.contains("makes sense?");
  }

  private boolean tryGifReactionReply(
      MessageReceivedEvent event, boolean directBotContext, boolean isAiScopedChannel) {
    if (!BotConfig.GIF_REPLIES_ENABLED) {
      return false;
    }
    if (!directBotContext && !isAiScopedChannel) {
      return false;
    }

    long channelId = event.getChannel().getIdLong();
    long now = System.currentTimeMillis() / 1000L;
    long last = lastGifReplyEpochByChannel.getOrDefault(channelId, 0L);
    if (now - last < BotConfig.GIF_REPLY_COOLDOWN_SECONDS) {
      return false;
    }

    if (!directBotContext) {
      int roll = ThreadLocalRandom.current().nextInt(100);
      if (roll >= BotConfig.GIF_REPLY_CHANCE_PERCENT) {
        return false;
      }
    }

    String reply = pickRandom(GIF_REACTION_LINES);
    if (ThreadLocalRandom.current().nextInt(100) < 60) {
      reply = reply + " " + pickRandom(EXPRESSIVE_EMOJIS);
    }
    if (ThreadLocalRandom.current().nextInt(100) < BotConfig.GIF_REPLY_DECORATION_CHANCE_PERCENT) {
      reply = reply + "\n" + pickRandom(BOT_GIF_LINKS);
      lastGifReplyEpochByChannel.put(channelId, now);
    }

    reply = reply + " " + PersonaVoice.gifCaption();

    String finalReply = sanitizeOutgoingReply(reply);
    event
        .getChannel()
        .sendMessage(finalReply)
        .queue(
            sentMessage -> {
              ConversationMemoryService.touchSession(channelId, event.getAuthor().getIdLong());
              lastGifReplyEpochByChannel.put(channelId, now);
            });
    return true;
  }

  private boolean containsGifSignal(MessageReceivedEvent event, String rawText) {
    String lower = rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
    if (lower.contains(".gif") || lower.contains("tenor.com") || lower.contains("giphy.com")) {
      return true;
    }

    for (var attachment : event.getMessage().getAttachments()) {
      String ext = attachment.getFileExtension();
      String ct = attachment.getContentType();
      if ((ext != null && ext.equalsIgnoreCase("gif"))
          || (ct != null && ct.toLowerCase(Locale.ROOT).contains("gif"))) {
        return true;
      }
    }

    return false;
  }

  /**
   * Optionally appends an expressive emoji and/or a GIF link to an outgoing reply. Applies both the
   * emoji chance and the GIF cooldown independently.
   *
   * @param allowGif when {@code false} no GIF decoration is appended
   */
  private String withExpressiveFlair(String reply, long channelId, boolean allowGif) {
    if (reply == null || reply.isBlank()) {
      return reply;
    }
    String out = reply;

    if (ThreadLocalRandom.current().nextInt(100) < BotConfig.EMOJI_REPLY_CHANCE_PERCENT) {
      out = out + " " + pickRandom(EXPRESSIVE_EMOJIS);
    }

    if (allowGif
        && ThreadLocalRandom.current().nextInt(100)
        < BotConfig.GIF_REPLY_DECORATION_CHANCE_PERCENT) {
      long now = System.currentTimeMillis() / 1000L;
      long last = lastGifReplyEpochByChannel.getOrDefault(channelId, 0L);
      if (now - last >= BotConfig.GIF_REPLY_COOLDOWN_SECONDS) {
        out = out + "\n" + pickRandom(BOT_GIF_LINKS);
        lastGifReplyEpochByChannel.put(channelId, now);
      }
    }

    return sanitizeOutgoingReply(out);
  }

  /**
   * Final safety/persona pass applied to every outgoing message. Blocks disallowed content,
   * enforces persona voice, and trims to Discord's 1900-char limit.
   */
  private String sanitizeOutgoingReply(String reply) {
    if (reply == null || reply.isBlank()) {
      return "Say that again and I'll jump right back in.";
    }
    if (SafetyGuard.isDisallowedResponse(reply)) {
      return "Sorry, I can't assist with that.";
    }
    reply = PersonaVoice.enforceDeadpoolVoice(reply);
    if (reply.length() > 1900) {
      return reply.substring(0, 1900).trim();
    }
    return reply;
  }

  /**
   * Returns recent SC domain context for a user, or null if expired/missing.
   */
  private String getRecentScDomain(long userId) {
    Long when = lastScDomainEpochByUser.get(userId);
    if (when == null) {
      lastScDomainByUser.remove(userId);
      return null;
    }

    long now = System.currentTimeMillis() / 1000L;
    if (now - when > SC_CONTEXT_TTL_SECONDS) {
      lastScDomainByUser.remove(userId);
      lastScDomainEpochByUser.remove(userId);
      return null;
    }

    return lastScDomainByUser.get(userId);
  }

  private boolean isAdmin(Member member) {
    return member != null
        && (member.isOwner()
        || member.hasPermission(Permission.MANAGE_SERVER)
        || member.hasPermission(Permission.ADMINISTRATOR));
  }

  /**
   * Checks whether the submitter is authorized to submit source overrides. Admins are always
   * allowed; non-admins are only allowed when {@code SOURCE_OVERRIDE_ADMIN_ONLY} is false and the
   * user ID is in the trusted list.
   */
  private boolean isAuthorizedSourceSubmit(Member member, long userId) {
    if (isAdmin(member)) {
      return true;
    }
    if (BotConfig.SOURCE_OVERRIDE_ADMIN_ONLY) {
      return false;
    }
    return BotConfig.SOURCE_OVERRIDE_TRUSTED_USER_IDS.contains(Long.toString(userId));
  }

  /**
   * Rate-limit helper that returns {@code true} only if sufficient time has elapsed since the last
   * use, and records the current timestamp when it does.
   */
  private boolean acquireCooldown(Map<Long, Long> map, long userId, long cooldownSeconds) {
    long now = System.currentTimeMillis() / 1000L;
    long gate = Math.max(1L, cooldownSeconds);
    long last = map.getOrDefault(userId, 0L);
    if (now - last < gate) {
      return false;
    }
    map.put(userId, now);
    return true;
  }

  /**
   * True if the message explicitly replies to a bot-authored message.
   */
  private boolean isReplyToBot(MessageReceivedEvent event) {
    if (event.getMessage().getMessageReference() == null) {
      return false;
    }
    var referenced = event.getMessage().getReferencedMessage();
    return referenced != null && referenced.getAuthor().isBot();
  }

  /**
   * Detects weather-related questions to provide a direct capability response.
   */
  private boolean isWeatherQuestion(String text) {
    if (text == null) {
      return false;
    }
    String lower = text.toLowerCase();
    return lower.contains("weather")
        || lower.contains("temperature")
        || lower.contains("forecast")
        || lower.contains("rain")
        || lower.contains("snow")
        || lower.contains("wind");
  }

  /**
   * Detect boredom/entertainment prompts so we can guide users to useful chat paths.
   */
  private boolean isBoredPrompt(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase(Locale.ROOT).trim();
    return lower.equals("boring")
        || lower.equals("bored")
        || lower.equals("im bored")
        || lower.equals("i'm bored")
        || lower.contains("entertain me")
        || lower.contains("this is boring")
        || lower.contains("so boring");
  }

  /**
   * True when message is a likely follow-up to the previous SC answer context.
   */
  private boolean isScContextFollowUp(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase(Locale.ROOT).trim();
    return lower.startsWith("what about")
        || lower.startsWith("how about")
        || lower.startsWith("and ")
        || lower.startsWith("same for")
        || lower.startsWith("and for")
        || lower.startsWith("what if")
        || lower.contains("more details")
        || lower.equals("details")
        || lower.equals("stats")
        || lower.contains("compare")
        || lower.contains("that one");
  }

  /**
   * Whether the message already specifies an SC domain and does not need context carry-over.
   */
  private boolean hasExplicitScDomainHint(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.contains("mine")
        || lower.contains("mining")
        || lower.contains("trade")
        || lower.contains("commodity")
        || lower.contains("ship")
        || lower.contains("weapon")
        || lower.contains("component")
        || lower.contains("refinery")
        || lower.contains("salvage")
        || lower.contains("location")
        || lower.contains("mission");
  }

  /**
   * Infer a canonical SC domain token used for contextual follow-up routing.
   */
  private String inferScDomain(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains("mine") || lower.contains("mining")) {
      return "mining";
    }
    if (lower.contains("trade") || lower.contains("commodity")) {
      return "trade";
    }
    if (lower.contains("ship")) {
      return "ship";
    }
    if (lower.contains("weapon")) {
      return "weapon";
    }
    if (lower.contains("component")) {
      return "component";
    }
    if (lower.contains("refinery")) {
      return "refinery";
    }
    if (lower.contains("salvage")) {
      return "salvage";
    }
    if (lower.contains("location") || lower.contains("where is")) {
      return "location";
    }
    if (lower.contains("mission")) {
      return "mission";
    }
    return null;
  }

  /**
   * Detect short follow-up style preferences after a mining discussion.
   */
  private boolean isMiningPreferenceFollowUp(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.contains("risk")
        || lower.contains("all in")
        || lower.contains("yolo")
        || lower.contains("profit")
        || lower.contains("safe")
        || lower.contains("safer")
        || lower.contains("beginner")
        || lower.contains("steady")
        || lower.contains("full send")
        || lower.contains("send it");
  }

  /**
   * Infers a canonical SC subject name (ship/weapon/etc.) from the user's message text.
   */
  private String inferScSubject(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    Matcher quoted = QUOTED_TEXT.matcher(text);
    if (quoted.find()) {
      return quoted.group(1).trim();
    }

    String[] hints = {
        "ship",
        "weapon",
        "component",
        "commodity",
        "mission",
        "location",
        "refinery",
        "mining",
        "salvage",
        "trade"
    };
    String lower = text.toLowerCase(Locale.ROOT);
    for (String hint : hints) {
      int idx = lower.indexOf(hint);
      if (idx < 0) {
        continue;
      }
      String tail = text.substring(Math.min(text.length(), idx + hint.length())).trim();
      tail = tail.replaceFirst("^(for|about|on|called|named|the|a|an)\\s+", "").trim();
      if (!tail.isBlank()) {
        String[] tokens = tail.split("\\s+");
        int max = Math.min(tokens.length, 4);
        return String.join(" ", java.util.Arrays.copyOfRange(tokens, 0, max)).trim();
      }
    }
    return "";
  }

  /**
   * Returns {@code true} when the reply ends with a question or an open-ended invitation.
   */
  private boolean responseAsksFollowUp(String reply) {
    if (reply == null || reply.isBlank()) {
      return false;
    }
    String lower = reply.toLowerCase(Locale.ROOT);
    return lower.contains("?")
        || lower.contains("tell me")
        || lower.contains("want the")
        || lower.contains("if you want")
        || lower.contains("what are you");
  }

  /**
   * True when the user's recent topic queue includes the specified token.
   */
  private boolean hasRecentTopic(long userId, String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    String needle = token.toLowerCase(Locale.ROOT);
    for (String topic : ConversationMemoryService.getRecentTopics(userId)) {
      if (topic != null && topic.toLowerCase(Locale.ROOT).contains(needle)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Ignore messages that are pure conversational acknowledgements with no actionable content.
   */
  private boolean isLowSignalAck(String text) {
    String lower = text == null ? "" : text.trim().toLowerCase();
    // Single-word non-responses
    if (lower.equals("no")
        || lower.equals("nah")
        || lower.equals("nope")
        || lower.equals("ye")
        || lower.equals("yeah")
        || lower.equals("y")
        || lower.equals("ya")
        || lower.equals("k")
        || lower.equals("ok")
        || lower.equals("okay")
        || lower.equals("sure")
        // Pure confirmations / acknowledgements
        || lower.equals("i see")
        || lower.equals("makes sense")
        || lower.equals("that makes sense")
        || lower.equals("got it")
        || lower.equals("noted")
        || lower.equals("understood")
        || lower.equals("fair enough")
        || lower.equals("good point")
        || lower.equals("true")
        || lower.equals("true that")
        || lower.equals("sounds good")
        || lower.equals("alright")
        || lower.equals("alr")
        || lower.equals("yeah ok")
        || lower.equals("yep ok")
        // Emote-only
        || lower.matches("^:[a-z0-9_+-]+:$")
        || isOnlySymbols(text)) {
      return true;
    }
    return false;
  }

  /**
   * Returns true for short symbol/emoji-only messages with no letters/digits.
   */
  private boolean isOnlySymbols(String text) {
    if (text == null) {
      return false;
    }
    String trimmed = text.trim();
    if (trimmed.isEmpty() || trimmed.length() > 12) {
      return false;
    }
    return !trimmed.matches(".*[A-Za-z0-9].*");
  }

  /**
   * Returns {@code true} when the message looks like feedback about the mining GUI (e.g., "MOLE
   * only shows one head option"), triggering a canned explanation about the multi-head module
   * flow.
   */
  private boolean isMiningGuiFeedbackPrompt(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    boolean miningContext =
        lower.contains("mole")
            || lower.contains("mining")
            || lower.contains("laser")
            || lower.contains("head")
            || lower.contains("module");
    boolean feedback =
        lower.contains("missing")
            || lower.contains("needs")
            || lower.contains("option")
            || lower.contains("only 1")
            || lower.contains("only one")
            || lower.contains("not enough");
    return miningContext && feedback;
  }

  /**
   * Tries to detect an inline alias teaching pattern (e.g. {@code "X = Y"}) and persist the pair
   * via {@link Botcode.StarCitizen.QueryAliasService}.
   *
   * @return {@code true} when an alias was learned and a confirmation reply was sent
   */
  private boolean tryLearnQueryAlias(
      MessageReceivedEvent event, String text, boolean directBotContext) {
    if (!directBotContext || text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    boolean correctionFraming =
        lower.contains("correct yourself")
            || lower.contains("learn")
            || lower.contains("improve")
            || lower.contains("when asked")
            || lower.contains("means");
    boolean explicitAliasEquation = text.contains("=");
    if (!correctionFraming && !explicitAliasEquation) {
      return false;
    }

    Matcher m = ALIAS_CORRECTION_PATTERN.matcher(text);
    if (!m.find()) {
      return false;
    }

    String alias = m.group(1).trim();
    String expansion = m.group(2).trim();
    if (!hasLetters(alias) || !hasLetters(expansion)) {
      return false;
    }
    if (!SafetyGuard.isSafeLearningPhrase(expansion)) {
      return false;
    }

    boolean learned = QueryAliasService.learnAliasPair(alias, expansion);
    if (!learned) {
      return false;
    }

    event
        .getChannel()
        .sendMessage(
            "-... Got it. Learned alias pair `"
                + alias.toLowerCase(Locale.ROOT)
                + "` <-> `"
                + expansion.toLowerCase(Locale.ROOT)
                + "`.")
        .queue();
    return true;
  }

  /**
   * True when text contains at least one letter character.
   */
  private boolean hasLetters(String value) {
    return value != null && value.matches(".*[A-Za-z].*");
  }

  /**
   * Returns {@code true} when the message is worth recording as a passive learning signal. Rejects
   * blanks, pure-symbol strings, command prefixes, and disallowed content.
   */
  private boolean shouldRecordPassiveLearning(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String cleaned = text.trim();
    if (cleaned.length() < 4) {
      return false;
    }
    if (!hasLetters(cleaned)) {
      return false;
    }
    // Ignore command-like payloads and unsafe instruction prompts.
    if (cleaned.startsWith("@") || cleaned.startsWith("/") || cleaned.startsWith("!")) {
      return false;
    }
    return !SafetyGuard.isDisallowed(cleaned);
  }

  /**
   * Reads positive/negative feedback signals from a message and routes them to
   * {@link Botcode.AI.Personality.PhraseLearner} to up- or down-vote the phrase associated with the
   * user's last tracked bot reply.
   */
  private void applyImplicitQualityFeedback(MessageReceivedEvent event, String text) {
    if (!BotConfig.AUTONOMOUS_LEARNING_ENABLED) {
      return;
    }
    if (text == null || text.isBlank()) {
      return;
    }

    Long targetMessageId = resolveFeedbackTargetMessageId(event);
    if (targetMessageId == null || !PhraseLearner.isTracked(targetMessageId)) {
      return;
    }

    String lower = text.toLowerCase(Locale.ROOT);
    if (TriggerEngine.isCorrectionSignal(text)
        || containsAnySignal(lower, NEGATIVE_FEEDBACK_SIGNALS)) {
      PhraseLearner.downvoteForUser(targetMessageId, event.getAuthor().getIdLong());
      return;
    }

    if (containsAnySignal(lower, POSITIVE_FEEDBACK_SIGNALS)) {
      PhraseLearner.upvoteForUser(targetMessageId, event.getAuthor().getIdLong());
    }
  }

  /**
   * Resolves the bot message ID that implicit feedback should be applied to. Prefers a direct reply
   * reference, then falls back to the latest tracked reply for the user/channel pair within the TTL
   * window.
   */
  private Long resolveFeedbackTargetMessageId(MessageReceivedEvent event) {
    if (event.getMessage().getMessageReference() != null && isReplyToBot(event)) {
      return event.getMessage().getMessageReference().getMessageIdLong();
    }

    String key = feedbackKey(event.getChannel().getIdLong(), event.getAuthor().getIdLong());
    PendingFeedbackTarget pending = lastTrackedReplyByUserChannel.get(key);
    if (pending == null) {
      return null;
    }
    long now = System.currentTimeMillis() / 1000L;
    if (now - pending.createdEpoch > BotConfig.AUTONOMOUS_FEEDBACK_TTL_SECONDS) {
      lastTrackedReplyByUserChannel.remove(key);
      return null;
    }
    return pending.messageId;
  }

  /**
   * Returns {@code true} if the given text contains at least one of the provided signal strings.
   */
  private boolean containsAnySignal(String lower, List<String> signals) {
    for (String signal : signals) {
      if (lower.contains(signal)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Stores the most recent tracked bot reply message for a channel+user pair, used for implicit
   * feedback routing.
   */
  private void rememberTrackedReplyTarget(long channelId, long userId, long messageId) {
    long now = System.currentTimeMillis() / 1000L;
    lastTrackedReplyByUserChannel.put(
        feedbackKey(channelId, userId), new PendingFeedbackTarget(messageId, now));
  }

  /**
   * Composite key for the per-user/channel feedback and session tracking maps.
   */
  private String feedbackKey(long channelId, long userId) {
    return channelId + ":" + userId;
  }

  /**
   * Holds the bot message ID and creation epoch for autonomous feedback attribution.
   */
  private static class PendingFeedbackTarget {

    final long messageId;
    final long createdEpoch;

    PendingFeedbackTarget(long messageId, long createdEpoch) {
      this.messageId = messageId;
      this.createdEpoch = createdEpoch;
    }
  }

  /**
   * Persists SC domain/subject context and tracks the conversational reply for autonomous phrase
   * tuning after each AI-generated message is sent.
   */
  private void recordAiContextAndSession(
      AIResponder.AIResponse ai,
      String resolvedPrompt,
      String safeAiReply,
      long userId,
      MessageReceivedEvent event,
      String userText,
      long botMessageId) {
    String domain;
    String subject;
    switch (ai.source) {
      case STAR_CITIZEN:
        domain = inferScDomain(resolvedPrompt);
        subject = inferScSubject(resolvedPrompt);
        if (domain != null) {
          lastScDomainByUser.put(userId, domain);
          lastScDomainEpochByUser.put(userId, System.currentTimeMillis() / 1000L);
        }
        ConversationMemoryService.recordBotReplyContext(
            userId,
            domain != null ? domain : "star_citizen",
            subject,
            responseAsksFollowUp(safeAiReply));
        break;
      case SELF_FACTS:
        ConversationMemoryService.recordBotReplyContext(
            userId, "self", "phoenix bot", responseAsksFollowUp(safeAiReply));
        break;
      case WEB_LOOKUP:
        ConversationMemoryService.recordBotReplyContext(
            userId, "web", resolvedPrompt, responseAsksFollowUp(safeAiReply));
        break;
      case PREMIUM:
      default:
        String fallbackSubject =
            !ConversationMemoryService.getRecentTopics(userId).isEmpty()
                ? ConversationMemoryService.getRecentTopics(userId).get(0)
                : "conversation";
        ConversationMemoryService.recordBotReplyContext(
            userId, "casual", fallbackSubject, responseAsksFollowUp(safeAiReply));
        break;
    }

    TriggerEngine.Intent intent = TriggerEngine.detectIntent(userText);
    if (ai.source != AIResponder.Source.STAR_CITIZEN
        && ai.source != AIResponder.Source.WEB_LOOKUP
        && ai.source != AIResponder.Source.SELF_FACTS) {
      // Only track conversational-style AI replies for autonomous phrase tuning.
      PhraseLearner.trackReply(botMessageId, intent, safeAiReply);
      rememberTrackedReplyTarget(event.getChannel().getIdLong(), userId, botMessageId);
    }

    ConversationMemoryService.touchSession(event.getChannel().getIdLong(), userId);
  }

  /**
   * Extracts a short lead line from an AI ship reply to place above the embed. Returns a generic
   * Deadpool quip if the reply starts with bold ship-name formatting or exceeds 140 characters
   * (would look awkward as a lead sentence).
   */
  private String extractShipLeadLine(String aiReply) {
    if (aiReply == null || aiReply.isBlank()) {
      return "Shopping for ship organs now? Completely normal behavior.";
    }
    int split = aiReply.indexOf("\n\n");
    String lead = split > 0 ? aiReply.substring(0, split).trim() : aiReply.trim();
    if (lead.startsWith("**Ship")) {
      return "Shopping for ship organs now? Completely normal behavior.";
    }
    if (lead.length() > 140) {
      return "Shopping for ship organs now? Completely normal behavior.";
    }
    return lead;
  }

  // ---------------------------------------------------------------------------
  // Multi-user conversation handling
  // ---------------------------------------------------------------------------

  /**
   * Participates contextually in multi-user channel conversations, offering feedback,
   * suggestions, or insights based on what's being discussed.
   */
  private boolean tryMultiUserConversationInterjection(
      MessageReceivedEvent event, String text) {
    long channelId = event.getChannel().getIdLong();

    // Periodically prune old conversation data
    ChannelConversationService.pruneOldConversations();

    // Check if this is a multi-user conversation the bot should join
    if (!ChannelConversationService.shouldBotInterjact(channelId)) {
      return false;
    }

    // Get conversation context
    ChannelConversationService.ConversationContext context =
        ChannelConversationService.analyzeChannelConversation(channelId);

    // Don't respond to own bot in the context
    if (context.participantUserIds.contains(event.getJDA().getSelfUser().getIdLong())) {
      context.participantUserIds.remove(event.getJDA().getSelfUser().getIdLong());
    }

    // Need at least 2 other users for multi-user conversation
    if (context.participantUserIds.size() < 2) {
      return false;
    }

    // Generate contextual response based on conversation sentiment
    TriggerEngine.Intent intent = TriggerEngine.detectIntent(text);
    EmotionEngine.Emotion emotion = EmotionEngine.detectEmotion(text);
    PersonalityEngine.PersonalityProfile profile =
        PersonalityEngine.decideProfile(intent, emotion);
    long userId = event.getAuthor().getIdLong();

    String contextualReply =
        generateContextualGroupResponse(context, intent, emotion, text);
    if (contextualReply == null || contextualReply.isBlank()) {
      return false;
    }

    final String finalReply =
        withExpressiveFlair(sanitizeOutgoingReply(contextualReply), channelId, true);

    ConversationMemoryService.recordUserMessage(
        userId, event.getAuthor().getName(), text, intent, emotion);

    event
        .getChannel()
        .sendMessage(finalReply)
        .queue(
            sentMessage -> {
              PhraseLearner.trackReply(
                  sentMessage.getIdLong(),
                  intent,
                  "group_conversation");
              rememberTrackedReplyTarget(channelId, userId, sentMessage.getIdLong());
              ConversationMemoryService.recordBotReplyContext(
                  userId,
                  "group_chat",
                  context.dominantTopic,
                  responseAsksFollowUp(finalReply));
              ConversationMemoryService.touchSession(channelId, userId);
            });

    return true;
  }

  /**
   * Generates contextual responses tailored to multi-user group conversations.
   */
  private String generateContextualGroupResponse(
      ChannelConversationService.ConversationContext context,
      TriggerEngine.Intent intent,
      EmotionEngine.Emotion emotion,
      String lastMessage) {
    String sentiment = context.sentiment;
    String topic = context.dominantTopic;

    // If last message is a question, offer insight or perspective
    if ("question".equals(sentiment) || lastMessage.toLowerCase().contains("?")) {
      return generateQuestionResponse(context, lastMessage, topic);
    }

    // If someone is describing a problem, offer solutions or suggestions
    if ("problem".equals(sentiment)) {
      return generateProblemResponse(context, lastMessage, topic);
    }

    // For positive messages, add enthusiasm or build on the energy
    if ("positive".equals(sentiment)) {
      return generatePositiveResponse(context, lastMessage, topic);
    }

    // Default: add constructive banter or suggestions
    return generateNeutralResponse(context, lastMessage, topic);
  }

  private String generateQuestionResponse(
      ChannelConversationService.ConversationContext context,
      String lastMessage,
      String topic) {
    String lower = lastMessage.toLowerCase();

    // Star Citizen specific question handling
    if (lower.contains("ship") || lower.contains("weapon") ||
        lower.contains("mining") || lower.contains("trade")) {
      return pickRandom(List.of(
          "Good question --” I can help narrow this down. What's your main priority here?",
          "That's the real question right now. Quick follow-up: what's your playing style?",
          " Let me chime in --” I've got some data on that. What's your current focus?",
          "Valid. Here's the thing though: it depends on your setup. Tell me more?",
          "Solid inquiry. I'd need a bit more context --” what role are you planning?"
      ));
    }

    // General questions
    return pickRandom(List.of(
        "That's a solid question. Has anyone tried the obvious approach?",
        "Good call raising that. I'd suggest testing the approach first.",
        "Valid point. The answer usually hinges on what you're ultimately trying to do though.",
        "Real question. Might be worth considering both angles here."
    ));
  }

  private String generateProblemResponse(
      ChannelConversationService.ConversationContext context,
      String lastMessage,
      String topic) {
    return pickRandom(List.of(
        "Yikes. Have you tried the standard troubleshooting path yet, or shall we brainstorm?",
        "That's rough. Before you nuke it from orbit: have you confirmed the basics?",
        "Ah yeah, that's a known pain point. Quick workaround: check your settings.",
        "Ouch. Not ideal. The usual solution is to reset --” worth a shot?",
        "Big mood. Side note: make sure you're not hitting a known issue first."
    ));
  }

  private String generatePositiveResponse(
      ChannelConversationService.ConversationContext context,
      String lastMessage,
      String topic) {
    return pickRandom(List.of(
        "Yes! That energy. Don't forget to capitalize on it while momentum's high.",
        "That's what I like to see. Keep riding that wave.",
        "Approved. Chaos is looking optimistic today.",
        "Legendary. Now scale it.",
        "That move? *Chef's kiss*. What's next?"
    ));
  }

  private String generateNeutralResponse(
      ChannelConversationService.ConversationContext context,
      String lastMessage,
      String topic) {
    return pickRandom(List.of(
        "Interesting angle. Has anyone considered alternatives?",
        "Fair take. Most people don't realize the full scope here.",
        "Yeah, that's one way. Another approach could work too.",
        "Solid point. Related: there's more to consider.",
        "True story. Pro tip though: think about the bigger picture."
    ));
  }

  // ---------------------------------------------------------------------------
  // REACTION VOTING --” ... upvotes,  downvotes the bot phrase
  // ---------------------------------------------------------------------------

  /**
   * Handles emoji reactions on bot messages to score learned phrases.
   */
  @Override
  public void onMessageReactionAdd(MessageReactionAddEvent event) {
    if (event.getUser() != null && event.getUser().isBot()) {
      return;
    }

    long messageId = event.getMessageIdLong();
    if (!PhraseLearner.isTracked(messageId)) {
      return;
    }

    String emoji = event.getEmoji().getName();

    if (PhraseLearner.UPVOTE_EMOJIS.contains(emoji)) {
      PhraseLearner.upvoteForUser(messageId, event.getUserIdLong());
    } else if (PhraseLearner.DOWNVOTE_EMOJIS.contains(emoji)) {
      PhraseLearner.downvoteForUser(messageId, event.getUserIdLong());
    }
  }

  /**
   * Reads a {@code long} from an environment variable, returning {@code def} on parse failure.
   */
  private static long readLongEnv(String name, long def) {
    try {
      return Long.parseLong(System.getenv(name));
    } catch (Exception e) {
      return def;
    }
  }

  private static int readIntEnv(String name, int def) {
    try {
      return Integer.parseInt(System.getenv(name));
    } catch (Exception e) {
      return def;
    }
  }
}

