package com.avairebot.commands.roblox.verification;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.commands.Command;
import com.avairebot.contracts.verification.VerificationEntity;
import com.avairebot.contracts.verification.VerificationProviders;
import com.avairebot.database.collection.Collection;
import com.avairebot.database.query.QueryBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.json.JSONObject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.avairebot.utils.JsonReader.readJsonFromUrl;

public class ReverifyCommand extends Command {

    private static final String[] names = {"roblox", "pinewood", "activity-center", "security", "apple", "lemons", "duel", "acapella", "kronos", "mega", "miners"};


    public ReverifyCommand(AvaIre avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Reverify Command";
    }

    @Override
    public String getDescription() {
        return "Change your account being used for verification.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
                "`:command` - Set a different account on your profile for verification."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
                "`:command`"
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
                "isOfficialPinewoodGuild"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("reverify", "re-verify");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        context.makeInfo("<a:loading:742658561414266890> Checking external databases... <a:loading:742658561414266890>").queue(
                unverifiedMessage -> {
                    VerificationEntity rover = avaire.getRobloxAPIManager().getVerification().callUserFromRoverAPI(context.member.getId());
                    VerificationEntity bloxlink = avaire.getRobloxAPIManager().getVerification().callUserFromBloxlinkAPI(context.member.getId());
                    VerificationEntity pinewood = avaire.getRobloxAPIManager().getVerification().callUserFromDatabaseAPI(context.member.getId());

                    List<VerificationEntity> verificationEntities = new ArrayList<>();
                    if (rover != null) {
                        verificationEntities.add(rover);
                    }
                    if (bloxlink != null) {
                        verificationEntities.add(bloxlink);
                    }
                    if (pinewood != null) {
                        verificationEntities.add(bloxlink);
                    }


                    if (verificationEntities.size() < 1) {
                        unverifiedMessage.editMessageEmbeds(context.makeWarning("An account could not be found that's linked to your discord id. Please enter your Roblox name:").requestedBy(context).buildEmbed()).queue(unused -> {
                            avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class,
                                    message -> message.getMember().equals(context.getMember()) && message.getChannel().equals(context.channel),
                                    usernameMessage -> {
                                        verifyNewAccount(context, usernameMessage.getMessage().getContentRaw(), unverifiedMessage);
                                        usernameMessage.getMessage().delete().queue();
                                    }, 5, TimeUnit.MINUTES, () -> unverifiedMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 60 seconds, the reward system has been stopped.").buildEmbed()).queue());
                        });
                        return;
                    }

                    SelectionMenu.Builder menu = SelectionMenu.create("menu:provider-to-verify-with" + ":" + context.getMember().getId() + ":" + context.getMessage().getId())
                            .setPlaceholder("Select the verification provider!") // shows the placeholder indicating what this menu is for
                            .addOption("Verify a new account!", "verify-new-account", "Select this to verify a new account.", Emoji.fromMarkdown("<:PBST_GOD:857728071183237141>"))
                            .setRequiredRange(1, 1); // only one can be selected

                    for (VerificationEntity ve : verificationEntities) {
                        VerificationProviders provider = VerificationProviders.resolveProviderFromProvider(ve.getProvider());
                        if (provider != null) {
                            menu.addOption(ve.getRobloxUsername(), ve.getProvider(), "Verify with " + ve.getRobloxUsername() + " from " + provider.provider, Emoji.fromMarkdown(provider.emoji));
                        }
                    }

                    unverifiedMessage.editMessageEmbeds(context.makeSuccess("Found `" + verificationEntities.size() + "` providers with your account in their database, please select the provider you want to verify with!").requestedBy(context).buildEmbed())
                            .setActionRow(menu.build()).queue(menuSelection -> avaire.getWaiter().waitForEvent(SelectionMenuEvent.class,
                            interaction -> {
                                if (interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel)) {
                                    return true;
                                }
                                //interaction.deferReply().setEphemeral(true).flatMap(emp -> emp.sendMessage("Sorry, but you're not allowed to use this interaction unless you're `" + context.getMember().getEffectiveName() + "`")).queue();
                                return false;
                            },
                            providerSelect -> {
                                providerSelect.deferEdit().queue(k -> {
                                    if (providerSelect.getSelectedOptions() != null) {
                                        for (SelectOption so : providerSelect.getSelectedOptions()) {
                                            if (so.getValue().equals("verify-new-account")) {
                                                unverifiedMessage.editMessageEmbeds(context.makeWarning("You selected the option to verify with a new account\n**Please enter the Roblox name of said account**:").requestedBy(context).buildEmbed()).setActionRows(Collections.emptyList()).queue(unused -> {
                                                    avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class,
                                                            interaction -> interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel),
                                                            usernameMessage -> {
                                                                verifyNewAccount(context, usernameMessage.getMessage().getContentRaw(), unverifiedMessage);
                                                                usernameMessage.getMessage().delete().queue();
                                                            });
                                                });
                                                return;
                                            }
                                            if (so.getValue().equals("rover")) {
                                                assert rover != null;
                                                addAccountToDatabase(context, rover.getRobloxId(), rover.getRobloxUsername(), unverifiedMessage);
                                                return;
                                            }
                                            if (so.getValue().equals("bloxlink")) {
                                                assert bloxlink != null;
                                                addAccountToDatabase(context, bloxlink.getRobloxId(), bloxlink.getRobloxUsername(), unverifiedMessage);
                                                return;
                                            }
                                            if (so.getValue().equals("pinewood")) {
                                                assert pinewood != null;
                                                addAccountToDatabase(context, pinewood.getRobloxId(), pinewood.getRobloxUsername(), unverifiedMessage);
                                                return;
                                            }
                                        }
                                    }
                                });
                            }, 5, TimeUnit.MINUTES, () -> unverifiedMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 60 seconds, the reward system has been stopped.").buildEmbed()).queue()));

                }
        );
        return true;
    }


    private void verifyNewAccount(CommandMessage context, String robloxUsername, Message originalMessage) {
        Long robloxId = getRobloxId(robloxUsername);
        if (robloxId == null) {
            context.makeError("Verification failed. Username doesn't exist on roblox. (`:username`)").set("username", robloxUsername).queue();
            return;
        }


        SelectionMenu menu = SelectionMenu.create("menu:method-to-verify-with"  + ":" + context.getMember().getId() + ":" + context.getMessage().getId())
                .setPlaceholder("Select the verification method!") // shows the placeholder indicating what this menu is for
                .setRequiredRange(1, 1) // only one can be selected
                .addOption("In-game Verification", "game-verification", "Join a game on roblox to verify!", Emoji.fromUnicode("\uD83D\uDC68\u200D\uD83D\uDE80"))
                .addOption("Edit Description", "edit-description", "Add text to your profile description!", Emoji.fromMarkdown("<:roblox:863179377080401960>"))
                .build();

        originalMessage.editMessageEmbeds(context.makeInfo("Account was found on roblox, how would you like to verify?").requestedBy(context).buildEmbed())
                .setActionRow(menu).queue(m -> avaire.getWaiter().waitForEvent(SelectionMenuEvent.class,
                interaction -> {
                    if (interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel)) {
                        return true;
                    }
                    //interaction.deferReply().setEphemeral(true).flatMap(emp -> emp.sendMessage("Sorry, but you're not allowed to use this interaction unless you're `" + context.getMember().getEffectiveName() + "`")).queue();
                    return false;
                },
                accountSelect -> {
                    accountSelect.deferEdit().queue(k -> {
                        if (accountSelect.getSelectedOptions() != null) {
                            for (SelectOption so : accountSelect.getSelectedOptions()) {
                                if (so.getValue().equals("game-verification")) {
                                    originalMessage.editMessageEmbeds(context.makeError("This feature is still in progress, please try again later.").requestedBy(context).buildEmbed()).queue();
                                    return;
                                }

                                StringBuilder token = new StringBuilder();
                                for (int i = 0; i < 6; i++) {
                                    token.append(names[(int) (Math.random() * names.length)]).append(" ");
                                }
                                token.deleteCharAt(token.length() - 1);

                                if (so.getValue().equals("edit-description")) {
                                    Button b1 = Button.success("accept:" + originalMessage.getId(), "Check your status").withEmoji(Emoji.fromUnicode("✅"));
                                    Button b2 = Button.danger("reject:" + originalMessage.getId(), "Cancel verification").withEmoji(Emoji.fromUnicode("❌"));
                                    originalMessage.editMessageEmbeds(context.makeError("Please go to [your profile](https://www.roblox.com/users/:robloxId/profile) and edit your description!\n\nMake sure it contains the following text before confirming you`ve changed it!\n" +
                                            "```" + token + "```")
                                            .set("robloxId", robloxId)
                                            .setImage("https://i.imgur.com/VXoXcIS.png")
                                            .setThumbnail("https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + robloxId).requestedBy(context).buildEmbed())
                                            .setActionRow(b1.asEnabled(), b2.asEnabled())
                                            .queue(statusCheck -> avaire.getWaiter().waitForEvent(ButtonClickEvent.class, interaction -> {
                                                if (interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel)) {
                                                    return true;
                                                }
                                                //interaction.deferReply().setEphemeral(true).flatMap(emp -> emp.sendMessage("Sorry, but you're not allowed to use this interaction unless you're `" + context.getMember().getEffectiveName() + "`")).queue();
                                                return false;
                                            }, statusButton -> {
                                                statusButton.deferEdit().queue();
                                                if ("✅".equals(statusButton.getButton().getEmoji().getName())) {
                                                    String status = avaire.getRobloxAPIManager().getUserAPI().getUserStatus(robloxId);
                                                    if (status.contains(token)) {
                                                        addAccountToDatabase(context, robloxId, robloxUsername, originalMessage);
                                                    } else {
                                                        originalMessage.editMessageEmbeds(context.makeWarning("Your status does not contain the token, verification cancelled.").requestedBy(context).buildEmbed()).setActionRows(Collections.emptyList()).queue();
                                                    }
                                                    return;
                                                }
                                                originalMessage.editMessageEmbeds(context.makeWarning("System has been cancelled, if you want to verify again run the !verify command").requestedBy(context).buildEmbed()).queue();
                                            }, 5, TimeUnit.MINUTES, () -> originalMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 60 seconds, the reward system has been stopped.").buildEmbed()).queue()));
                                    return;
                                }
                            }
                        }
                    });
                }, 5, TimeUnit.MINUTES, () -> originalMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 60 seconds, the reward system has been stopped.").buildEmbed()).queue()));
    }

    private void addAccountToDatabase(CommandMessage context, Long robloxId, String robloxUsername, Message originalMessage) {
        try {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_DATABASE_TABLE_NAME);
            Collection accounts = qb.where("id", context.getMember().getId()).get();
            if (accounts.size() > 0) {
                qb.update(addAccount -> {
                    addAccount
                            .set("id", context.getMember().getId())
                            .set("robloxId", robloxId)
                            .set("username", robloxUsername);
                });
            } else {
                qb.insert(addAccount -> {
                    addAccount
                            .set("id", context.getMember().getId())
                            .set("robloxId", robloxId)
                            .set("username", robloxUsername);
                });
            }

            originalMessage.editMessageEmbeds(context.makeSuccess("Your profile has been verified and your account `:username` with id `:robloxId` has been linked to your discord account (`:id`). You will be verified on this discord in a few seconds.")
                    .set("username", robloxUsername).set("robloxId", robloxId).set("id", context.getMember().getId()).requestedBy(context).buildEmbed()).setActionRows(Collections.emptyList()).queue();
            avaire.getRobloxAPIManager().getVerification().verify(context, false);
        } catch (SQLException throwables) {
            originalMessage.editMessageEmbeds(context.makeError("Something went wrong adding your account to the database :(").requestedBy(context).buildEmbed()).setActionRows(Collections.emptyList()).queue();
        }
    }


    public Long getRobloxId(String un) {
        try {
            JSONObject json = readJsonFromUrl("https://api.roblox.com/users/get-by-username?username=" + un);
            return json.getLong("Id");
        } catch (Exception e) {
            return 0L;
        }
    }
}
