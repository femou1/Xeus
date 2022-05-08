/*
 * Copyright (c) 2018.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders;

import java.io.File;

@SuppressWarnings("WeakerAccess")
public class Constants {

    public static final File STORAGE_PATH = new File("storage");

    // Database Tables
    public static final String GUILD_TABLE_NAME = "guilds";
    public static final String GUILD_TYPES_TABLE_NAME = "guild_types";
    public static final String STATISTICS_TABLE_NAME = "statistics";
    public static final String BLACKLIST_TABLE_NAME = "blacklists";
    public static final String PLAYER_EXPERIENCE_TABLE_NAME = "experiences";
    public static final String BOT_VOTES_TABLE_NAME = "votes";
    public static final String FEEDBACK_TABLE_NAME = "feedback";
    public static final String SHARDS_TABLE_NAME = "shards";
    public static final String LOG_TABLE_NAME = "logs";
    public static final String LOG_TYPES_TABLE_NAME = "log_types";
    public static final String REACTION_ROLES_TABLE_NAME = "reaction_roles";
    public static final String PURCHASES_TABLE_NAME = "purchases";
    public static final String MUTE_TABLE_NAME = "mutes";
    public static final String BAN_TABLE_NAME = "bans";
    public static final String INSTALLED_PLUGINS_TABLE_NAME = "installed_plugins";

    public static final String REMINDERS_TABLE_NAME = "reminders";

    // Global Update Specific Tables
    public static final String GROUP_MODERATORS_TABLE = "group_moderators";
    public static final String GUILD_SETTINGS_TABLE = "guild_settings";
    public static final String VERIFICATION_SETTINGS_TABLE_NAME = "verification_settings";
    public static final String VERIFICATION_DATABASE_TABLE_NAME = "verification_database";
    public static final String ROLE_PERSISTENCE_TABLE_NAME = "role_persistence";
    public static final String MGM_LOG_TABLE_NAME = "mgm_logs";
    public static final String MGM_LOG_TYPES_TABLE_NAME = "mgm_logs_types";
    public static final String LINK_FILTER_TABLE_NAME = "link_filter";
    public static final String GLOBAL_MUTE_TABLE_NAME = "global_mutes";
    public static final String GLOBAL_WATCH_TABLE_NAME = "global_watch";
    public static final String GLOBAL_SETTINGS_TABLE = "global_settings";
    public static final String WARNINGS_TABLE_NAME = "warns";

    // Pinewood Specific Tables
    public static final String EVALS_DATABASE_TABLE_NAME = "pinewood_evaluations";
    public static final String EVALS_LOG_DATABASE_TABLE_NAME = "pinewood_evaluations_log";
    public static final String PENDING_QUIZ_TABLE_NAME = "pinewood_pending_quiz";

    public static final String REWARD_REQUESTS_TABLE_NAME = "reward_requests";

    public static final String VOTE_TABLE_NAME = "xeus_vote";
    public static final String VOTES_TABLE_NAME = "xeus_votes";
    public static final String VOTABLE_TABLE_NAME = "xeus_votable";

    public static final String PB_SUGGESTIONS_TABLE_NAME = "suggestions";
    public static final String REPORTS_DATABASE_TABLE_NAME = "handbook_reports";
    public static final String REMITTANCE_DATABASE_TABLE_NAME = "patrol_remittance";
    
    public static final String ON_WATCH_TABLE_NAME = "on_watch";
    public static final String ON_WATCH_LOG_TABLE_NAME = "on_watch_logs";
    public static final String ON_WATCH_TYPES_TABLE_NAME = "on_watch_types";

    public static final String ANTI_UNBAN_TABLE_NAME = "pia_antiban";
    public static final String FEATURE_BLACKLIST_TABLE_NAME = "feature_blacklist";



    // Package Specific Information
    public static final String PACKAGE_MIGRATION_PATH = "com.pinewoodbuilders.database.migrate";
    public static final String PACKAGE_SEEDER_PATH = "com.pinewoodbuilders.database.seeder";
    public static final String PACKAGE_COMMAND_PATH = "com.pinewoodbuilders.commands";
    public static final String PACKAGE_INTENTS_PATH = "com.pinewoodbuilders.ai.dialogflow.intents";
    public static final String PACKAGE_JOB_PATH = "com.pinewoodbuilders.scheduler";

    // Emojis
    public static final String EMOTE_ONLINE = "<:green_circle:679666667672174592>";
    public static final String EMOTE_AWAY = "<:yellow_circle:679666871368417290>";
    public static final String EMOTE_DND = "<:red_circle:6796668916982088550>";

    // Purchase Types
    public static final String RANK_BACKGROUND_PURCHASE_TYPE = "rank-background";

    // Audio Metadata
    public static final String AUDIO_HAS_SENT_NOW_PLAYING_METADATA = "has-sent-now-playing";

    // Command source link
    public static final String SOURCE_URI = "https://gitlab.com/pinewood-builders/discord/xeus/-/blob/master/src/main/java/com/avairebot/commands/%s/%s.java";

    public static final String PIA_LOG_CHANNEL = "788316320747094046";

}
