package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.contracts.appeals.AppealType;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
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
                .setFooter("Pinewood Intelligence Agency", "https://i.imgur.com/RAOn0OI.png")
                .setTitle("Pinewood - Appeal System")
                .set("username", event.getValue("username").getAsString())
                .set("whenOccur", event.getValue("whenOccur").getAsString())
                .set("whyPut", event.getValue("whyPut").getAsString())
                .set("whyRemove", event.getValue("whyRemove").getAsString())
                .set("prevention", event.getValue("prevention").getAsString())
                .buildEmbed()
            ).queue();
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
                        .addOption("Appeal for a Trello Ban",
                            "appeal:PIA:trelloban",
                            "You may appeal a trello-ban through this selection. ",
                            Emoji.fromMarkdown("<:trelloban:997965939792412823>"))
                        .addOption("Appeal for a Game Ban",
                            "appeal:PIA:gameban",
                            "You may appeal a game-ban through this selection. ",
                            Emoji.fromMarkdown("<:gameban:997965983153147955>"))
                        .addOption("Appeal for a Game Mute",
                            "appeal:PIA:gamemute",
                            "You may appeal a game-mute through this selection. ",
                            Emoji.fromMarkdown("<:gamemute:997966017143775252>"))
                        .addOption("Appeal for a Global ban",
                            "appeal:PIA:globalban",
                            "You may appeal a globalban through this selection. ",
                            Emoji.fromMarkdown("<:globalban:997966041277804546>"))
                        .addOption("Appeal for a Raid Blacklist",
                            "appeal:RAID:raidblacklist",
                            "You may appeal a raid-blacklist through this selection.",
                            Emoji.fromMarkdown("<:raidblacklist:997966060542230538>"))
                        .addOption("Appeal for a Group Discord Ban",
                            "guild:groupdiscordban",
                            "You may appeal a discord ban here.")
                        .addOption("Appeal for a Group Ranklock",
                            "guild:groupranklock",
                            "You may appeal a group ranklock here.")
                        .addOption("Appeal for a Group Blacklist",
                            "guild:groupblacklist",
                            "You may appeal a group blacklist here.")
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
        // guild:SUB:groupdiscordban appeal:PIA:globalban appeal:RAID:raidblacklist

        String roles = value[1];
        String type = value[2];
        AppealType appealType = AppealType.fromName(type);

        String name = event.getMember().getEffectiveName() + "-" + type;

        c.createTextChannel(name).submit()
            .thenCompose((channel) -> channel.upsertPermissionOverride(event.getMember()).setAllowed(Permission.VIEW_CHANNEL).submit())
            .thenCompose((override) -> override.getChannel().upsertPermissionOverride(getAppealRole(roles, event.getGuild())).setAllowed(Permission.VIEW_CHANNEL).submit())
            .thenCompose((chan) -> event.getGuild().getTextChannelById(chan.getChannel().getId()).sendMessage(event.getMember().getAsMention()).setEmbeds(new PlaceholderMessage(new EmbedBuilder(),
                    """
                    We have created an appeal channel for your :appeal appeal!
                    Below this embed there will be a button for you to answer some questions about why we should accept your appeal.
                    
                    Please click this button and respond within 24 hours, otherwise we will close your appeal.
                    """
                ).set("appeal", appealType != null ? appealType.getCleanName() : type)
                    .setFooter("Pinewood Intelligence Agency", "https://i.imgur.com/RAOn0OI.png")
                    .setTitle("Pinewood - Appeal System")
                    .buildEmbed())
                .setActionRow(Button.primary("questions-respond", "Click this button to obtain the questions.").asEnabled())
                .submit())
            .thenCompose((s) -> event.editMessageEmbeds(new EmbedBuilder().setDescription("Your appeal channel has been created in "+ s.getChannel().getAsMention()+"!").build())
                .setActionRows(Collections.emptyList()).submit())
            .whenComplete((s, error) -> {
                if (error != null) error.printStackTrace();

            });
    }

    private IPermissionHolder getAppealRole(String roles, Guild guild) {
        return switch (roles) {
            case "PBST" -> guild.getRoleById("750472575007588384");
            case "PET" -> guild.getRoleById("750472631987339264");
            case "TMS" -> guild.getRoleById("750472599041212426");
            case "PBM" -> guild.getRoleById("750479715344842832");
            default -> guild.getRoleById("750472556699582565");
        };
    }
}
