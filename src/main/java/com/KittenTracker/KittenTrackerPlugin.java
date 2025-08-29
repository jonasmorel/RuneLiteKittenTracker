// File: KittenTrackerPlugin.java
// This is the main file for the plugin. It handles all the logic,
// listens for game events, and manages the timers.

package com.KittenTracker;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
        name = "Kitten Tracker",
        description = "Tracks the growth, hunger, and attention of a pet kitten.",
        tags = {"pet", "cat", "kitten", "tracker", "timer"}
)
public class KittenTrackerPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(KittenTrackerPlugin.class);

    private static final String CONFIG_GROUP = "kittentracker";
    private static final String REMAINING_HUNGER_KEY = "remainingHunger";
    private static final String REMAINING_ATTENTION_KEY = "remainingAttention";
    private static final String SYNCED_GROWTH_KEY = "syncedGrowthDuration";
    private static final String CURRENT_ATTENTION_KEY = "currentAttentionDuration";


    public static final Duration TOTAL_GROWTH_TIME = Duration.ofHours(3);
    public static final Duration HUNGER_TIME = Duration.ofMinutes(30);
    public static final Duration ATTENTION_TIME_STROKE = Duration.ofMinutes(39);
    public static final Duration ATTENTION_TIME_WOOL = Duration.ofMinutes(65);

    private static final Duration HUNGER_NOTIFICATION_THRESHOLD = Duration.ofMinutes(3);
    private static final Duration ATTENTION_NOTIFICATION_THRESHOLD = Duration.ofMinutes(7);


    private static final Set<Integer> KITTEN_IDS = new HashSet<>(Arrays.asList(
            5591, 5592, 5593, 5594, 5595, 5596, // Standard kittens
            7351, 7352, 7353, 7354, 7355, 7356  // Hellkittens
    ));

    // MODIFIED: Simplified the regex pattern to remove unnecessary non-capturing groups.
    private static final Pattern GUESS_AGE_PATTERN = Pattern.compile("approximate time until fully adult: (?:(\\d+) hours? )?(\\d+) minutes?");

    @Inject
    private Client client;

    @Inject
    private KittenTrackerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KittenTrackerOverlay overlay;

    @Inject
    private ConfigManager configManager;

    @Inject
    private Notifier notifier;

    private Instant lastFedTime;
    private Instant lastAttentionTime;
    private Instant growthSyncTime;
    private Duration syncedGrowthDuration;

    private Duration currentAttentionDuration;
    private boolean kittenFollowing = false;

    private ScheduledExecutorService executor;

    private Duration growthTimeRemaining;
    private Duration hungerTimeRemaining;
    private Duration attentionTimeRemaining;

    private boolean hungerNotificationSent = false;
    private boolean attentionNotificationSent = false;


    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::updateTimersForOverlay, 100, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void shutDown()
    {
        if (kittenFollowing)
        {
            saveTimers();
        }
        overlayManager.remove(overlay);
        executor.shutdownNow();
    }

    @Subscribe
    public void onGameTick(@SuppressWarnings("unused") GameTick tick)
    {
        boolean foundKitten = false;
        if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
        {
            for (NPC npc : client.getNpcs())
            {
                if (KITTEN_IDS.contains(npc.getId()) && npc.getInteracting() == client.getLocalPlayer())
                {
                    foundKitten = true;
                    break;
                }
            }
        }

        if (foundKitten && !kittenFollowing)
        {
            loadTimers();
        }
        else if (!foundKitten && kittenFollowing)
        {
            saveTimers();
        }
        kittenFollowing = foundKitten;
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        ChatMessageType type = chatMessage.getType();
        if (type != ChatMessageType.GAMEMESSAGE &&
                type != ChatMessageType.SPAM &&
                type != ChatMessageType.DIALOG &&
                type != ChatMessageType.MESBOX)
        {
            return;
        }

        String message = chatMessage.getMessage();
        log.trace("ChatMessage received: Type='{}', Message='{}'", type, message);

        if (message.contains("The kitten gratefully laps up the milk.") || message.contains("The kitten gobbles up the fish."))
        {
            log.debug("Kitten fed. Resetting hunger timer.");
            lastFedTime = Instant.now();
            hungerNotificationSent = false;
        }
        else if (message.contains("You softly stroke your cat."))
        {
            log.debug("Kitten petted. Resetting attention timer to stroke duration.");
            lastAttentionTime = Instant.now();
            currentAttentionDuration = ATTENTION_TIME_STROKE;
            attentionNotificationSent = false;
        }
        else if (message.contains("You play with the kitten"))
        {
            log.debug("Played with kitten using wool. Resetting attention timer to wool duration.");
            lastAttentionTime = Instant.now();
            currentAttentionDuration = ATTENTION_TIME_WOOL;
            attentionNotificationSent = false;
        }
        else
        {
            Matcher ageMatcher = GUESS_AGE_PATTERN.matcher(message);
            if (ageMatcher.find())
            {
                try
                {
                    String hourGroup = ageMatcher.group(1);
                    int hoursLeft = (hourGroup != null) ? Integer.parseInt(hourGroup) : 0;
                    int minutesLeft = Integer.parseInt(ageMatcher.group(2));

                    Duration timeLeft = Duration.ofHours(hoursLeft).plusMinutes(minutesLeft);
                    syncedGrowthDuration = TOTAL_GROWTH_TIME.minus(timeLeft);
                    growthSyncTime = Instant.now();
                    log.debug("Synced growth timer. Time left: {}h {}m", hoursLeft, minutesLeft);
                }
                catch (NumberFormatException e)
                {
                    log.error("Failed to parse kitten age from message: {}", message, e);
                }
            }
        }
        updateTimersForOverlay();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
        {
            if (kittenFollowing)
            {
                saveTimers();
            }
            kittenFollowing = false;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (event.getGroup().equals(CONFIG_GROUP) && event.getKey().equals("resetTimersButton"))
        {
            if (Boolean.parseBoolean(event.getNewValue()))
            {
                log.debug("Reset Timers button clicked. Wiping saved timers.");
                resetTimers();
                configManager.setConfiguration(CONFIG_GROUP, "resetTimersButton", false);
            }
        }
    }

    private void updateTimersForOverlay()
    {
        if (!kittenFollowing)
        {
            growthTimeRemaining = null;
            hungerTimeRemaining = null;
            attentionTimeRemaining = null;
            return;
        }
        growthTimeRemaining = calculateGrowthTimeRemaining();
        hungerTimeRemaining = calculateTimeRemaining(lastFedTime, HUNGER_TIME);
        attentionTimeRemaining = calculateTimeRemaining(lastAttentionTime, currentAttentionDuration);

        checkNotifications();
    }

    private void checkNotifications()
    {
        if (config.notifyOnHunger() && !hungerNotificationSent && hungerTimeRemaining != null &&
                hungerTimeRemaining.compareTo(HUNGER_NOTIFICATION_THRESHOLD) <= 0)
        {
            notifier.notify("Your kitten is very hungry!");
            hungerNotificationSent = true;
        }

        if (config.notifyOnAttention() && !attentionNotificationSent && attentionTimeRemaining != null &&
                attentionTimeRemaining.compareTo(ATTENTION_NOTIFICATION_THRESHOLD) <= 0)
        {
            notifier.notify("Your kitten is feeling lonely!");
            attentionNotificationSent = true;
        }
    }

    private Duration calculateGrowthTimeRemaining()
    {
        if (growthSyncTime == null || syncedGrowthDuration == null)
        {
            return null;
        }
        Duration elapsedSinceSync = Duration.between(growthSyncTime, Instant.now());
        Duration currentGrowth = syncedGrowthDuration.plus(elapsedSinceSync);

        if (currentGrowth.compareTo(TOTAL_GROWTH_TIME) >= 0)
        {
            return Duration.ZERO;
        }
        return TOTAL_GROWTH_TIME.minus(currentGrowth);
    }

    private Duration calculateTimeRemaining(Instant lastActionTime, Duration totalDuration)
    {
        if (lastActionTime == null || totalDuration == null)
        {
            return null;
        }
        Duration elapsed = Duration.between(lastActionTime, Instant.now());
        if (elapsed.compareTo(totalDuration) >= 0)
        {
            return Duration.ZERO;
        }
        return totalDuration.minus(elapsed);
    }

    private void saveTimers()
    {
        Duration hungerRemaining = calculateTimeRemaining(lastFedTime, HUNGER_TIME);
        if (hungerRemaining != null)
        {
            configManager.setConfiguration(CONFIG_GROUP, REMAINING_HUNGER_KEY, hungerRemaining.toMillis());
        }

        Duration attentionRemaining = calculateTimeRemaining(lastAttentionTime, currentAttentionDuration);
        if (attentionRemaining != null)
        {
            configManager.setConfiguration(CONFIG_GROUP, REMAINING_ATTENTION_KEY, attentionRemaining.toMillis());
        }

        if (growthSyncTime != null && syncedGrowthDuration != null)
        {
            Duration elapsedSinceSync = Duration.between(growthSyncTime, Instant.now());
            Duration newTotalGrowth = syncedGrowthDuration.plus(elapsedSinceSync);
            configManager.setConfiguration(CONFIG_GROUP, SYNCED_GROWTH_KEY, newTotalGrowth.toMillis());
        }

        if (currentAttentionDuration != null)
        {
            configManager.setConfiguration(CONFIG_GROUP, CURRENT_ATTENTION_KEY, currentAttentionDuration.toMillis());
        }

        log.debug("Kitten timers (paused state) saved.");
    }

    private void loadTimers()
    {
        Long hungerRemainingMillis = configManager.getConfiguration(CONFIG_GROUP, REMAINING_HUNGER_KEY, Long.class);
        if (hungerRemainingMillis != null)
        {
            Duration hungerRemaining = Duration.ofMillis(hungerRemainingMillis);
            lastFedTime = Instant.now().minus(HUNGER_TIME.minus(hungerRemaining));
            hungerNotificationSent = hungerRemaining.compareTo(HUNGER_NOTIFICATION_THRESHOLD) <= 0;
        }

        Long attentionRemainingMillis = configManager.getConfiguration(CONFIG_GROUP, REMAINING_ATTENTION_KEY, Long.class);
        Long currentAttentionMillis = configManager.getConfiguration(CONFIG_GROUP, CURRENT_ATTENTION_KEY, Long.class);
        if (attentionRemainingMillis != null && currentAttentionMillis != null)
        {
            Duration attentionRemaining = Duration.ofMillis(attentionRemainingMillis);
            currentAttentionDuration = Duration.ofMillis(currentAttentionMillis);
            lastAttentionTime = Instant.now().minus(currentAttentionDuration.minus(attentionRemaining));
            attentionNotificationSent = attentionRemaining.compareTo(ATTENTION_NOTIFICATION_THRESHOLD) <= 0;
        }

        Long syncedGrowthMillis = configManager.getConfiguration(CONFIG_GROUP, SYNCED_GROWTH_KEY, Long.class);
        if (syncedGrowthMillis != null)
        {
            syncedGrowthDuration = Duration.ofMillis(syncedGrowthMillis);
            growthSyncTime = Instant.now();
        }
        log.debug("Kitten timers (resumed state) loaded.");
        updateTimersForOverlay();
    }

    private void resetTimers()
    {
        lastFedTime = null;
        lastAttentionTime = null;
        growthSyncTime = null;
        syncedGrowthDuration = null;
        currentAttentionDuration = null;
        hungerNotificationSent = false;
        attentionNotificationSent = false;

        configManager.unsetConfiguration(CONFIG_GROUP, REMAINING_HUNGER_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, REMAINING_ATTENTION_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, SYNCED_GROWTH_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, CURRENT_ATTENTION_KEY);

        log.debug("All kitten timers have been reset.");
        updateTimersForOverlay();
    }

    public boolean isKittenFollowing()
    {
        return kittenFollowing;
    }

    public Duration getGrowthTimeRemaining()
    {
        return growthTimeRemaining;
    }

    public Duration getHungerTimeRemaining()
    {
        return hungerTimeRemaining;
    }

    public Duration getAttentionTimeRemaining()
    {
        return attentionTimeRemaining;
    }

    public Duration getCurrentAttentionDuration()
    {
        return currentAttentionDuration;
    }


    @Provides
    KittenTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KittenTrackerConfig.class);
    }
}