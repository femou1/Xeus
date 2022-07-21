package com.pinewoodbuilders.commands.roblox.verification;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.VerificationCommandContract;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.contracts.verification.VerificationProviders;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VerifyCommand extends VerificationCommandContract {

    public VerifyCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Verify Command";
    }

    @Override
    public String getDescription() {
        return "Verify yourself on the discord.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
                "`:command` - Verify yourself and update the cache."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
                "`:command`"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("verify");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification().fetchVerification(context.member.getId(), false, "pinewood");
        if (verificationEntity != null) {
            return avaire.getRobloxAPIManager().getVerification().verify(context);
        }
        return runVerificationProgress(context);

    }

    private boolean runVerificationProgress(CommandMessage context) {

        context.makeInfo("<a:loading:742658561414266890> You haven't verified with the Xeus verification database yet. Checking external databases... <a:loading:742658561414266890>").queue(
                unverifiedMessage -> {
                    VerificationEntity rover = avaire.getRobloxAPIManager().getVerification().callUserFromRoverAPI(context.member.getId());
                    VerificationEntity bloxlink = avaire.getRobloxAPIManager().getVerification().callUserFromBloxlinkAPI(context.member.getId());
                    VerificationEntity rowifi = avaire.getRobloxAPIManager().getVerification().callUserFromRoWifiAPI(context.member.getId());

                    List<VerificationEntity> verificationEntities = new ArrayList<>();
                    if (rover != null) {
                        verificationEntities.add(rover);
                    }
                    if (bloxlink != null) {
                        verificationEntities.add(bloxlink);
                    }

                    if (rowifi != null) {
                        verificationEntities.add(rowifi);
                    }

                    if (verificationEntities.size() < 1) {
                        unverifiedMessage.editMessageEmbeds(context.makeWarning("An account could not be found that's linked to your discord id. Please enter your Roblox name:").requestedBy(context).buildEmbed()).queue(unused -> {
                            avaire.getWaiter().waitForEvent(MessageReceivedEvent.class,
                                    message -> message.getMember() != null && message.getMember().equals(context.getMember()) && message.getChannel().equals(context.channel),
                                    usernameMessage -> {
                                        verifyNewAccount(context, usernameMessage.getMessage().getContentRaw(), unverifiedMessage);
                                        usernameMessage.getMessage().delete().queue();
                                    }, 5, TimeUnit.MINUTES, () -> unverifiedMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 5 minutes, the verification system has been stopped.").buildEmbed()).queue());
                        });
                        return;
                    }

                    SelectMenu.Builder menu = SelectMenu.create("menu:provider-to-verify-with" + ":" + context.getMember().getId() + ":" + context.getMessage().getId())
                            .setPlaceholder("Select the verification provider!") // shows the placeholder indicating what this menu is for
                            .addOption("Verify a new account!", "verify-new-account", "Select this to verify a new account.", Emoji.fromFormatted("<:PBST_GOD:857728071183237141>"))
                            .setRequiredRange(1, 1); // only one can be selected

                    for (VerificationEntity ve : verificationEntities) {
                        VerificationProviders provider = VerificationProviders.resolveProviderFromProvider(ve.getProvider());
                        if (provider != null) {
                            String username = ve.getRobloxUsername() != null ? ve.getRobloxUsername() : "Deleted";
                            menu.addOption(username, ve.getProvider() + ":" + username, "Verify with " + username + " from " + provider.provider, Emoji.fromFormatted(provider.emoji));
                        }
                    }

                    unverifiedMessage.editMessageEmbeds(context.makeSuccess("Found `" + verificationEntities.size() + "` providers with your account in their database, please select the provider you want to verify with! (By clicking on one of these providers, you allow us to store your discord ID and roblox ID within our database. We use this information to link your account to a discord account and to check if there are any infractions on past accounts)").requestedBy(context).buildEmbed())
                            .setActionRow(menu.build()).queue();
                    avaire.getWaiter().waitForEvent(SelectMenuInteractionEvent.class,
                            interaction -> interaction.getMember() != null && interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel) && interaction.getMessage().equals(unverifiedMessage),
                            providerSelect -> {
                                providerSelect.deferEdit().queue(k -> {
                                    providerSelect.getSelectedOptions();
                                    for (SelectOption so : providerSelect.getSelectedOptions()) {
                                        if (so.getValue().equals("verify-new-account")) {
                                            unverifiedMessage.editMessageEmbeds(context.makeWarning("You selected the option to verify with a new account\n**Please enter the Roblox name of said account**:")
                                                .requestedBy(context).buildEmbed())
                                                .setActionRows(Collections.emptyList()).queue(unused -> {
                                                avaire.getWaiter().waitForEvent(MessageReceivedEvent.class,
                                                    interact -> interact.getMember() != null && interact.getMember().equals(context.getMember()) && interact.getChannel().equals(context.channel),
                                                        usernameMessage -> {
                                                            verifyNewAccount(context, usernameMessage.getMessage().getContentRaw(), unverifiedMessage);
                                                            usernameMessage.getMessage().delete().queue();
                                                        });
                                            });
                                            return;
                                        }

                                        String provider = so.getValue().split(":")[0];
                                        String username = so.getValue().split(":")[1];

                                        if (provider.equals("rover")) {
                                            assert rover != null;
                                            addAccountToDatabase(context, rover.getRobloxId(), unverifiedMessage, username);
                                            return;
                                        }
                                        if (provider.equals("bloxlink")) {
                                            assert bloxlink != null;
                                            addAccountToDatabase(context, bloxlink.getRobloxId(), unverifiedMessage, username);
                                            return;
                                        }
                                        if (provider.equals("rowifi")) {
                                            assert bloxlink != null;
                                            addAccountToDatabase(context, rowifi.getRobloxId(), unverifiedMessage, username);
                                            return;
                                        }
                                    }
                                });
                            }, 5, TimeUnit.MINUTES, () -> unverifiedMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 5 minutes, the verification system has been stopped.").buildEmbed()).queue());
                }
        );
        return true;
    }

    private void verifyNewAccount(CommandMessage context, String robloxUsername, Message originalMessage) {
        long robloxId = getRobloxId(robloxUsername);
        if (robloxId == 0L) {
            context.makeError("Verification failed. Username doesn't exist on roblox or unable to find the username. (`:username`)").set("username", robloxUsername).queue();
            return;
        }


        SelectMenu menu = SelectMenu.create("menu:method-to-verify-with" + ":" + context.getMember().getId() + ":" + context.getMessage().getId())
                .setPlaceholder("Select the verification method!") // shows the placeholder indicating what this menu is for
                .setRequiredRange(1, 1) // only one can be selected
                .addOption("In-game Verification", "game-verification", "Join a game on roblox to verify!", Emoji.fromUnicode("\uD83D\uDC68\u200D\uD83D\uDE80"))
                .addOption("Edit Description", "edit-description", "Add text to your profile description!", Emoji.fromFormatted("<:roblox:863179377080401960>"))
                .build();

        originalMessage.editMessageEmbeds(context.makeInfo("Account was found on roblox, how would you like to verify?").requestedBy(context).buildEmbed())
                .setActionRow(menu).queue(m -> avaire.getWaiter().waitForEvent(SelectMenuInteractionEvent.class,
                interaction -> interaction.getMember() != null && interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel) && interaction.getMessage().equals(originalMessage),
                accountSelect -> {
                    accountSelect.deferEdit().queue(k -> {
                        if (accountSelect.getSelectedOptions() != null) {
                            for (SelectOption so : accountSelect.getSelectedOptions()) {
                                if (so.getValue().equals("game-verification")) {
                                    runGameVerification(context, originalMessage, robloxId);
                                    return;
                                }

                                if (so.getValue().equals("edit-description")) {
                                    runDescriptionVerification(context, originalMessage, robloxId);
                                    return;
                                }
                            }
                        }
                    });
                }, 5, TimeUnit.MINUTES, () -> originalMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 5 minutes, the verification system has been stopped.").buildEmbed()).queue()));
    }

    public long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return 0L;
        }
    }
}
