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
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import java.util.Collections;
import java.util.List;

public class AppealsServerEventAdapter extends EventAdapter {

    public AppealsServerEventAdapter(Xeus avaire) {
        super(avaire);
    }

    //appeal-questions
    public void onAppealModelInteractionEvent(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "appeal-questions" -> event.editMessage(event.getMember().getAsMention()).setEmbeds(new PlaceholderMessage(new EmbedBuilder(),
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
            ).setActionRows(Collections.emptyList()).queue();
        }
    }

    public void onAppealsSelectMenuInteractionEvent(SelectMenuInteractionEvent event) {
        String[] selection = event.getSelectedOptions().get(0).getValue().split(":");

        switch (selection[0].toLowerCase()) {
            case "guild" -> guildSelectionMenu(event, selection);
            case "appeal" -> createAppealChannel(event, selection);
        }
    }

    // start-appeal
    public void onAppealsButtonClickEvent(ButtonInteractionEvent event) {
        String id = event.getButton().getId();
        if (id == null) return;

        switch (id.toLowerCase()) {
            case "start-appeal" ->  event.deferReply().setEphemeral(true).addEmbeds(new EmbedBuilder().setDescription(
                """
                You have activated the Pinewood Appeal System. By continuing you agree to the following:
                
                ***__Miscellaneous Information__***
                **1**. If you do not do anything in your appeal for 24 hours, the ticket will be deleted.
                **2**. You cannot appeal negative points/marks in divisions, reach out to a leader in that division instead.
                **3**. Purchases of in-game content with Robux does not exempt you from the rules nor entitle you to a free unban. Rules are rules, and they apply to everyone.
                **4**. Your account is your responsibility, unless you have indisputable proof we will not accept any excuse about unauthorized account usage.
                **5**. If you are found to be using a fake account to appeal, you will be banned.
                **6**. Do not make appeals for no reason; if you do so multiple times, you will be removed from the server.
                
                __We have the full right to decide what to do with your appeal, being able to appeal is a privilege. If you cannot deal with this, you will be removed from the server.__
                """).build()).addActionRow(Button.primary("agree-rules",
                "I have read and approve of the rules listed in the embed above.")).queue();
            case "agree-rules" -> event.getInteraction().editMessageEmbeds(new EmbedBuilder().setDescription(
                """
                You have agreed to the rules.
                We're asking about your appeal now, what punishment would you like to appeal for?
                
                __Please read through the entire selection menu!!!__
                """).build()).setActionRow(
                    SelectMenu.create("punishment-selection")
                        .addOption("Appeal for a trello ban",
                            "appeal:PIA:trelloban",
                            "You may appeal a trello-ban through this selection. ",
                            Emoji.fromMarkdown("<:trelloban:997965939792412823>"))
                        .addOption("Appeal for a game ban",
                            "appeal:PIA:gameban",
                            "You may appeal a game-ban through this selection. ",
                            Emoji.fromMarkdown("<:gameban:997965983153147955>"))
                        .addOption("Appeal for a game mute",
                            "appeal:PIA:gamemute",
                            "You may appeal a game-mute through this selection. ",
                            Emoji.fromMarkdown("<:gamemute:997966017143775252>"))
                        .addOption("Appeal for a global ban",
                            "appeal:PIA:globalban",
                            "You may appeal a globalban through this selection. ",
                            Emoji.fromMarkdown("<:globalban:997966041277804546>"))
                        .addOption("Appeal for a raid blacklist",
                            "appeal:RAID:raidblacklist",
                            "You may appeal a raid-blacklist through this selection.",
                            Emoji.fromMarkdown("<:raidblacklist:997966060542230538>"))
                        .addOption("Appeal for a group discord ban",
                            "guild:groupdiscordban",
                            "You may appeal a discord ban here.",
                            Emoji.fromMarkdown("<:groupdiscordban:998332587447681135>"))
                        .addOption("Appeal for a group ranklock",
                            "guild:groupranklock",
                            "You may appeal a group ranklock here.",
                            Emoji.fromMarkdown("<:ranklock:998332416991170651>"))
                        .addOption("Appeal for a group blacklist",
                            "guild:groupblacklist",
                            "You may appeal a group blacklist here.",
                            Emoji.fromMarkdown("<:blacklist:998332444916858880>"))
                        .setRequiredRange(1, 1).build().asEnabled()).queue();
            case "questions-respond" -> event.replyModal(buildQuestionsModal(event)).queue();
        }
    }

    private Modal buildQuestionsModal(ButtonInteractionEvent event) {
        VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(event.getMember().getId());

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

    private void guildSelectionMenu(SelectMenuInteractionEvent event, String[] value) {
        AppealType type = AppealType.fromName(value[1]);
        if (type == null) {event.reply("Type not found.").setEphemeral(true).queue();return;}
        if (!type.isGuildSelect()) {event.reply("No guild select.").setEphemeral(true).queue();return;}
        SelectMenu.Builder menu = SelectMenu.create("user-selection")
            .addOption("Pinewood Builders Security Team",
                "appeal:PBST:" + type.getName(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromMarkdown("<:PBST:790431720297857024>"))
            .addOption("Pinewood Emergency Team",
                "appeal:PET:" + type.getName(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromMarkdown("<:PET:694389856071319593>"))
            .addOption("The Mayhem Syndicate",
                "appeal:TMS:" + type.getName(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromMarkdown("<:TMS:757737764417437827>"))
            .setRequiredRange(1, 1);

        if (!type.getName().equals("groupranklock")) {
            menu.addOption("Pinewood Builders Media",
                "appeal:PBM:" + type.getName(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromMarkdown("<:PBM:757736209333223434>"));
        }

        if (type.getName().equals("groupdiscordban")) {
            menu.addOption("Other discord ban",
                "appeal:PIA:" + type.getName(),
                "Appeal your (other) " + type.getCleanName() + " here.",
                Emoji.fromMarkdown("<:otherservers:997966085175386212>"));
        }



        event.editMessageEmbeds(new EmbedBuilder().setDescription(
            """
                You have selected to appeal for a **"""+ type.getCleanName() + """
                    **
                    This punishment has an additional menu. Please tell us what guild you'd like to appeal for!
                    """).build()).setActionRow(menu.build()).queue();
    }



    private void createAppealChannel(SelectMenuInteractionEvent event, String[] value) {
        Guild g = event.getGuild();
        Category c = g != null ? g.getCategoryById("834325263240003595") : null;
        if (c == null) {
            event.reply("Category not found.").setEphemeral(true).queue();
            return;
        }

        String roles = value[1];
        String type = value[2];
        AppealType appealType = AppealType.fromName(type);
        VerificationEntity ve = avaire.getRobloxAPIManager().getVerification().fetchInstantVerificationWithBackup(event.getMember().getId());

        if (!checkIfCanAppeal(type, roles, ve, g)) {
            event.editMessageEmbeds(new EmbedBuilder().setDescription("You may not appeal for " + appealType.getCleanName() + ". You either don't have this punishment, or something went wrong. Contact a PIA Moderator if you believe this is a mistake.").build()).setActionRows(Collections.emptyList()).queue();
            return;
        }

        String name = type + "-" + RandomUtil.generateString(5);

        c.createTextChannel(name).setTopic(type.toLowerCase() + " - " + event.getMember().getId() + " - " + roles + " - OPEN").submit()
            .thenCompose((channel) -> channel.upsertPermissionOverride(event.getMember()).setAllowed(Permission.VIEW_CHANNEL).submit())
            .thenCompose((override) -> override.getChannel().upsertPermissionOverride(getAppealRole(roles, event.getGuild())).setAllowed(Permission.VIEW_CHANNEL).submit())
            .thenCompose((chan) -> event.getGuild().getTextChannelById(chan.getChannel().getId()).sendMessage(event.getMember().getAsMention())
                .setEmbeds(new PlaceholderMessage(new EmbedBuilder(),
                    """
                    We have created an appeal channel for your :appeal appeal!
                    Below this embed there will be a button for you to answer some questions about why we should accept your appeal.
                    
                    Please click this button and respond within 24 hours, otherwise we will close your appeal.
                    """
                ).set("appeal", appealType != null ? appealType.getCleanName() : type)
                    .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                    .setTitle("Pinewood - Appeal System")
                    .setThumbnail(appealType.getEmoteImage())
                    .buildEmbed())
                .setActionRow(Button.primary("questions-respond", "Click this button to obtain the questions.").asEnabled())
                .submit())
            .thenCompose((s) -> event.editMessageEmbeds(new EmbedBuilder().setDescription("Your appeal channel has been created in "+ s.getChannel().getAsMention()+"!").build())
                .setActionRows(Collections.emptyList()).submit())
            .thenCompose((s) -> getTextChannelByRole(roles, event.getGuild())
                .sendMessageEmbeds(new PlaceholderMessage(new EmbedBuilder(),
                    """
                    ***Logged Info***:
                    **`User`**: :userMention
                    **`Type`**: :appeal - (:emote)
                    """)
                    .set("userMention", event.getMember().getAsMention())
                    .set("appeal", appealType != null ? appealType.getCleanName() : type)
                    .set("emote", appealType != null ? appealType.getEmote() : "")
                    .setThumbnail(appealType.getEmoteImage())
                    .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
                .setAuthor(event.getUser().getAsTag() + " - Appeal System", null, event.getUser().getEffectiveAvatarUrl())
                .buildEmbed()).submit())
            .whenComplete((s, error) -> {
                if (error != null) error.printStackTrace();

            });
    }

    private boolean checkIfCanAppeal(String type, String group, VerificationEntity ve, Guild g) {
        GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, g);
        return switch (type.toLowerCase()) {
            case "globalban" ->
                avaire.getGlobalPunishmentManager().isGlobalBanned(settings.getMainGroupId(), String.valueOf(ve.getDiscordId()));
            case "gameban" ->
                avaire.getGlobalPunishmentManager().isRobloxGlobalBanned(settings.getMainGroupId(), ve.getRobloxId());
            case "groupranklock" ->
                avaire.getRobloxAPIManager().getKronosManager().isRanklocked(ve.getRobloxId(), group.toLowerCase());
            case "groupblacklist" ->
                getBlacklistByShortname(group).contains(ve.getRobloxId());
            case "groupdiscordban" -> group.equals("OTHER") ? isOtherGuildBanned(ve) : getGuildByShortName(group).retrieveBanList().stream().filter(k -> k.getUser().getIdLong() == ve.getDiscordId()).toList().size() > 0;
            case "trelloban" ->
                avaire.getRobloxAPIManager().getKronosManager().getTrelloBans().containsKey(ve.getRobloxId()) &&
                    avaire.getRobloxAPIManager().getKronosManager().getTrelloBans().get(ve.getRobloxId()).stream().anyMatch(TrellobanLabels::isAppealable);
            default -> true;
        };
    }

    private boolean isOtherGuildBanned(VerificationEntity ve) {
        boolean kddBanned = avaire.getShardManager().getGuildById("791168471093870622").retrieveBanList().stream().filter(k -> k.getUser().getIdLong() == ve.getDiscordId()).toList().size() > 0;
        boolean pbBanned = avaire.getShardManager().getGuildById("371062894315569173").retrieveBanList().stream().filter(k -> k.getUser().getIdLong() == ve.getDiscordId()).toList().size() > 0;
        boolean pbqaBanned = avaire.getShardManager().getGuildById("791168471093870622").retrieveBanList().stream().filter(k -> k.getUser().getIdLong() == ve.getDiscordId()).toList().size() > 0;

        return kddBanned || pbBanned || pbqaBanned;
    }

    private Guild getGuildByShortName(String group) {
        return switch (group) {
            case "PBST" -> avaire.getShardManager().getGuildById("438134543837560832");
            case "PET" -> avaire.getShardManager().getGuildById("436670173777362944");
            case "TMS" -> avaire.getShardManager().getGuildById("572104809973415943");
            case "PBM" -> avaire.getShardManager().getGuildById("498476405160673286");
            default -> null;
        };
    }

    private List<Long> getBlacklistByShortname(String group) {
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
            default -> guild.getTextChannelById("998325283985825892");
        };
    }
}
