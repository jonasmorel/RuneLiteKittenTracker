// File: KittenTrackerOverlay.java
// This file is responsible for drawing the information box on the screen.
// It gets the timer data from the main plugin file and displays it.

package com.KittenTracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class KittenTrackerOverlay extends OverlayPanel
{
    private final KittenTrackerPlugin plugin;
    private final KittenTrackerConfig config;

    @Inject
    private KittenTrackerOverlay(KittenTrackerPlugin plugin, KittenTrackerConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * This is the main method that draws the overlay. It's called every frame.
     */
    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isKittenFollowing())
        {
            return null;
        }

        setPosition(config.getOverlayPosition());

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Kitten Tracker")
                .color(Color.WHITE)
                .build());

        addTimerLine("Growth", plugin.getGrowthTimeRemaining(), KittenTrackerPlugin.TOTAL_GROWTH_TIME);
        addTimerLine("Hunger", plugin.getHungerTimeRemaining(), KittenTrackerPlugin.HUNGER_TIME);
        addTimerLine("Attention", plugin.getAttentionTimeRemaining(), plugin.getCurrentAttentionDuration());

        return super.render(graphics);
    }

    /**
     * Helper method to add a line to the overlay panel for a specific timer.
     * @param label The text label for the timer (e.g., "Growth").
     * @param remaining The duration of time remaining.
     * @param total The total duration for this timer.
     */
    private void addTimerLine(String label, Duration remaining, Duration total)
    {
        String value;
        Color valueColor;

        if (remaining != null)
        {
            long seconds = remaining.getSeconds();
            value = String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);

            if (label.equals("Growth"))
            {
                valueColor = config.getGrowthColor();
            }
            else
            {
                valueColor = getTimerColor(remaining, total);
            }
        }
        else
        {
            valueColor = Color.LIGHT_GRAY;
            switch (label)
            {
                case "Growth":
                    value = "Check age";
                    break;
                case "Hunger":
                    value = "Feed kitten";
                    break;
                case "Attention":
                    value = "Pet kitten";
                    break;
                default:
                    value = "N/A";
                    break;
            }
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left(label + ":")
                .right(value)
                .rightColor(valueColor)
                .build());
    }

    /**
     * Determines the color for the timer based on how much time is left.
     * @param remaining The duration of time remaining.
     * @param total The total duration for this timer.
     */
    private Color getTimerColor(Duration remaining, Duration total)
    {
        if (remaining == null || total == null || total.isZero())
        {
            return Color.WHITE;
        }

        double percentage = (double) remaining.toMillis() / total.toMillis();
        if (percentage <= 0.15)
        {
            return config.getDangerColor();
        }
        if (percentage <= 0.50)
        {
            return config.getWarningColor();
        }
        return config.getNormalColor();
    }
}