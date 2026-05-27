package Botcode.AI.AIUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks multi-user conversations in channels and provides contextual insight for bot
 * participation.
 *
 * <p>Analyzes message patterns, user interactions, and conversation flow to determine when the bot
 * should naturally inject feedback, suggestions, or join ongoing discussions.
 */
public class ChannelConversationService {

  private static final int MAX_RECENT_MESSAGES_PER_CHANNEL = 50;
  private static final int MIN_UNIQUE_USERS_FOR_CONVERSATION = 2;
  private static final long CONVERSATION_WINDOW_SECONDS = 5 * 60L; // 5 minute window
  private static final int MIN_MESSAGE_COUNT_FOR_CONTEXT = 3;
  private static final int INTERJECT_PROBABILITY_BASE = 25; // 25% base chance

  /**
   * Tracks recent messages per channel: channelId -> [MessageRecord, ...]
   */
  private static final Map<Long, Deque<MessageRecord>> recentMessagesByChannel =
      new ConcurrentHashMap<>();

  /**
   * Tracks unique users per channel in real-time.
   */
  private static final Map<Long, Set<Long>> uniqueUsersByChannel = new ConcurrentHashMap<>();

  /**
   * Represents a single message in a conversation thread.
   */
  public static class MessageRecord {
    public long timestamp;
    public long userId;
    public String username;
    public String content;
    public String topic;

    public MessageRecord(long userId, String username, String content, String topic) {
      this.timestamp = System.currentTimeMillis() / 1000;
      this.userId = userId;
      this.username = username;
      this.content = content;
      this.topic = topic;
    }
  }

  /**
   * Represents a summary of an active multi-user conversation.
   */
  public static class ConversationContext {
    public long channelId;
    public Set<Long> participantUserIds;
    public Set<String> topicsDiscussed;
    public int messageCount;
    public long lastMessageTime;
    public String dominantTopic;
    public List<String> recentMessages;
    public String sentiment; // "positive", "neutral", "question", "problem"

    public ConversationContext(long channelId) {
      this.channelId = channelId;
      this.participantUserIds = new HashSet<>();
      this.topicsDiscussed = new HashSet<>();
      this.recentMessages = new ArrayList<>();
    }
  }

  /**
   * Records a user message in a channel conversation.
   */
  public static void recordChannelMessage(
      long channelId, long userId, String username, String content, String detectedTopic) {
    Deque<MessageRecord> messages =
        recentMessagesByChannel.computeIfAbsent(
            channelId,
            k -> new LinkedList<>());

    messages.addLast(new MessageRecord(userId, username, content, detectedTopic));
    while (messages.size() > MAX_RECENT_MESSAGES_PER_CHANNEL) {
      messages.removeFirst();
    }

    Set<Long> users =
        uniqueUsersByChannel.computeIfAbsent(
            channelId, k -> new HashSet<>());
    users.add(userId);
  }

  /**
   * Determines if the bot should interject in a channel conversation.
   *
   * <p>Considers:
   * - Number of active participants (2+)
   * - Recent message frequency
   * - Presence of questions or problems
   * - Time since last bot response
   *
   * @return true if bot should consider joining the conversation
   */
  public static boolean shouldBotInterjact(long channelId) {
    Deque<MessageRecord> messages =
        recentMessagesByChannel.getOrDefault(channelId, new LinkedList<>());

    if (messages.size() < MIN_MESSAGE_COUNT_FOR_CONTEXT) {
      return false;
    }

    Set<Long> participants = uniqueUsersByChannel.getOrDefault(channelId, new HashSet<>());
    if (participants.size() < MIN_UNIQUE_USERS_FOR_CONVERSATION) {
      return false;
    }

    // Check if conversation is recent and active
    MessageRecord lastMsg = messages.peekLast();
    if (lastMsg == null) {
      return false;
    }

    long timeSinceLastMsg = (System.currentTimeMillis() / 1000) - lastMsg.timestamp;
    if (timeSinceLastMsg > CONVERSATION_WINDOW_SECONDS) {
      return false;
    }

    // Check if any recent message contains question/problem markers
    boolean hasQuestionOrProblem =
        messages.stream()
            .anyMatch(
                msg ->
                    msg.content.toLowerCase().contains("?")
                        || msg.content.toLowerCase().contains("help")
                        || msg.content.toLowerCase().contains("how")
                        || msg.content.toLowerCase().contains("why")
                        || msg.content.toLowerCase().contains("problem")
                        || msg.content.toLowerCase().contains("broken")
                        || msg.content.toLowerCase().contains("stuck"));

    if (!hasQuestionOrProblem) {
      // Only 20% chance to interject in casual conversation
      return Math.random() * 100 < (INTERJECT_PROBABILITY_BASE / 2);
    }

    // Higher probability for questions/problems
    return Math.random() * 100 < INTERJECT_PROBABILITY_BASE;
  }

  /**
   * Analyzes the current channel conversation and returns context the bot can use.
   */
  public static ConversationContext analyzeChannelConversation(long channelId) {
    Deque<MessageRecord> messages =
        recentMessagesByChannel.getOrDefault(channelId, new LinkedList<>());
    Set<Long> participants = uniqueUsersByChannel.getOrDefault(channelId, new HashSet<>());

    ConversationContext context = new ConversationContext(channelId);
    context.participantUserIds = new HashSet<>(participants);
    context.messageCount = messages.size();

    if (!messages.isEmpty()) {
      MessageRecord lastMsg = messages.peekLast();
      context.lastMessageTime = lastMsg.timestamp;

      // Extract topics
      messages.forEach(msg -> {
        if (msg.topic != null && !msg.topic.isBlank()) {
          context.topicsDiscussed.add(msg.topic);
        }
        context.recentMessages.add(msg.username + ": " + msg.content);
      });

      // Determine dominant topic
      if (!context.topicsDiscussed.isEmpty()) {
        context.dominantTopic =
            context.topicsDiscussed.stream()
                .findFirst()
                .orElse("general");
      } else {
        context.dominantTopic = "general";
      }

      // Detect sentiment
      String lastMsgLower = lastMsg.content.toLowerCase();
      if (lastMsgLower.contains("?") || lastMsgLower.contains("help") ||
          lastMsgLower.contains("confused")) {
        context.sentiment = "question";
      } else if (lastMsgLower.contains("problem") || lastMsgLower.contains("broken") ||
          lastMsgLower.contains("error") || lastMsgLower.contains("stuck")) {
        context.sentiment = "problem";
      } else if (lastMsgLower.contains("!") || lastMsgLower.contains("awesome") ||
          lastMsgLower.contains("great")) {
        context.sentiment = "positive";
      } else {
        context.sentiment = "neutral";
      }
    }

    return context;
  }

  /**
   * Gets the last few messages in a channel for context.
   */
  public static List<String> getRecentConversationSnippet(long channelId, int limit) {
    Deque<MessageRecord> messages =
        recentMessagesByChannel.getOrDefault(channelId, new LinkedList<>());

    List<String> snippet = new ArrayList<>();
    messages.stream()
        .skip(Math.max(0, messages.size() - limit))
        .forEach(msg -> snippet.add(msg.username + ": " + msg.content));

    return snippet;
  }

  /**
   * Clears old channel data to prevent memory leaks.
   */
  public static void pruneOldConversations() {
    long now = System.currentTimeMillis() / 1000;
    recentMessagesByChannel.forEach(
        (channelId, messages) -> {
          while (!messages.isEmpty() && (now - messages.peekFirst().timestamp) >
              (CONVERSATION_WINDOW_SECONDS * 2)) {
            messages.removeFirst();
          }
        });

    // Remove empty channels
    recentMessagesByChannel.keySet().removeIf(ch -> recentMessagesByChannel.get(ch).isEmpty());
    uniqueUsersByChannel.keySet().removeIf(ch -> uniqueUsersByChannel.get(ch).isEmpty());
  }

  /**
   * Gets all unique conversation participants in a channel.
   */
  public static Set<Long> getChannelParticipants(long channelId) {
    return new HashSet<>(uniqueUsersByChannel.getOrDefault(channelId, new HashSet<>()));
  }

  /**
   * Clears conversation history for a channel (useful for channel archival).
   */
  public static void clearChannelConversation(long channelId) {
    recentMessagesByChannel.remove(channelId);
    uniqueUsersByChannel.remove(channelId);
  }
}



