package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.contracts.appeals.AppealType;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.contracts.kronos.TrellobanLabels;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.controllers.GuildSettingsController;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.RandomUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class AppealsServerEventAdapter extends EventAdapter {

    public AppealsServerEventAdapter(Xeus avaire) {
        super(avaire);
    }

    //appeal-questions
    public void onAppealModelInteractionEvent(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "appeal-questions" ->
                event.editMessage(event.getMember().getAsMention()).setEmbeds(new PlaceholderMessage(new EmbedBuilder(),
                        """
                            **Roblox Username**:
                            `:username`
                                                
                            **When did this occur**:
                            `:whenOccur`
                                      
                            **Why was this moderation action put on you**:
                            ```:whyPut```
                                                
                            **Why do you believe this moderation action should be removed/reduced**
                            ```:whyRemove```
                                                
                            **What will you do to help prevent this from occurring again if your appeal is accepted**:
                            ```:prevention```
                            """
                    )
                        .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                        .setTitle("Pinewood - Appeal System")
                        .set("username", event.getValue("username").getAsString())
                        .set("whenOccur", event.getValue("whenOccur").getAsString())
                        .set("whyPut", event.getValue("whyPut").getAsString())
                        .set("whyRemove", event.getValue("whyRemove").getAsString())
                        .set("prevention", event.getValue("prevention").getAsString())
                        .buildEmbed()
                ).setComponents(Collections.emptyList()).queue();
        }
    }

    public void onAppealsStringSelectInteraction(StringSelectInteractionEvent event) {
        String[] selection = event.getSelectedOptions().get(0).getValue().split(":");

        if (selection[0].startsWith("decline"))
            event.editMessageEmbeds(new EmbedBuilder().setDescription("Rejected").build()).queue();

        switch (selection[0].toLowerCase()) {
            case "guild" -> guildSelectionMenu(selection, event);
            case "appeal" -> createAppealChannel(selection, event, event.getGuild(), event.getUser());
            case "deletion" -> createDeletionChannel(selection, event, event.getGuild(), event.getUser());
        }
    }


    // start-appeal
    public void onAppealsButtonClickEvent(ButtonInteractionEvent event) {
        String id = event.getButton().getId();
        if (id == null) return;

        switch (id.toLowerCase()) {
            case "start-deletion" -> event.deferReply(true).addEmbeds(new EmbedBuilder().setDescription(
                """
                    You have activated the Pinewood Data Deletion System. By continuing you agree to the following:
                                    
                    ***__Policies you will agree to__***
                    **1**. Your data will not be recoverable. This is an irreversible process.
                    **2**. All data will be removed from our databases. Unless they are connected to punishments like global-bans, game-mutes, trello-bans, rank locks or blacklists.
                    **4**. You waive any rights to a requesting your points or data to be restored.
                    **5**. Data deletion requests will be reviewed by a Facilitator or above, do not ping anyone, or your deletion request may be denied.
                    **6**. If this system is abused, you may receive punishment as the Facilitators see fit.
                    **7**. Do not go begging for your data to be restored. ___YOU WILL NOT BE ABLE TO RESTORE YOUR DATA___.
                    **8**. In case of a Xeus deletion, you will be given a 72 hour block on the bot, unable to verify or use the bot in any discord.
                    **9**. If you are trello-banned, you cannot use this system.
                                        
                    By clicking on the "I AGREE" selector below ___YOU CONFIRM THAT YOU WANT YOUR DATA DELETED, THAT THIS IS AN IRREVERSIBLE ACTION AND THAT WE CANNOT BE HELD ACCOUNTABLE FOR ANY LOSSES AFTER THE DELETION___
                    """).setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL).build()).addActionRow(
                StringSelectMenu.create("punishment-selection")
                    .addOption("I REJECT",
                        "decline-1:PIA:deletion",
                        "Use this button to reject the data deletion request.",
                        Emoji.fromFormatted("<:no:694270050257076304>"))
                    .addOption("I REJECT",
                        "decline-2:PIA:deletion",
                        "Use this button to reject the data deletion request.",
                        Emoji.fromFormatted("<:no:694270050257076304>"))
                    .addOption("I REJECT",
                        "decline-3:PIA:deletion",
                        "Use this button to reject the data deletion request.",
                        Emoji.fromFormatted("<:no:694270050257076304>"))

                    .addOption("I REJECT",
                        "decline-4:PIA:deletion",
                        "Use this button to reject the data deletion request.",
                        Emoji.fromFormatted("<:no:694270050257076304>"))

                    .addOption("I AGREE",
                        "deletion:PIA:deletion",
                        "Use this button to confirm a data deletion request.",
                        Emoji.fromFormatted("<:cereal2:958119849958211664>"))
                    .addOption("I REJECT",
                        "decline-5:PIA:deletion",
                        "Use this button to reject the data deletion request.",
                        Emoji.fromFormatted("<:no:694270050257076304>"))
                    .addOption("I REJECT",
                        "decline-6:PIA:deletion",
                        "Use this button to reject the data deletion request.",
                        Emoji.fromFormatted("<:no:694270050257076304>"))

                    .addOption("I REJECT",
                        "decline-7:PIA:deletion",
                        "Use this button to reject the data deletion request.",
                        Emoji.fromFormatted("<:no:694270050257076304>"))

                    .addOption("I REJECT",
                        "decline-8:PIA:deletion",
                        "Use this button to reject the data deletion request.",
                        Emoji.fromFormatted("<:no:694270050257076304>"))

                    .build().asEnabled()
            ).queue();
            case "start-appeal" -> event.deferReply().setEphemeral(true).addEmbeds(new EmbedBuilder().setDescription(
                """
                    You have activated the Pinewood Appeal System. By continuing you agree to the following:
                                    
                    ***__Miscellaneous Information__***
                    **1**. If you do not do anything in your appeal for 24 hours, the ticket will be deleted.
                    **2**. You cannot appeal negative points/marks in divisions, reach out to a leader in that division instead.
                    **3**. Purchases of in-game content with Robux does not exempt you from the rules nor entitle you to a free unban. Rules are rules, and they apply to everyone.
                    **4**. You waive any rights to a refund, maintaining any points you have earned in a subgroup. Or credits you have earned in-game (If applicable, this is a case-by-case basis).
                    **5**. Your account is your responsibility, unless you have indisputable proof we will not accept any excuse about unauthorized account usage.
                    **6**. If you are found to be using a fake account to appeal, you will be banned.
                    **7**. Do not make appeals for no reason; if you do so multiple times, you will be removed from the server.
                    """).build(), new EmbedBuilder().setDescription(
                """
                    Please allow up to 96 hours before your appeal starts to be reviewed.
                    Responses are given in our free time, you may not get a response if your appeal is denied.
                    Make sure your DM's are enabled so Xeus can send you a transcript.
                                        
                    And you may NOT contact a moderator or lead of a division to view your appeal quicker, do this and they have the full right to delete the appeal.
                    The time until you may appeal again is decided by the leader/moderator responding to your appeal.
                                        
                    __We have the full right to decide what to do with your appeal, being able to appeal is a privilege. If you cannot deal with this, you will be removed from the server.__
                                        
                    Pressing the button below confirms you agree to these terms and rules. If you choose to ignore this we have the full right to deny your appeal.
                    """
            ).build()).addActionRow(Button.primary("agree-rules",
                "I have read and approve of the rules listed in the embed above.")).queue();
            case "agree-rules" -> event.getInteraction().editMessageEmbeds(new EmbedBuilder().setDescription(
                """
                    You have agreed to the rules.
                    We're asking about your appeal now, what punishment would you like to appeal for?
                                    
                    __Please read through the entire selection menu!!!__
                    """).build()).setActionRow(
                StringSelectMenu.create("punishment-selection")
                    .addOption("Appeal for a trello ban",
                        "appeal:PIA:trelloban",
                        "You may appeal a trello-ban through this selection. ",
                        Emoji.fromFormatted("<:trelloban:997965939792412823>"))
                    .addOption("Appeal for a game ban",
                        "appeal:PIA:gameban",
                        "You may appeal a game-ban through this selection. ",
                        Emoji.fromFormatted("<:gameban:997965983153147955>"))
                    .addOption("Appeal for a game mute",
                        "appeal:PIA:gamemute",
                        "You may appeal a game-mute through this selection. ",
                        Emoji.fromFormatted("<:gamemute:997966017143775252>"))
                    .addOption("Appeal for a global ban",
                        "appeal:PIA:globalban",
                        "You may appeal a globalban through this selection. ",
                        Emoji.fromFormatted("<:globalban:997966041277804546>"))
                    .addOption("Appeal for a raid blacklist",
                        "appeal:RAID:raidblacklist",
                        "You may appeal a raid-blacklist through this selection.",
                        Emoji.fromFormatted("<:raidblacklist:1018269384281690304>"))
                    .addOption("Appeal for a weapon ban",
                        "appeal:PIA:weaponban",
                        "You may appeal a weaponban through this selection.",
                        Emoji.fromFormatted("<:weaponban:997966060542230538>"))
                    .addOption("Appeal for a group discord ban",
                        "guild:groupdiscordban",
                        "You may appeal a discord ban here.",
                        Emoji.fromFormatted("<:groupdiscordban:998332587447681135>"))
                    .addOption("Appeal for a group ranklock",
                        "guild:groupranklock",
                        "You may appeal a group ranklock here.",
                        Emoji.fromFormatted("<:ranklock:998332416991170651>"))
                    .addOption("Appeal for a group blacklist",
                        "guild:groupblacklist",
                        "You may appeal a group blacklist here.",
                        Emoji.fromFormatted("<:blacklist:998332444916858880>"))
                    .addOption("Create a appeal for something else",
                        "appeal:PIA:other",
                        "Any special cases go here..",
                        Emoji.fromFormatted("<:CadetThinking:893602259693338665>"))
                    .setRequiredRange(1, 1).build().asEnabled()).queue();
            case "questions-respond" -> event.replyModal(buildQuestionsModal(event)).queue();
        }
    }

    private Modal buildQuestionsModal(ButtonInteractionEvent event) {
        VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(event.getUser().getId());

        String fetchedUsername = verificationEntity != null ? verificationEntity.getRobloxUsername() : event.getMember().getEffectiveName();

        TextInput username = TextInput.create("username", "What is your roblox username?", TextInputStyle.SHORT)
            .setPlaceholder("The username you'd like to appeal with.")
            .setValue(fetchedUsername)
            .setRequired(true)
            .setMinLength(3)
            .setMaxLength(32) // or setRequiredRange(10, 100)
            .build();

        TextInput whenOccur = TextInput.create("whenOccur", "When did this occur?", TextInputStyle.SHORT)
            .setPlaceholder("Give your best time estimate.")
            .setRequiredRange(1, 100)
            .setRequired(true)
            .build();

        TextInput whyPut = TextInput.create("whyPut", "Why did you get this punishment?", TextInputStyle.PARAGRAPH)
            .setPlaceholder("We don't punish for nothing.")
            .setRequiredRange(1, 500)
            .setRequired(true)
            .build();

        TextInput whyRemove = TextInput.create("whyRemove", "Why should we remove/reduce your punishment?", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Give us a compelling reason. (Remember the rules you agreed to earlier)")
            .setRequiredRange(1, 1000)
            .setRequired(true)
            .build();

        TextInput prevention = TextInput.create("prevention", "How will you stop this from happening again?", TextInputStyle.PARAGRAPH)
            .setPlaceholder("We wouldn't want you causing more trouble, this will get you removed forever.")
            .setRequiredRange(1, 1000)
            .setRequired(true)
            .build();

        return Modal.create("appeal-questions", "Answer these questions to appeal!").addActionRows(
            ActionRow.of(username),
            ActionRow.of(whenOccur),
            ActionRow.of(whyPut),
            ActionRow.of(whyRemove),
            ActionRow.of(prevention)
        ).build();
    }

    private void guildSelectionMenu(String[] value, StringSelectInteraction reply) {
        AppealType type = AppealType.fromName(value[1]);
        if (type == null) {reply.editMessage("Type not found.").queue(); return;}
        if (!type.isGuildSelect()) {reply.editMessage("No guild select.").queue(); return;}
        StringSelectMenu.Builder menu = StringSelectMenu.create("user-selection")
            .addOption("Pinewood Builders Security Team",
                "appeal:PBST:" + type.getName(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromFormatted("<:PBST:790431720297857024>"))
            .addOption("Pinewood Emergency Team",
                "appeal:PET:" + type.getName(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromFormatted("<:PET:694389856071319593>"))
            .addOption("The Mayhem Syndicate",
                "appeal:TMS:" + type.getName(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromFormatted("<:TMS:757737764417437827>"))
            .setRequiredRange(1, 1);

        if (!type.getName().equals("groupranklock")) {
            menu.addOption("Pinewood Builders Media",
                "appeal:PBM:" + type.getName(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromFormatted("<:PBM:757736209333223434>"));
        }

        if (type.getName().equals("groupdiscordban")) {
            menu.addOption("Other discord ban",
                "appeal:PIA:" + type.getName(),
                "Appeal your (other) " + type.getCleanName() + " here.",
                Emoji.fromFormatted("<:otherservers:997966085175386212>"));
        }


        reply.editMessageEmbeds(new EmbedBuilder().setDescription(
            """
                You have selected to appeal for a **""" + type.getCleanName() + """
                **
                This punishment has an additional menu. Please tell us what guild you'd like to appeal for!
                """).build()).setActionRow(menu.build()).queue();
    }


    private void createAppealChannel(String[] value, StringSelectInteraction reply, Guild g, User user) {
        Category c = g != null ? g.getCategoryById("834325263240003595") : null;
        if (c == null) {
            reply.editMessage("Category not found.").queue();
            return;
        }

        String roles = value[1];
        String type = value[2];
        AppealType appealTypeDeclare = AppealType.fromName(type);
        if (appealTypeDeclare == null) appealTypeDeclare = AppealType.OTHER;
        final AppealType appealType = appealTypeDeclare;
        VerificationEntity ve = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(user.getId());

        if (ve == null) {
            reply.editMessage("You are not verified in any database, please contact a moderator and send them a screenshot of this message.").queue();
            return;
        }

        reply.deferEdit().submit().thenAccept(
            newReply -> {

                boolean canAppeal = checkIfCanAppeal(type, roles, ve, g);
                boolean isBotAdmin = avaire.getBotAdmins().getUserById(user.getId()).isGlobalAdmin();

                if (!isBotAdmin) {
                    if (!canAppeal) {
                        newReply.editOriginalEmbeds(new EmbedBuilder().setColor(appealType.getColor()).setDescription("You may not appeal with `" + roles + "` for `" + appealType.getCleanName() + "`. You either don't have this punishment or you have a punishment that overrides others (**Example**: `A trelloban overrides a global-ban`, `A globalban overrides a group discord ban` and so on. Contact a PIA Moderator if you believe this is a mistake.").build()).setComponents(Collections.emptyList()).queue();
                        return;
                    }
                }

                String name = type + "-" + roles + "-" + RandomUtil.generateString(5);
                c.createTextChannel(name).setTopic(type.toLowerCase() + " - " + user.getId() + " - " + roles + " - OPEN")
                    .addMemberPermissionOverride(user.getIdLong(), Permission.VIEW_CHANNEL.getRawValue(), 0L)
                    .addRolePermissionOverride(getAppealRole(roles, g).getIdLong(), Permission.VIEW_CHANNEL.getRawValue(), 0L).submit()
                    .thenCompose((chan) -> chan.sendMessage(user.getAsMention())
                        .setEmbeds(new PlaceholderMessage(new EmbedBuilder().setColor(appealType.getColor()),
                            """
                                We have created an appeal channel for your :appeal appeal!
                                Below this embed there will be a button for you to answer some questions about why we should accept your appeal.
                                                    
                                Please click this button and respond within 24 hours, otherwise we will close your appeal.
                                """
                        ).set("appeal", appealType.getCleanName())
                            .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                            .setTitle("Pinewood - Appeal System")
                            .setThumbnail(appealType.getEmoteImage())
                            .buildEmbed())
                        .setActionRow(Button.primary("questions-respond", "Click this button to obtain the questions.").asEnabled())
                        .submit())
                    .thenCompose((s) -> newReply.editOriginalEmbeds(new EmbedBuilder().setDescription("Your appeal channel has been created in " + s.getChannel().getAsMention() + "!").build())
                        .setComponents(Collections.emptyList()).submit())
                    .thenCompose((s) -> getTextChannelByRole(roles, g)
                        .sendMessageEmbeds(new PlaceholderMessage(new EmbedBuilder().setColor(appealType.getColor()),
                            """
                                ***Logged Info***:
                                **`User`**: :userMention
                                **`Type`**: :appeal - (:emote)
                                """)
                            .set("userMention", user.getAsMention())
                            .set("appeal", appealType.getCleanName())
                            .set("emote", appealType.getEmote())
                            .setThumbnail(appealType.getEmoteImage())
                            .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                            .setAuthor(user.getAsTag() + " - Appeal System", null, user.getEffectiveAvatarUrl())
                            .buildEmbed()).submit())
                    .whenComplete((s, error) -> {
                        if (error != null) error.printStackTrace();
                    });
            }
        );
    }

    private void createDeletionChannel(String[] value, StringSelectInteraction reply, Guild g, User user) {
        Category c = g != null ? g.getCategoryById("1001914491929370765") : null;
        if (c == null) {
            reply.editMessage("Category not found.").queue();
            return;
        }

        String roles = value[1];
        String type = value[2];
        AppealType appealTypeDeclare = AppealType.fromName(type);
        if (appealTypeDeclare == null) appealTypeDeclare = AppealType.OTHER;
        final AppealType delType = appealTypeDeclare;
        VerificationEntity ve = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(user.getId());

        if (ve == null) {
            reply.editMessage("You are not verified in any database, please contact a moderator and send them a screenshot of this message.").queue();
            return;
        }

        InteractionHook newReply = reply.deferEdit().complete();
        boolean canAppeal = checkIfCanAppeal(type, roles, ve, g);

        if (!canAppeal) {
            newReply.editOriginal("According to our database, you have some form of punishment preventing the deletion of your data. You may not request your data to be deleted.").queue();
            return;
        }

        String name = type + "-" + roles + "-" + RandomUtil.generateString(5);
        c.createTextChannel(name).setTopic(type.toLowerCase() + " - " + user.getId() + " - " + roles + " - OPEN")
            .addMemberPermissionOverride(user.getIdLong(), Permission.VIEW_CHANNEL.getRawValue(), 0L)
            .addRolePermissionOverride(getAppealRole(roles, g).getIdLong(), Permission.VIEW_CHANNEL.getRawValue(), 0L).submit()
            .thenCompose((chan) -> chan.sendMessage(user.getAsMention())
                .setEmbeds(new PlaceholderMessage(new EmbedBuilder().setColor(delType.getColor()),
                    """
                        We have created an data deletion request channel for your account!
                        The facilitator handling your request will contact you soon, do not ping them.
                        """
                ).set("appeal", delType.getCleanName())
                    .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                    .setTitle("Pinewood - Deletion Request System")
                    .setThumbnail(delType.getEmoteImage())
                    .buildEmbed())
                .submit())
            .thenCompose((s) -> newReply.editOriginalEmbeds(new EmbedBuilder().setDescription("Your data deletion channel has been created in " + s.getChannel().getAsMention() + "!").build())
                .setComponents(Collections.emptyList()).submit())
            .thenCompose((s) -> getTextChannelByRole(roles, g)
                .sendMessageEmbeds(new PlaceholderMessage(new EmbedBuilder().setColor(delType.getColor()),
                    """
                        ***Logged Info***:
                        **`User`**: :userMention
                        **`Type`**: :appeal - (:emote)
                        """)
                    .set("userMention", user.getAsMention())
                    .set("appeal", delType.getCleanName())
                    .set("emote", delType.getEmote())
                    .setThumbnail(delType.getEmoteImage())
                    .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                    .setAuthor(user.getAsTag() + " - Deletion Request System", null, user.getEffectiveAvatarUrl())
                    .buildEmbed()).submit());
    }

    private boolean checkIfCanAppeal(String type, String group, VerificationEntity ve, Guild g) {
        GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, g);

        boolean isGlobalBanned = avaire.getGlobalPunishmentManager().isGlobalBanned(settings.getMainGroupId(), String.valueOf(ve.getDiscordId()));
        boolean isGameBanned = avaire.getGlobalPunishmentManager().isRobloxGlobalBanned(settings.getMainGroupId(), ve.getRobloxId());

        HashMap <Long, List <TrellobanLabels>> trellobans = avaire.getRobloxAPIManager().getKronosManager().getTrelloBans();
        boolean isTrelloBanned = trellobans.containsKey(ve.getRobloxId());
        boolean canAppealTrello = isTrelloBanned && trellobans.get(ve.getRobloxId()).stream().noneMatch(card -> card.equals(TrellobanLabels.BLACKLISTED_WITHOUT_APPEAL));


        return switch (type.toLowerCase()) {
            case "trelloban" -> canAppealTrello;
            case "globalban" -> isGlobalBanned && !isTrelloBanned;
            case "gameban" -> isGameBanned && !isTrelloBanned;
            case "groupblacklist" ->
                getBlacklistByShortname(group).contains(ve.getRobloxId()) && !isGameBanned && !isGlobalBanned && !isTrelloBanned;
            case "groupdiscordban" ->
                /*group.equals("OTHER") ? isOtherGuildBanned(ve) : isBannedFromShortname(group, ve) && */
                !isGameBanned && !isGlobalBanned && !isTrelloBanned && !getBlacklistByShortname(group).contains(ve.getRobloxId());
            case "groupranklock" ->
                avaire.getRobloxAPIManager().getKronosManager().isRanklocked(ve.getRobloxId(), group.toLowerCase()) &&
                    !isGlobalBanned && !isGameBanned && !isTrelloBanned && !getBlacklistByShortname(group).contains(ve.getRobloxId());
            case "deletion" -> !isGlobalBanned
                && !isGameBanned
                && !isTrelloBanned
                && !avaire.getBlacklistManager().isAnyBlacklisted(ve.getRobloxId())
                && !avaire.getRobloxAPIManager().getKronosManager().hasNegativePointsAnywhere(ve.getRobloxId())
                && !avaire.getRobloxAPIManager().getKronosManager().hasRanklockAnywhere(ve.getRobloxId());
            case "weaponban" -> !isGameBanned
                && !isTrelloBanned;
            default -> !isTrelloBanned;
        };
    }

    private boolean isOtherGuildBanned(VerificationEntity ve) {
        boolean kddBanned = avaire.getShardManager().getGuildById("791168471093870622").retrieveBanList().complete().stream().anyMatch(k -> k.getUser().getIdLong() == ve.getDiscordId());
        boolean pbBanned = avaire.getShardManager().getGuildById("371062894315569173").retrieveBanList().complete().stream().anyMatch(k -> k.getUser().getIdLong() == ve.getDiscordId());
        boolean pbqaBanned = avaire.getShardManager().getGuildById("791168471093870622").retrieveBanList().complete().stream().anyMatch(k -> k.getUser().getIdLong() == ve.getDiscordId());

        return kddBanned || pbBanned || pbqaBanned;
    }

    private boolean isBannedFromShortname(String group, VerificationEntity ve) {
        return switch (group) {
            case "PBST" ->
                avaire.getShardManager().getGuildById("438134543837560832").retrieveBanList().complete().stream().anyMatch(k -> k.getUser().getIdLong() == ve.getDiscordId());
            case "PET" ->
                avaire.getShardManager().getGuildById("436670173777362944").retrieveBanList().complete().stream().anyMatch(k -> k.getUser().getIdLong() == ve.getDiscordId());
            case "TMS" ->
                avaire.getShardManager().getGuildById("572104809973415943").retrieveBanList().complete().stream().anyMatch(k -> k.getUser().getIdLong() == ve.getDiscordId());
            case "PBM" ->
                avaire.getShardManager().getGuildById("498476405160673286").retrieveBanList().complete().stream().anyMatch(k -> k.getUser().getIdLong() == ve.getDiscordId());
            default -> false;
        };
    }

    private List <Long> getBlacklistByShortname(String group) {
        return switch (group) {
            case "PBST" -> avaire.getBlacklistManager().getPBSTBlacklist();
            case "PET" -> avaire.getBlacklistManager().getPETBlacklist();
            case "TMS" -> avaire.getBlacklistManager().getTMSBlacklist();
            case "PBM" -> avaire.getBlacklistManager().getPBMBlacklist();
            default -> Collections.emptyList();
        };
    }

    private IPermissionHolder getAppealRole(String roles, Guild guild) {
        return switch (roles) {
            case "PBST" -> guild.getRoleById("750472575007588384");
            case "PET" -> guild.getRoleById("750472631987339264");
            case "TMS" -> guild.getRoleById("750472599041212426");
            case "PBM" -> guild.getRoleById("750479715344842832");
            case "RAID" -> guild.getRoleById("998362055398658179");
            default -> guild.getRoleById("750472556699582565");
        };
    }

    private TextChannel getTextChannelByRole(String roles, Guild guild) {
        return switch (roles) {
            case "PBST" -> guild.getTextChannelById("998324969320763513");
            case "PET" -> guild.getTextChannelById("998325012014571650");
            case "TMS" -> guild.getTextChannelById("998324875968126986");
            case "PBM" -> guild.getTextChannelById("998325057782808718");
            case "RAID" -> guild.getTextChannelById("1000872152137998457");
            default -> guild.getTextChannelById("998325283985825892");
        };
    }
}
