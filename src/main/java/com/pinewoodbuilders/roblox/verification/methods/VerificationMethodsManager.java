package com.pinewoodbuilders.roblox.verification.methods;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.contracts.verification.VerificationResult;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.awt.*;
import java.time.Instant;

public class VerificationMethodsManager {
    private final Xeus avaire;
    private final RobloxAPIManager robloxAPIManger;

    public VerificationMethodsManager(Xeus avaire, RobloxAPIManager robloxAPIManager) {
        this.avaire = avaire;
        this.robloxAPIManger = robloxAPIManager;
    }

    public void slashCommandVerify(Member member, Guild guild, InteractionHook hook) {

        GuildSettingsTransformer transformer = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, guild);
        VerificationResult result = robloxAPIManger.getVerification().verify(transformer, member, guild, true);

        String image = getImageFromVerificationEntity(result.getVerificationEntity());
        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor(member.getUser().getName(), "https://xeus.pinewood-builders.com", member.getUser().getEffectiveAvatarUrl())
            .setDescription(result.getMessage())
            .setTimestamp(Instant.now())
            .setThumbnail(image != null ? image : "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=1")
            .setFooter(result.getVerificationEntity() != null ? result.getVerificationEntity().getRobloxUsername() : "Verification failed...");

        if (!result.isSuccess()) {
            eb.setColor(new Color(255, 0, 0));
            hook.setEphemeral(true).sendMessage(member.getAsMention()).addEmbeds(eb.build()).queue();
        } else {
            eb.setColor(new Color(0, 255, 0));
            hook.sendMessage(member.getAsMention()).addEmbeds(eb.build()).queue();
        }


        /*HashMap <Long, List <TrellobanLabels>> trellobans = avaire.getRobloxAPIManager().getKronosManager().getTrelloBans();
        if (member == null) {
            hook.sendMessage("You do not exist, so you have not been verified.").queue();
            return;
        }

        if (!PermissionUtil.canInteract(guild.getSelfMember(), member)) {
            hook.sendMessage("I cannot modify `"+ member.getEffectiveName() +"`, please ask an guild admin to fix this.").queue();
            return;
        }

        if (member.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("Xeus Bypass") || r.getName().equalsIgnoreCase("RoVer Bypass"))) {
            hook.sendMessage("`" + member.getEffectiveName() +"` has a bypass role, you will not be verified.").queue();
            return;
        }

        VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification().fetchVerificationWithBackup(member.getId(), useCache);
        if (verificationEntity == null) {
            hook.sendMessage("`"+ member.getEffectiveName() +"` is not verified. Please run `!verify` as a normal command.").queue();
            return;
        }

        if (trellobans != null) {
            if (isTrelloBanned(verificationEntity)) {
                hook.sendMessage("`" + member.getEffectiveName() + "` is trello-banned.").queue();
                return;
            }
        }

        try {
            Collection accounts = avaire.getDatabase().newQueryBuilder(Constants.ANTI_UNBAN_TABLE_NAME).where("roblox_user_id", verificationEntity.getRobloxId()).orWhere("roblox_username", verificationEntity.getRobloxUsername()).get();
            if (accounts.size() > 0) {
                hook.sendMessage("`"+ member.getEffectiveName() +"` is banned though the MGM Ban-list.").queue();
            }
        } catch (SQLException throwables){
        }

        if (guild.getId().equals("438134543837560832")) {
            if (avaire.getBlacklistManager().getPBSTBlacklist().contains(verificationEntity.getRobloxId())) {
                hook.sendMessage("Blacklisted from PBST.").queue();
                return;
            }
        } else if (guild.getId().equals("572104809973415943")) {
            if (avaire.getBlacklistManager().getTMSBlacklist().contains(verificationEntity.getRobloxId())) {
                hook.sendMessage("Blacklisted from TMS.").queue();
                return;
            }
        } else if (guild.getId().equalsIgnoreCase("498476405160673286")) {
            if (avaire.getBlacklistManager().getPBMBlacklist().contains(verificationEntity.getRobloxId())) {
                hook.sendMessage("Blacklisted from PBM.").queue();
                return;
            }
        } else if (guild.getId().equalsIgnoreCase("436670173777362944")) {
            if (avaire.getBlacklistManager().getPETBlacklist().contains(verificationEntity.getRobloxId())) {
                hook.sendMessage("Blacklisted from PET.").queue();
                return;
            }
        }

        VerificationTransformer verificationTransformer = VerificationController.fetchVerificationFromGuild(avaire, guild);
        if (verificationTransformer == null) {
            hook.sendMessage("Verificationtransformer is null.").queue();
            return;
        }

        if (verificationTransformer.getRanks() == null || verificationTransformer.getRanks().length() < 2) {
            hook.sendMessage("Ranks is null.").queue();
            return;
        }

        List <RobloxUserGroupRankService.Data> robloxRanks = avaire.getRobloxAPIManager().getUserAPI().getUserRanks(verificationEntity.getRobloxId());
        if (robloxRanks == null || robloxRanks.size() == 0) {
            hook.sendMessage(""+ member.getEffectiveName() +" does not have groups on your profile, please join a group.").queue();
            return;
        }

        GuildRobloxRanksService guildRanks = (GuildRobloxRanksService) avaire.getRobloxAPIManager().toService(verificationTransformer.getRanks(), GuildRobloxRanksService.class);

        Map <GuildRobloxRanksService.GroupRankBinding, Role> bindingRoleMap =
            guildRanks.getGroupRankBindings().stream()
                .collect(Collectors.toMap(Function.identity(), groupRankBinding -> guild.getRoleById(groupRankBinding.getRole()))),
            bindingRoleAddMap = new HashMap <>();

        //Loop through all the group-rank bindings
        bindingRoleMap.forEach((groupRankBinding, role) -> {
            List <String> robloxGroups = robloxRanks.stream().map(data -> data.getGroup().getId() + ":" + data.getRole().getRank())
                .collect(Collectors.toList());

            for (String groupRank : robloxGroups) {
                String[] rank = groupRank.split(":");
                String groupId = rank[0];
                String rankId = rank[1];

                if (groupRankBinding.getGroups().stream()
                    .filter(group -> !group.getId().equals("GamePass"))
                    .anyMatch(group -> group.getId().equals(groupId) && group.getRanks().contains(Integer.valueOf(rankId)))) {
                    bindingRoleAddMap.put(groupRankBinding, role);
                }

            }
        });

        bindingRoleMap.forEach((groupRankBinding, role) -> {
            groupRankBinding.getGroups().stream().filter(data -> data.getId().equals("GamePass"))
                .forEach(pass -> {
                    List <RobloxGamePassService.Datum> rgs = avaire.getRobloxAPIManager().getUserAPI().getUserGamePass(verificationEntity.getRobloxId(), Long.valueOf(pass.getRanks().get(0)));
                    if (rgs != null) {
                        bindingRoleAddMap.put(groupRankBinding, role);
                    }
                });
        });

        //Collect the toAdd and toRemove roles from the previous maps
        java.util.Collection <Role> rolesToAdd = bindingRoleAddMap.values().stream().filter(role -> PermissionUtil.canInteract(guild.getSelfMember(), role)).collect(Collectors.toList()),
            rolesToRemove = bindingRoleMap.values()
                .stream().filter(role -> !bindingRoleAddMap.containsValue(role) && PermissionUtil.canInteract(guild.getSelfMember(), role)).collect(Collectors.toList());

        if (verificationTransformer.getVerifiedRole() != 0) {
            Role r = guild.getRoleById(verificationTransformer.getVerifiedRole());
            if (r != null) {
                rolesToAdd.add(r);
            }
        }


        StringBuilder stringBuilder = new StringBuilder();
        //Modify the roles of the member
        guild.modifyMemberRoles(member, rolesToAdd, rolesToRemove)
            .queue();

        String rolesToAddAsString = "__**`" + member.getEffectiveName() + "`**__\nRoles to add:\n" + (rolesToAdd.size() > 0
            ? (rolesToAdd.stream().map(role -> {if (role.hasPermission(Permission.BAN_MEMBERS)) {
            return "- **" + role.getName() + "**";
        } else {
            return "- " + role.getName();
        }})
            .collect(Collectors.joining("\n"))) : "No roles have been added");
        stringBuilder.append(rolesToAddAsString);

        String rolesToRemoveAsString = "\n```Roles to remove```:\n" + (bindingRoleMap.size() > 0
            ? (rolesToRemove.stream().map(role -> "- `" + role.getName() + "`")
            .collect(Collectors.joining("\n"))) : "No roles have been removed");
        //stringBuilder.append(rolesToRemoveAsString);


        if (!verificationEntity.getRobloxUsername().equals(member.getEffectiveName())) {
            if (PermissionUtil.canInteract(guild.getSelfMember(), member)) {
                guild.modifyNickname(member, verificationTransformer.getNicknameFormat().replace("%USERNAME%", verificationEntity.getRobloxUsername())).queue();
                stringBuilder.append("\n\nNickname has been set to `").append(verificationEntity.getRobloxUsername()).append("`");
            } else {
                stringBuilder.append("\n\nChanging nickname failed :(");
            }
        }
        EmbedBuilder eb = new EmbedBuilder().setThumbnail(getImageFromVerificationEntity(verificationEntity)).setColor(new Color(0, 255, 0)).setDescription(stringBuilder.toString()).setFooter("Have fun!");
        hook.sendMessageEmbeds(eb.build()).queue();*/

    }

    private boolean isTrelloBanned(VerificationEntity verificationEntity) {
        return avaire.getRobloxAPIManager().getKronosManager().getTrelloBans().containsKey(verificationEntity.getRobloxId());
    }

    private String getImageFromVerificationEntity(VerificationEntity ve) {
        if (ve == null) {
            return null;
        }
        return "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + ve.getRobloxId();
    }
}
