package com.pinewoodbuilders.onwatch.onwatchlog;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public class OnWatchActionEvent extends Event {

    private final OnWatchAction action;
    private final int caseId;

    /**
     * Creates a new modlog action event.
     *
     * @param api    The JDA api(shard) that the event should be triggered on.
     * @param action The modlog action that is happening.
     * @param caseId The case ID for the given modlog event.
     */
    public OnWatchActionEvent(JDA api, OnWatchAction action, int caseId) {
        super(api);

        this.action = action;
        this.caseId = caseId;
    }

    /**
     * Gets the target for the modlog action.
     *
     * @return The target for the modlog action.
     */
    @Nullable
    public User getTarget() {
        return action.getTarget();
    }

    /**
     * Gets the stringified version of the target, containing the users
     * username and discriminator, as well as a mention in brackets.
     *
     * @return The stringified version of the target.
     */
    @Nullable
    public String getTargetStringified() {
        return action.getStringifiedTarget();
    }

    /**
     * Gets the moderator for the modlog action.
     *
     * @return The moderator for the modlog action.
     */
    public User getModerator() {
        return action.getModerator();
    }

    /**
     * Gets the stringified version of the moderator, containing the users
     * username and discriminator, as well as a mention in brackets.
     *
     * @return The stringified version of the moderator.
     */
    public String getModeratorStringified() {
        return action.getStringifiedModerator();
    }

    /**
     * Gets the reason for the modlog action.
     *
     * @return The reason for the modlog action.
     */
    @Nullable
    public String getReason() {
        return action.getMessage();
    }

    /**
     * Gets the type of modlog action that is being preformed.
     *
     * @return The type of modlog action that is being preformed.
     */
    public OnWatchType getType() {
        return action.getType();
    }

    /**
     * Gets the case ID for the modlog action, the case ID can be used later
     * by moderators to change the reason for a given modlog action.
     *
     * @return The case ID for the modlog action.
     */
    public int getCaseId() {
        return caseId;
    }
}

