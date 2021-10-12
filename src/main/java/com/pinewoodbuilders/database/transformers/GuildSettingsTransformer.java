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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.reflect.TypeToken;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.database.transformers.Transformer;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.controllers.GlobalSettingsController;

import net.dv8tion.jda.api.entities.Guild;

public class GuildSettingsTransformer extends Transformer {
    
    // Global Settings
    private String id;
    private String name;
    private String nameRaw;
    private long robloxGroupId = 0;
    private String groupName;
    private long mainGroupId = 0;
    private long mainDiscordRole = 0;
    private int minimumHrRank = 255;
    private int minimumLeadRank = 255;
    private Set<Long> localLeadRoles = new HashSet<>();
    private Set<Long> localHRRoles = new HashSet<>();
    private Set<Long> noLinksRoles = new HashSet<>();
    private Set<Long> groupShoutRoles = new HashSet<>();
    private boolean pbVerificationTrelloban;
    private String pbVerificationBlacklistLink;
    private boolean verificationAntiMainGlobalModImpersonation;
    private boolean permissionBypass;
    private boolean isOfficialSubGroup;

    // Global Transformer
    private GlobalSettingsTransformer globalTransformer = null;

    // Guild Settings
    private final List<String> badWordsExact = new ArrayList<>();
    private final List<String> badWordsWildcard = new ArrayList <>();
    private final List<String> evalQuestions = new ArrayList<>();
    private long emojiId = 0;
    private long onWatchChannel = 0;
    private long onWatchRole = 0;
    private boolean localFilter = false;
    private long localFilterLog = 0;
    private long patrolRemittanceChannel = 0;
    private String patrolRemittance = null;
    private long suggestionChannelId = 0;
    private long handbookReportChannel = 0;
    private long suggestionCommunityChannelId = 0;
    private long suggestionApprovedChannelId = 0;
    private long joinLogs = 0;
    private long auditLogsChannelId = 0;
    private long voteValidationChannelId = 0;
    private long userAlertsChannelId = 0;
    private long evaluationEvalChannel = 0;

    public GuildSettingsTransformer(Guild guild, DataRow data) {
        super(data);

        if (hasData()) {
            id = data.getString("id");

            robloxGroupId = data.getLong("roblox_group_id");
            groupName = data.getString("group_name");
            mainGroupId = data.getLong("main_group_id");
            if (mainGroupId != 0) {
                GlobalSettingsTransformer globalSettings = GlobalSettingsController.fetchGlobalSettingsFromGroupSettings(Xeus.getInstance(), mainGroupId);
                globalTransformer = globalSettings != null ? globalSettings : null;
            }
            mainDiscordRole = data.getLong("main_discord_role");
            minimumHrRank = data.getInt("minimum_hr_rank");
            minimumLeadRank = data.getInt("minimum_lead_rank");   

            pbVerificationTrelloban = data.getBoolean("pb_verification_trelloban");
            pbVerificationBlacklistLink = data.getString("pb_verification_blacklist_link");
            verificationAntiMainGlobalModImpersonation = data.getBoolean("verification_anti_main_global_mod_impersonate");
            permissionBypass = data.getBoolean("permission_bypass"); 
            
            emojiId = data.getLong("emoji_id");
            onWatchChannel = data.getLong("on_watch_channel");
            onWatchRole = data.getLong("on_watch_role");
            localFilter = data.getBoolean("local_filter");
            localFilterLog = data.getLong("local_filter_log");
            patrolRemittanceChannel = data.getLong("patrol_remittance_channel");
            patrolRemittance = data.getString("patrol_remittance_message");
            handbookReportChannel = data.getLong("handbook_report_channel");
            suggestionChannelId = data.getLong("suggestion_channel_id");
            suggestionCommunityChannelId = data.getLong("suggestion_community_channel_id");
            suggestionApprovedChannelId = data.getLong("suggestion_approved_channel_id");
            joinLogs = data.getLong("join_logs");
            auditLogsChannelId = data.getLong("audit_logs_channel_id");
            voteValidationChannelId = data.getLong("vote_validation_channel_id");
            userAlertsChannelId = data.getLong("user_alerts_channel_id");
            evaluationEvalChannel = data.getLong("evaluation_answer_channel");
            isOfficialSubGroup = data.getBoolean("official_sub_group");

            if (data.getString("moderator_roles", null) != null) {
                List <String> moderatorRoles = Xeus.gson.fromJson(
                    data.getString("moderator_roles"),
                    new TypeToken <List <String>>() {
                    }.getType());

                for (String roleId : moderatorRoles) {
                    try {
                        localHRRoles.add(
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
                        localLeadRoles.add(
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
            
            if (data.getString("filter_exact", null) != null) {
                List<String> dbFilter = Xeus.gson.fromJson(data.getString("filter_exact"),
                        new TypeToken<List<String>>() {
                        }.getType());

                badWordsExact.addAll(dbFilter);
            }
            if (data.getString("filter_wildcard", null) != null) {
                List<String> dbFilter = Xeus.gson.fromJson(data.getString("filter_wildcard"),
                        new TypeToken<List<String>>() {
                        }.getType());

                badWordsWildcard.addAll(dbFilter);
            }
            if (data.getString("eval_questions", null) != null) {
                List<String> evaluationQuestions = Xeus.gson.fromJson(data.getString("eval_questions"),
                        new TypeToken<List<String>>() {
                        }.getType());

                evalQuestions.addAll(evaluationQuestions);
            }

            reset();
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameRaw() {
        return nameRaw;
    }
    public void setId(String id) {
        this.id = id;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setNameRaw(String nameRaw) {
        this.nameRaw = nameRaw;
    }

    public long getRobloxGroupId() {
        return this.robloxGroupId;
    }

    public void setRobloxGroupId(long robloxGroupId) {
        this.robloxGroupId = robloxGroupId;
    }

    public String getGroupName() {
        return this.groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public long getMainGroupId() {
        return this.mainGroupId;
    }

    public void setMainGroupId(long mainGroupId) {
        this.mainGroupId = mainGroupId;
    }

    public GlobalSettingsTransformer getGlobalSettings() {
        return this.globalTransformer;
    }

    public long getMainDiscordRole() {
        return this.mainDiscordRole;
    }

    public void setMainDiscordRole(long mainDiscordRole) {
        this.mainDiscordRole = mainDiscordRole;
    }

    public int getMinimumHrRank() {
        return this.minimumHrRank;
    }

    public void setMinimumHrRank(int minimumHrRank) {
        this.minimumHrRank = minimumHrRank;
    }

    public int getMinimumLeadRank() {
        return this.minimumLeadRank;
    }

    public void setMinimalLeadRank(int minimumLeadRank) {
        this.minimumLeadRank = minimumLeadRank;
    }

   
    public Set<Long> getLeadRoles() {
        return this.localLeadRoles;
    }

    public Set<Long> getHRRoles() {
        return this.localHRRoles;
    }

    public Set<Long> getGroupShoutRoles() {
        return this.groupShoutRoles;
    }
    
    public Set<Long> getNoLinksRoles() {
        return this.noLinksRoles;
    }

    public boolean isPbVerificationTrelloban() {
        return this.pbVerificationTrelloban;
    }

    public boolean getPbVerificationTrelloban() {
        return this.pbVerificationTrelloban;
    }

    public void setPbVerificationTrelloban(boolean pbVerificationTrelloban) {
        this.pbVerificationTrelloban = pbVerificationTrelloban;
    }

    public String getPbVerificationBlacklistLink() {
        return this.pbVerificationBlacklistLink;
    }

    public void setPbVerificationBlacklistLink(String pbVerificationBlacklistLink) {
        this.pbVerificationBlacklistLink = pbVerificationBlacklistLink;
    }

    public boolean isVerificationAntiMainGlobalModImpersonation() {
        return this.verificationAntiMainGlobalModImpersonation;
    }

    public boolean getVerificationAntiMainGlobalModImpersonation() {
        return this.verificationAntiMainGlobalModImpersonation;
    }

    public void setVerificationAntiMainGlobalModImpersonation(boolean verificationAntiMainGlobalModImpersonation) {
        this.verificationAntiMainGlobalModImpersonation = verificationAntiMainGlobalModImpersonation;
    }

    public boolean isPermissionBypass() {
        return this.permissionBypass;
    }

    public boolean getPermissionBypass() {
        return this.permissionBypass;
    }

    public void setPermissionBypass(boolean permissionBypass) {
        this.permissionBypass = permissionBypass;
    }

    public List<String> getBadWordsExact() {
        return this.badWordsExact;
    }

    public List<String> getBadWordsWildcard() {
        return this.badWordsWildcard;
    }

    public List<String> getEvalQuestions() {
        return this.evalQuestions;
    }

    public long getEmojiId() {
        return this.emojiId;
    }

    public long getOnWatchChannel() {
        return this.onWatchChannel;
    }

    public long getOnWatchRole() {
        return this.onWatchRole;
    }

    public boolean getLocalFilter() {
        return this.localFilter;
    }

    public boolean isLocalFilter() {
        return this.localFilter;
    }

    public long getLocalFilterLog() {
        return this.localFilterLog;
    }

    public long getPatrolRemittanceChannel() {
        return this.patrolRemittanceChannel;
    }

    public String getPatrolRemittance() {
        return this.patrolRemittance;
    }

    public long getSuggestionChannelId() {
        return this.suggestionChannelId;
    }

    public long getHandbookReportChannel() {
        return this.handbookReportChannel;
    }

    public long getSuggestionCommunityChannelId() {
        return this.suggestionCommunityChannelId;
    }

    public long getSuggestionApprovedChannelId() {
        return this.suggestionApprovedChannelId;
    }

    public long getJoinLogs() {
        return this.joinLogs;
    }

    public long getAuditLogsChannelId() {
        return this.auditLogsChannelId;
    }

    public long getVoteValidationChannelId() {
        return this.voteValidationChannelId;
    }

    public long getUserAlertsChannelId() {
        return this.userAlertsChannelId;
    }

    public long getEvaluationEvalChannel() {
        return this.evaluationEvalChannel;
    }
    public void setEmojiId(long emojiId) {
        this.emojiId = emojiId;
    }
    public void setOnWatchChannel(long onWatchChannel) {
        this.onWatchChannel = onWatchChannel;
    }
    public void setOnWatchRole(long onWatchRole) {
        this.onWatchRole = onWatchRole;
    }
    public void setLocalFilter(boolean localFilter) {
        this.localFilter = localFilter;
    }
    public void setLocalFilterLog(long localFilterLog) {
        this.localFilterLog = localFilterLog;
    }
    public void setPatrolRemittanceChannel(long patrolRemittanceChannel) {
        this.patrolRemittanceChannel = patrolRemittanceChannel;
    }
    public void setPatrolRemittance(String patrolRemittance) {
        this.patrolRemittance = patrolRemittance;
    }
    public void setSuggestionChannelId(long suggestionChannelId) {
        this.suggestionChannelId = suggestionChannelId;
    }
    public void setHandbookReportChannel(long handbookReportChannel) {
        this.handbookReportChannel = handbookReportChannel;
    }
    public void setSuggestionCommunityChannelId(long suggestionCommunityChannelId) {
        this.suggestionCommunityChannelId = suggestionCommunityChannelId;
    }
    public void setSuggestionApprovedChannelId(long suggestionApprovedChannelId) {
        this.suggestionApprovedChannelId = suggestionApprovedChannelId;
    }
    public void setJoinLogs(long joinLogs) {
        this.joinLogs = joinLogs;
    }
    public void setAuditLogsChannelId(long auditLogsChannelId) {
        this.auditLogsChannelId = auditLogsChannelId;
    }
    public void setVoteValidationChannelId(long voteValidationChannelId) {
        this.voteValidationChannelId = voteValidationChannelId;
    }
    public void setUserAlertsChannelId(long userAlertsChannelId) {
        this.userAlertsChannelId = userAlertsChannelId;
    }
    public void setEvaluationEvalChannel(long evaluationEvalChannel) {
        this.evaluationEvalChannel = evaluationEvalChannel;
    }
    public boolean isOfficialSubGroup() {
        return this.isOfficialSubGroup;
    }
    public void setIsOfficialSubGroup(boolean sOSG) {
        this.isOfficialSubGroup = sOSG;
    }
}
