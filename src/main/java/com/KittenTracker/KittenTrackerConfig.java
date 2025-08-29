// File: KittenTrackerConfig.java
// This file defines the settings panel for our plugin.
// Users can customize colors and other options here.

package com.KittenTracker;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.ui.overlay.OverlayPosition;

@ConfigGroup("kittentracker")
public interface KittenTrackerConfig extends Config
{
    @ConfigItem(
            keyName = "normalColor",
            name = "Normal Color",
            description = "The color of the timer when the kitten is content.",
            position = 1
    )
    default Color getNormalColor()
    {
        return Color.GREEN;
    }

    @ConfigItem(
            keyName = "warningColor",
            name = "Warning Color",
            description = "The color of the timer when the kitten needs attention soon.",
            position = 2
    )
    default Color getWarningColor()
    {
        return Color.YELLOW;
    }

    @ConfigItem(
            keyName = "dangerColor",
            name = "Danger Color",
            description = "The color of the timer when the kitten is about to run away.",
            position = 3
    )
    default Color getDangerColor()
    {
        return Color.RED;
    }

    @ConfigItem(
            keyName = "growthColor",
            name = "Growth Timer Color",
            description = "The color of the growth timer.",
            position = 4
    )
    default Color getGrowthColor()
    {
        return Color.WHITE;
    }

    @ConfigItem(
            keyName = "overlayPosition",
            name = "Overlay Position",
            description = "The position of the kitten tracker overlay.",
            position = 5
    )
    default OverlayPosition getOverlayPosition()
    {
        return OverlayPosition.TOP_LEFT; // Default position
    }

    @ConfigItem(
            keyName = "notifyOnHunger",
            name = "Notify when hungry",
            description = "Send a desktop notification when your kitten is very hungry.",
            position = 6
    )
    default boolean notifyOnHunger()
    {
        return true;
    }

    @ConfigItem(
            keyName = "notifyOnAttention",
            name = "Notify for attention",
            description = "Send a desktop notification when your kitten is lonely.",
            position = 7
    )
    default boolean notifyOnAttention()
    {
        return true;
    }

    @ConfigItem(
            keyName = "resetTimersButton",
            name = "Reset Timers",
            description = "Click the checkbox to reset all saved kitten timers. Use this when you get a new kitten.",
            position = 8
    )
    default boolean resetTimersButton()
    {
        return false;
    }
}