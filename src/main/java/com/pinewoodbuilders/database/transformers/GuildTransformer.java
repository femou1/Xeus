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

package com.pinewoodbuilders.database.transformers;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.database.transformers.Transformer;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.*;

public class GuildTransformer extends Transformer {

    private static final GuildTypeTransformer partnerTypeTransformer = new PartnerGuildTypeTransformer();

    private final Map <String, String> aliases = new HashMap <>();
    private final Map <String, String> prefixes = new HashMap <>();
    private final Map <String, String> selfAssignableRoles = new HashMap <>();
    private final Map <Integer, String> levelRoles = new HashMap <>();
    private final Map <String, Map <String, String>> modules = new HashMap <>();

    private final List <ChannelTransformer> channels = new ArrayList <>();
    private final List <String> badWordsExact = new ArrayList <>();
    private final List <String> badWordsWildcard = new ArrayList <>();

    private final List <String> piaWordsExact = new ArrayList <>();
    private final List <String> piaWordsWildcard = new ArrayList <>();

    private final List <String> reportPermissionRoles = new ArrayList <>();
    private final List <String> evalQuestions = new ArrayList <>();

    private final Set <Long> levelExemptChannels = new HashSet <>();
    private final Set <Long> levelExemptRoles = new HashSet <>();

    private final Set <Long> lockableChannels = new HashSet <>();
    private final Set <Long> lockableChannelsRoles = new HashSet <>();

    private final Set <Long> moderatorRoles = new HashSet <>();
    private final Set <Long> managerRoles = new HashSet <>();
    private final Set <Long> administratorRoles = new HashSet <>();
    private final Set <Long> noLinksRoles = new HashSet <>();
    private final Set <Long> groupShoutRoles = new HashSet<>();

    private final GuildTypeTransformer guildType;
    private boolean partner;

    private String id;
    private String name;
    private String nameRaw;
    private String locale;
    private String levelChannel = null;
    private boolean levels = false;
    private boolean levelAlerts = false;
    private boolean levelHierarchy = false;
    private boolean filter = false;

    private String autorole = null;
    private String modlog = null;
    private String muteRole = null;

    //Pinewood specific
    private String mainDiscordRole = null;
    private String onWatchRole = null;
    private String onWatch = null;
    private String gamenightRole = null;
    private String filterLog = null;
    private String memberToYoungChannelId = null;
    private String pinewoodEventRequestsChannelId = null;

    private String suggestionChannel = null;
    private String suggestionCommunityChannel = null;
    private String suggestionEmoteId = null;
    private String suggestionApprovedChannelId = null;

    private String reportEmoteId = null;
    private String handbookReportChannel = null;
    private String reportInfoMessage = null;
    private String voteValidationChannel = null;

    private String patrolRemittanceChannel = null;
    private String patrolRemittanceEmoteId = null;
    private String patrolRemittanceMessage = null;


    private int modlogCase = 0;
    private int onWatchCase = 0;
    private double levelModifier = -1;
    private long reportCategory = 0;
    private long audit_log = 0;
    private long evalAnswerChannel = 0;
    private long join_logs = 0;

    private int robloxGroupId = 0;
    private int minimalHrRank = 0;
    private int minimumLeadRank = 0;

    private int characterSpam = 0;
    private int emojiSpam = 0;
    private int imageSpam = 0;
    private int linkSpam = 0;
    private int massMentionSpam = 0;
    private int messageSpam = 0;


    public GuildTransformer(Guild guild) {
        super(null);

        locale = null;
        id = guild.getId();
        name = guild.getName();
        nameRaw = guild.getName();

        partner = getVipStatus(guild.getVoiceChannels());
        guildType = partner ? partnerTypeTransformer : new GuildTypeTransformer(data);
    }

    private boolean getVipStatus(List<VoiceChannel> voiceChannels) {
        for (VoiceChannel vc : voiceChannels) {
            if (!vc.getRegion().isVip()) {
                continue;
            }

            return vc.getRegion().isVip();
        }
        return false;
    }

    public GuildTransformer(Guild guild, DataRow data) {
        super(data);

        partner = guild.getRegion().isVip();

        if (hasData()) {
            id = data.getString("id");
            name = data.getString("name");
            nameRaw = data.get("name").toString();
            locale = data.getString("local");

            levels = data.getBoolean("levels");
            levelAlerts = data.getBoolean("level_alerts");
            levelHierarchy = data.getBoolean("hierarchy");
            levelChannel = data.getString("level_channel");
            levelModifier = data.getDouble("level_modifier", -1);
            autorole = data.getString("autorole");
            modlog = data.getString("modlog");
            muteRole = data.getString("mute_role");
            gamenightRole = data.getString("gamenight_role");
            modlogCase = data.getInt("modlog_case");
            characterSpam = data.getInt("automod_character_spam");
            emojiSpam = data.getInt("automod_emoji_spam");
            imageSpam = data.getInt("automod_image_spam");
            linkSpam = data.getInt("automod_link_spam");
            massMentionSpam = data.getInt("automod_mass_mention");
            messageSpam = data.getInt("automod_message_spam");

            mainDiscordRole = data.getString("main_discord_role");

            reportCategory = data.getLong("report_discord_category");
            voteValidationChannel = data.getString("vote_validation_channel");

            audit_log = data.getLong("audit_log");
            join_logs = data.getLong("join_logs");

            suggestionChannel = data.getString("suggestion_channel");
            suggestionCommunityChannel = data.getString("suggestion_community_channel");
            suggestionEmoteId = data.getString("suggestion_emote_id");
            suggestionApprovedChannelId = data.getString("approved_suggestion_channel");

            reportEmoteId = data.getString("report_emote_id");
            handbookReportChannel = data.getString("handbook_report_channel");
            reportInfoMessage = data.getString("report_info_message");

            robloxGroupId = data.getInt("roblox_group_id");
            minimalHrRank = data.getInt("minimum_hr_rank");
            minimumLeadRank = data.getInt("minimum_lead_rank");
            evalAnswerChannel = data.getLong("evaluation_answer_channel");

            patrolRemittanceChannel = data.getString("patrol_remittance_channel");
            patrolRemittanceEmoteId = data.getString("patrol_remittance_emote_id");
            patrolRemittanceMessage = data.getString("patrol_remittance_message");

            onWatchCase = data.getInt("on_watch_case");
            onWatchRole = data.getString("on_watch_role");
            onWatch = data.getString("on_watch");

            filter = data.getBoolean("filter");
            filterLog = data.getString("filter_log");

            memberToYoungChannelId = data.getString("member_to_young_channel_id");

            pinewoodEventRequestsChannelId = data.getString("event_request_channel");

            // Sets the discord partner value if the guild isn't already a Discord partner.
            if (!partner) {
                partner = data.getBoolean("partner", false);
            }

            if (data.getString("aliases", null) != null) {
                HashMap <String, String> dbAliases = Xeus.gson.fromJson(
                    data.getString("aliases"),
                    new TypeToken <HashMap <String, String>>() {
                    }.getType());

                for (Map.Entry <String, String> item : dbAliases.entrySet()) {
                    aliases.put(item.getKey().toLowerCase(), item.getValue());
                }
            }

            if (data.getString("filter_exact", null) != null) {
                ArrayList <String> dbFilter = Xeus.gson.fromJson(
                    data.getString("filter_exact"),
                    new TypeToken <ArrayList <String>>() {
                    }.getType());

                badWordsExact.addAll(dbFilter);
            }
            if (data.getString("filter_wildcard", null) != null) {
                ArrayList <String> dbFilter = Xeus.gson.fromJson(
                    data.getString("filter_wildcard"),
                    new TypeToken <ArrayList <String>>() {
                    }.getType());

                badWordsWildcard.addAll(dbFilter);
            }

            if (data.getString("piaf_exact", null) != null) {
                ArrayList <String> dbFilter = Xeus.gson.fromJson(
                    data.getString("piaf_exact"),
                    new TypeToken <ArrayList <String>>() {
                    }.getType());

                piaWordsExact.addAll(dbFilter);
            }
            if (data.getString("piaf_wildcard", null) != null) {
                ArrayList <String> dbFilter = Xeus.gson.fromJson(
                    data.getString("piaf_wildcard"),
                    new TypeToken <ArrayList <String>>() {
                    }.getType());

                piaWordsWildcard.addAll(dbFilter);
            }

            if (data.getString("eval_questions", null) != null) {
                ArrayList <String> evaluationQuestions = Xeus.gson.fromJson(
                    data.getString("eval_questions"),
                    new TypeToken <ArrayList <String>>() {
                    }.getType());

                evalQuestions.addAll(evaluationQuestions);
            }

            if (data.getString("report_discord", null) != null) {
                ArrayList <String> dbRoles = Xeus.gson.fromJson(
                    data.getString("report_discord"),
                    new TypeToken <ArrayList <String>>() {
                    }.getType());

                reportPermissionRoles.addAll(dbRoles);
            }

            if (data.getString("prefixes", null) != null) {
                HashMap <String, String> dbPrefixes = Xeus.gson.fromJson(
                    data.getString("prefixes"),
                    new TypeToken <HashMap <String, String>>() {
                    }.getType());

                for (Map.Entry <String, String> item : dbPrefixes.entrySet()) {
                    prefixes.put(item.getKey().toLowerCase(), item.getValue());
                }
            }

            if (data.getString("claimable_roles", null) != null) {
                HashMap <String, String> dbSelfAssignableRoles = Xeus.gson.fromJson(
                    data.getString("claimable_roles"),
                    new TypeToken <HashMap <String, String>>() {
                    }.getType());

                for (Map.Entry <String, String> item : dbSelfAssignableRoles.entrySet()) {
                    selfAssignableRoles.put(item.getKey(), item.getValue().toLowerCase());
                }
            }

            if (data.getString("level_roles", null) != null) {
                HashMap <String, String> dbLevelRoles = Xeus.gson.fromJson(
                    data.getString("level_roles"),
                    new TypeToken <HashMap <String, String>>() {
                    }.getType());

                for (Map.Entry <String, String> item : dbLevelRoles.entrySet()) {
                    levelRoles.put(NumberUtil.parseInt(item.getKey(), -1), item.getValue().toLowerCase());
                }
            }

            if (data.getString("level_exempt_channels", null) != null) {
                List <String> dbExemptExperienceChannels = Xeus.gson.fromJson(
                    data.getString("level_exempt_channels"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String channelId : dbExemptExperienceChannels) {
                    try {
                        levelExemptChannels.add(
                            Long.parseLong(channelId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("level_exempt_roles", null) != null) {
                List <String> dbExemptExperienceChannels = Xeus.gson.fromJson(
                    data.getString("level_exempt_roles"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String channelId : dbExemptExperienceChannels) {
                    try {
                        levelExemptRoles.add(
                            Long.parseLong(channelId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("lockable_channels", null) != null) {
                List <String> dbLockableChannelsChannels = Xeus.gson.fromJson(
                    data.getString("lockable_channels"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String channelId : dbLockableChannelsChannels) {
                    try {
                        lockableChannels.add(
                            Long.parseLong(channelId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("lockable_channels_roles", null) != null) {
                List <String> dbLockableChannelRoles = Xeus.gson.fromJson(
                    data.getString("lockable_channels_roles"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String channelId : dbLockableChannelRoles) {
                    try {
                        lockableChannelsRoles.add(
                            Long.parseLong(channelId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("moderator_roles", null) != null) {
                List <String> modRoles = Xeus.gson.fromJson(
                    data.getString("moderator_roles"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String roleId : modRoles) {
                    try {
                        moderatorRoles.add(
                            Long.parseLong(roleId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("manager_roles", null) != null) {
                List <String> manRoles = Xeus.gson.fromJson(
                    data.getString("manager_roles"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String roleId : manRoles) {
                    try {
                        managerRoles.add(
                            Long.parseLong(roleId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("admin_roles", null) != null) {
                List <String> adminRoles = Xeus.gson.fromJson(
                    data.getString("admin_roles"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String roleId : adminRoles) {
                    try {
                        administratorRoles.add(
                            Long.parseLong(roleId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("no_links_roles", null) != null) {
                List <String> noLinksRole = Xeus.gson.fromJson(
                    data.getString("no_links_roles"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String roleId : noLinksRole) {
                    try {
                        noLinksRoles.add(
                            Long.parseLong(roleId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("group_shout_roles", null) != null) {
                List <String> groupShoutRolesList = Xeus.gson.fromJson(
                    data.getString("group_shout_roles"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String roleId : groupShoutRolesList) {
                    try {
                        groupShoutRoles.add(
                            Long.parseLong(roleId)
                        );
                    } catch (NumberFormatException ignored) {
                        //
                    }
                }
            }

            if (data.getString("modules", null) != null) {
                HashMap <String, Map <String, String>> dbModules = Xeus.gson.fromJson(
                        data.getString("modules"),
                        new TypeToken <HashMap <String, Map <String, String>>>() {
                        }.getType());

                for (Map.Entry <String, Map <String, String>> item : dbModules.entrySet()) {
                    modules.put(item.getKey(), item.getValue());
                }
            }

            if (data.getString("channels", null) != null) {
                HashMap <String, Object> dbChannels = Xeus.gson.fromJson(
                    data.getString("channels"),
                    new TypeToken <HashMap <String, Object>>() {
                    }.getType());

                for (Map.Entry <String, Object> item : dbChannels.entrySet()) {
                    // noinspection unchecked
                    LinkedTreeMap <String, Object> value = (LinkedTreeMap <String, Object>) item.getValue();
                    value.put("id", item.getKey());

                    channels.add(new ChannelTransformer(new DataRow(value), this));
                }
            }
        }

        guildType = partner ? partnerTypeTransformer : new GuildTypeTransformer(data);

        reset();
    }

    public String getId() {
        return id;
    }

    public GuildTypeTransformer getType() {
        return guildType;
    }

    public String getName() {
        return name;
    }

    public String getNameRaw() {
        return nameRaw;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String code) {
        this.locale = code;
    }

    public boolean isLevels() {
        return levels;
    }

    public void setLevels(boolean level) {
        levels = level;
    }

    public boolean isLevelAlerts() {
        return levelAlerts;
    }

    public void setLevelAlerts(boolean levelAlerts) {
        this.levelAlerts = levelAlerts;
    }

    public boolean isLevelHierarchy() {
        return levelHierarchy;
    }

    public boolean isFilter() {
        return filter;
    }

    public void setFilter(boolean filter) {
        this.filter = filter;
    }

    public void setLevelHierarchy(boolean levelHierarchy) {
        this.levelHierarchy = levelHierarchy;
    }

    public String getLevelChannel() {
        return levelChannel;
    }

    public void setLevelChannel(String levelChannel) {
        this.levelChannel = levelChannel;
    }

    public long getAuditLogChannel() {
        return audit_log;
    }

    public void setAuditLogChannel(long audit_log) {
        this.audit_log = audit_log;
    }

    public long getJoinLogsChannel() {
        return join_logs;
    }

    public void setJoinLogs(long join_logs) {
        this.join_logs = join_logs;
    }

    public String getSuggestionChannel() {
        return suggestionChannel;
    }

    public void setSuggestionChannel(String suggestionChannel) {
        this.suggestionChannel = suggestionChannel;
    }

    public String getSuggestionCommunityChannel() {
        return suggestionCommunityChannel;
    }

    public void setSuggestionCommunityChannel(String suggestionCommunityChannel) {
        this.suggestionCommunityChannel = suggestionCommunityChannel;
    }

    public String getSuggestionEmoteId() {
        return suggestionEmoteId;
    }

    public String getSuggestionApprovedChannelId() {
        return suggestionApprovedChannelId;
    }

    public void setSuggestionApprovedChannelId(String suggestionApprovedChannelId) {
        this.suggestionApprovedChannelId = suggestionApprovedChannelId;
    }

    public void setSuggestionEmoteId(String suggestionEmoteId) {
        this.suggestionEmoteId = suggestionEmoteId;
    }

    public String getHandbookReportEmoteId() {
        return reportEmoteId;
    }

    public void setHandbookReportEmoteId(String reportEmoteId) {
        this.reportEmoteId = reportEmoteId;
    }

    public String getHandbookReportChannel() {
        return handbookReportChannel;
    }

    public void setHandbookReportChannel(String handbookReportChannel) {
        this.handbookReportChannel = handbookReportChannel;
    }

    public String getHandbookReportInfoMessage() {
        return reportInfoMessage;
    }

    public int getRobloxGroupId() {
        return robloxGroupId;
    }

    public int getMinimalHrRank() {
        return minimalHrRank;
    }

    public void setMinimalHrRank(int minimalHrRank) {
        this.minimalHrRank = minimalHrRank;
    }

    public int getMinimumLeadRank() {
        return minimumLeadRank;
    }

    public void setEvalAnswerChannel(long evalAnswerChannel) {
        this.evalAnswerChannel = evalAnswerChannel;
    }

    public long getEvalAnswerChannel() {
        return evalAnswerChannel;
    }

    public void setMinimumLeadRank(int minimumLeadRank) {
        this.minimumLeadRank = minimumLeadRank;
    }

    public void setRobloxGroupId(int robloxGroupId) {
        this.robloxGroupId = robloxGroupId;
    }

    public void setHandbookReportInfoMessage(String reportInfoMessage) {
        this.reportInfoMessage = reportInfoMessage;
    }

    public String getPinewoodEventRequestsChannelId() {
        return pinewoodEventRequestsChannelId;
    }

    public void setPinewoodEventRequestsChannelId(String id) {
        this.pinewoodEventRequestsChannelId = id;
    }

    public String getVoteValidationChannel() {
        return voteValidationChannel;
    }

    public void setVoteValidationChannel(String levelChannel) {
        this.voteValidationChannel = levelChannel;
    }

    public Map <Integer, String> getLevelRoles() {
        return levelRoles;
    }

    public double getLevelModifier() {
        return levelModifier;
    }

    public void setLevelModifier(double levelModifier) {
        this.levelModifier = levelModifier;
    }

    public Set <Long> getLevelExemptChannels() {
        return levelExemptChannels;
    }

    public Set <Long> getLevelExemptRoles() {
        return levelExemptRoles;
    }

    public Set <Long> getLockableChannels() {
        return lockableChannels;
    }

    public Set <Long> getLockableChannelRoles() {
        return lockableChannelsRoles;
    }

    public Set <Long> getModeratorRoles() {
        return moderatorRoles;
    }

    public Set <Long> getManagerRoles() {
        return managerRoles;
    }

    public Set <Long> getAdministratorRoles() {
        return administratorRoles;
    }

    public Set <Long> getGroupShoutRoles() {
        return groupShoutRoles;
    }

    public Set <Long> getNoLinksRoles() {
        return noLinksRoles;
    }

    public String getAutorole() {
        return autorole;
    }

    public void setAutorole(String autorole) {
        this.autorole = autorole;
    }

    public String getModlog() {
        return modlog;
    }

    public void setModlog(String modlogChannelId) {
        this.modlog = modlogChannelId;
    }

    public int getModlogCase() {
        return modlogCase;
    }

    public int getOnWatchCase() {
        return onWatchCase;
    }

    public String getOnWatchLog() {
        return onWatch;
    }

    public void setOnWatch(String onWatchChannelId) {
        this.onWatch = onWatchChannelId;
    }

    public void setModlogCase(int modlogCase) {
        this.modlogCase = modlogCase;
    }

    public void setOnWatchCase(int onWatchCase) {
        this.onWatchCase = onWatchCase;
    }

    public void setOnWatchRole(String onWatchRole) {
        this.onWatchRole = onWatchRole;
    }

    public String getMuteRole() {
        return muteRole;
    }

    public String getOnWatchRole() {
        return onWatchRole;
    }

    public void setMuteRole(String muteRole) {
        this.muteRole = muteRole;
    }

    public String getGamenightRole() {
        return gamenightRole;
    }

    public void setGamenightRole(String gamenightRole) {
        this.gamenightRole = gamenightRole;
    }

    public long getReportCategory() {
        return reportCategory;
    }

    public void setReportCategory(long id) {
        this.reportCategory = id;
    }

    public String getFilterLog() {
        return filterLog;
    }

    public void setFilterLog(String s) {
        this.filterLog = s;
    }

    public int getCharacterSpam() {
        return characterSpam;
    }

    public void setCharacterSpam(int characterSpam) {
        this.characterSpam = characterSpam;
    }

    public int getEmojiSpam() {
        return emojiSpam;
    }

    public void setEmojiSpam(int emojiSpam) {
        this.emojiSpam = emojiSpam;
    }

    public int getImageSpam() {
        return imageSpam;
    }

    public void setImageSpam(int imageSpam) {
        this.imageSpam = imageSpam;
    }

    public int getLinkSpam() {
        return linkSpam;
    }

    public void setLinkSpam(int linkSpam) {
        this.linkSpam = linkSpam;
    }

    public int getMassMentionSpam() {
        return massMentionSpam;
    }

    public void setMassMentionSpam(int massMentionSpam) {
        this.massMentionSpam = massMentionSpam;
    }

    public int getMessageSpam() {
        return messageSpam;
    }

    public void setMessageSpam(int messageSpam) {
        this.messageSpam = messageSpam;
    }

    public String getMemberToYoungChannelId() {
        return memberToYoungChannelId;
    }

    public String getPatrolRemittanceChannel() {
        return patrolRemittanceChannel;
    }

    public void setPatrolRemittanceChannel(String patrolRemittanceChannel) {
        this.patrolRemittanceChannel = patrolRemittanceChannel;
    }

    public String getPatrolRemittanceEmoteId() {
        return patrolRemittanceEmoteId;
    }

    public void setPatrolRemittanceEmoteId(String patrolRemittanceEmoteId) {
        this.patrolRemittanceEmoteId = patrolRemittanceEmoteId;
    }

    public String getPatrolRemittanceMessage() {
        return patrolRemittanceMessage;
    }

    public void setPatrolRemittanceMessage(String patrolRemittanceMessage) {
        this.patrolRemittanceMessage = patrolRemittanceMessage;
    }


    public String getMainDiscordRole() {
        return mainDiscordRole;
    }

    public void setMainDiscordRole(String mainDiscordRole) {
        this.mainDiscordRole = mainDiscordRole;
    }

    public void setMemberToYoungChannelId(String memberToYoungChannelId) {
        this.memberToYoungChannelId = memberToYoungChannelId;
    }

    public Map <String, String> getSelfAssignableRoles() {
        return selfAssignableRoles;
    }

    public Map <String, String> getPrefixes() {
        return prefixes;
    }

    public Map <String, String> getAliases() {
        return aliases;
    }

    public List <String> getBadWordsExact() {
        return badWordsExact;
    }

    public List <String> getBadWordsWildcard() {
        return badWordsWildcard;
    }

    public List <String> getPIAWordsWildcard() {
        return piaWordsWildcard;
    }

    public List <String> getPIAWordsExact() {
        return piaWordsExact;
    }

    public List <String> getReportPermissionRoles() {
        return reportPermissionRoles;
    }

    public List <ChannelTransformer> getChannels() {
        return channels;
    }

    public List <String> getEvalQuestions() {
        return evalQuestions;
    }

    public Map <String, Map <String, String>> getCategories() {
        return modules;
    }

    public boolean isPartner() {
        return partner;
    }

    @CheckReturnValue
    public ChannelTransformer getChannel(String id) {
        return getChannel(id, true);
    }

    @CheckReturnValue
    public ChannelTransformer getChannel(String id, boolean createIfDontExists) {
        for (ChannelTransformer channel : channels) {
            if (channel.getId().equals(id)) {
                return channel;
            }
        }

        if (createIfDontExists && createChannelTransformer(getId(), id)) {
            return getChannel(id, false);
        }

        return null;
    }

    public boolean createChannelTransformer(@Nonnull String guildId, @Nonnull String channelId) {
        if (!Objects.equals(guildId, getId())) {
            throw new RuntimeException(String.format("The given channel belongs to a different guild. Channel ID: %s Channel Guild ID: %s | Guild ID: %s",
                channelId, guildId, getId()
            ));
        }

        if (getChannel(channelId, false) != null) {
            return false;
        }

        HashMap <String, Object> data = new HashMap <>();
        data.put("id", channelId);
        channels.add(new ChannelTransformer(new DataRow(data), this));

        return true;
    }

    public boolean createChannelTransformer(TextChannel channel) {
        return createChannelTransformer(channel.getGuild().getId(), channel.getId());
    }

    public String channelsToJson() {
        Map <String, Object> objects = new HashMap <>();
        if (channels.isEmpty()) {
            return null;
        }

        for (ChannelTransformer transformer : channels) {
            objects.put(transformer.getId(), transformer.toMap());
        }

        return Xeus.gson.toJson(objects);
    }
}
