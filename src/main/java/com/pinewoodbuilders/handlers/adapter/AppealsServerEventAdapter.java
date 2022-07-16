package com.pinewoodbuilders.handlers.adapter;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.appeals.AppealType;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;

public class AppealsServerEventAdapter extends EventAdapter {

    public AppealsServerEventAdapter(Xeus avaire) {
        super(avaire);
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
                **1**. If you do not post anything in your appeal, after an hour the ticket will be deleted.
                **2**. You cannot appeal negative points/marks in divisions, reach out to a leader in that division instead.
                **3**. Purchases of in-game content with Robux does not exempt you from the rules nor entitle you to a free unban. Rules are rules, and they apply to everyone.
                **4**. Your account is your responsibility, unless you have indisputable proof we will not accept any excuse about unauthorized account usage.
                **5**. If you are found to be using a fake account to appeal, you will be banned.
                **6**. Do not make appeals for no reason; if you do so multiple times, you will be removed from the server.
                
                __We have the full right to decide what to do with your appeal, being able to appeal is a privilege. If you cannot deal with this, you will be removed from the server.__
                """).build()).addActionRow(Button.primary("agree-rules",
                "I have read and approve of the rules listed in the embed above.")).queue();
            case "agree-rules" -> event.deferReply().setEphemeral(true).addEmbeds(new EmbedBuilder().setDescription(
                """
                You have agreed to the rules.
                We're asking about your appeal now, what punishment would you like to appeal for?
                
                __Please read through the entire selection menu!!!__
                """).build()).addActionRow(
                    SelectMenu.create("punishment-selection")
                        .addOption("Appeal for a Trello Ban",
                            "trelloban",
                            "You may appeal a trello-ban through this selection. ",
                            Emoji.fromMarkdown("<:trelloban:997965939792412823>"))
                        .addOption("Appeal for a Game Ban",
                            "gameban",
                            "You may appeal a game-ban through this selection. ",
                            Emoji.fromMarkdown("<:gameban:997965983153147955>"))
                        .addOption("Appeal for a Game Mute",
                            "gamemute",
                            "You may appeal a game-mute through this selection. ",
                            Emoji.fromMarkdown("<:gamemute:997966017143775252>"))
                        .addOption("Appeal for a Global ban",
                            "globalban",
                            "You may appeal a globalban through this selection. ",
                            Emoji.fromMarkdown("<:globalban:997966041277804546>"))
                        .addOption("Appeal for a Raid Blacklist",
                            "raidblacklist",
                            "You may appeal a raid-blacklist through this selection.",
                            Emoji.fromMarkdown("<:raidblacklist:997966060542230538>"))
                        .addOption("Appeal for a Group Discord Ban",
                            "groupdiscordban",
                            "You may appeal a discord ban here.")
                        .addOption("Appeal for a Group Ranklock",
                            "groupranklock",
                            "You may appeal a group ranklock here.")
                        .addOption("Appeal for a Group Blacklist",
                            "groupblacklist",
                            "You may appeal a group blacklist here.")
                        .setRequiredRange(1, 1).build().asEnabled()).queue();
        }
    }

    private void guildSelectionMenu(SelectMenuInteractionEvent event, String value) {

        AppealType type = AppealType.fromName(value);
        if (type == null) {event.reply("Type not found.").setEphemeral(true).queue();return;}
        if (!type.isGuildSelect()) {event.reply("No guild select.").setEphemeral(true).queue();return;}
        event.deferReply().setEphemeral(true).addEmbeds(new EmbedBuilder().setDescription(
                """
                    You have selected to appeal for a **"""+ type.getCleanName() + """
                    **
                    This punishment has an additional menu connected to itself. Please tell us what guild you'd like to appeal for!
                    """).build()).addActionRow(SelectMenu.create("user-selection")
            .addOption("Pinewood Builders Security Team",
                "438134543837560832:" + type.getName() + ":" + event.getUser().getId(),
                "Appeal your " + type.getCleanName() + " here.",
                Emoji.fromMarkdown("<:PBST:790431720297857024>"))

            .setRequiredRange(1, 1).build()).queue();
    }

    public void onAppealsSelectMenuInteractionEvent(SelectMenuInteractionEvent event) {
        String value = event.getSelectedOptions().get(0).getValue();

        switch (value.toLowerCase()) {
            case "groupblacklist", "groupranklock", "groupdiscordban" -> guildSelectionMenu(event, value);
        }
    }
}
