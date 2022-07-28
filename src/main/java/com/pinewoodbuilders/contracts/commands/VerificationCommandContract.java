package com.pinewoodbuilders.contracts.commands;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.contracts.verification.VerificationResult;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.utilities.RandomUtil;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@CacheFingerprint(name = "roblox-verification-command")
public abstract class VerificationCommandContract extends Command {

    private static final String[] names = {"roblox", "pinewood", "activity-center", "security", "apple", "lemons", "duel", "acapella", "kronos", "mega", "miners"};
    private final String game_link = "https://www.roblox.com/games/4966946553/Xeus-Verification-Place";

    /**
     * Creates the given command instance by calling {@link #VerificationCommandContract(Xeus, boolean)} with allowDM set to true.
     *
     * @param avaire The Xeus class instance.
     */
    public VerificationCommandContract(Xeus avaire) {
        this(avaire, true);
    }

    /**
     * Creates the given command instance with the given
     * Xeus instance and the allowDM settings.
     *
     * @param avaire  The Xeus class instance.
     * @param allowDM Determines if the command can be used in DMs.
     */
    public VerificationCommandContract(Xeus avaire, boolean allowDM) {
        super(avaire, allowDM);
    }

    /**
     * This method is used to generate a 5 word code to put on your description.,
     *
     * @return Random code of 5 words (roblox safe)
     */
    protected String generateToken() {
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            token.append(names[(int) (Math.random() * names.length)]).append(" ");
        }
        token.deleteCharAt(token.length() - 1);
        return token.toString();
    }


    /**
     * @param context         {@link CommandMessage} from the command
     * @param robloxId        The roblox ID for the user you want to add to the database
     * @param originalMessage The {@link Message} object for editing the message when going to the next step.
     */
    public void addAccountToDatabase(CommandMessage context, Long robloxId, Message originalMessage, String username) {
        try {
            QueryBuilder qb = avaire.getDatabase().newQueryBuilder(Constants.VERIFICATION_DATABASE_TABLE_NAME);
            Collection accounts = qb.where("id", context.getMember().getId()).get();
            //Collection mainAccount = qb.where("main", "1").get();

            if (accounts.size() > 0) {
                qb.update(addAccount -> {
                    addAccount
                        .set("robloxId", robloxId);
                });
            } else {
                qb.insert(addAccount -> {
                    addAccount
                        .set("id", context.getMember().getId())
                        .set("robloxId", robloxId);
                });
            }


            VerificationResult vr = avaire.getRobloxAPIManager().getVerification().verify(context.getGuildSettingsTransformer(), context.member, context.guild, false);
            MessageEmbed embed1 = context.makeSuccess("Your profile has been verified and your account `:username` with id `:robloxId` has been linked to your discord account (`:id`). You will be verified on this discord in a few seconds.")
                .set("username", username).set("robloxId", robloxId).set("id", context.getMember().getId()).buildEmbed();

            MessageEmbed embed2 = vr.isSuccess() ? context.makeSuccess(vr.getMessage()).setTitle("Verification success!").setImage(getImageFromVerificationEntity(vr.getVerificationEntity())).requestedBy(context).buildEmbed() :
                context.makeError("Something went wrong during verification of your account..." + vr.getMessage()).buildEmbed();


            originalMessage.editMessageEmbeds(embed1, embed2).setActionRows(Collections.emptyList()).queue();



        } catch (SQLException throwables) {
            originalMessage.editMessageEmbeds(context.makeError("Something went wrong adding your account to the database :(").requestedBy(context).buildEmbed()).setActionRows(Collections.emptyList()).queue();
            throwables.printStackTrace();
        }
    }

    private String getImageFromVerificationEntity(VerificationEntity ve) {
        if (ve == null) {
            return null;
        }
        return "https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + ve.getRobloxId();
    }

    public void runGameVerification(CommandMessage context, Message originalMessage, Long robloxId) {
        String verificationCode = RandomUtil.generateString(16);
        HashMap<Long, String> verification = avaire.getRobloxAPIManager().getVerification().getInVerification();
        if (verification.containsKey(robloxId)) {
            originalMessage.editMessageEmbeds(context.makeError("This account is already running an in game verification, please try again in 5 minutes.").buildEmbed()).queue();
            return;
        }
        verification.put(robloxId, context.getAuthor().getId() + ":" + verificationCode);
        originalMessage.editMessageEmbeds(context.makeInfo("[Please join this game](" + game_link + "), and click on `Yes` to verify your account. When you've verified the confirmation, please click the :white_check_mark: button.").setImage("https://i.imgur.com/63XmSe7.png").buildEmbed())
                .setActionRow(
                        Button.success("confirm-verify:" + originalMessage.getId(), "I've clicked yes!").withEmoji(Emoji.fromUnicode("✅")).asEnabled(),
                        Button.danger("confirm-reject:" + originalMessage.getId(), "I don't wanna confirm anymore.").withEmoji(Emoji.fromUnicode("❌")).asEnabled(),
                        Button.link(game_link, "Join this game to verify").withEmoji(Emoji.fromUnicode("\uD83D\uDD17")).asEnabled())
                .queue(verifyMessage -> {
                    avaire.getWaiter().waitForEvent(ButtonInteractionEvent.class,
                            interaction -> interaction.getMember() != null && interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel),
                            action -> {
                                action.deferEdit().queue();
                                switch (action.getButton().getEmoji().getName()) {
                                    case "✅":
                                        if (verification.containsKey(robloxId)) {
                                            String discordId = verification.get(robloxId).split(":")[0];
                                            String verificationId = verification.get(robloxId).split(":")[1];

                                            if (verificationId.equals("verified") && discordId.equals(context.getAuthor().getId())) {
                                                verification.remove(robloxId);
                                                addAccountToDatabase(context, robloxId, originalMessage, avaire.getRobloxAPIManager().getUserAPI().getUsername(robloxId));
                                            } else {
                                                originalMessage.editMessageEmbeds(context.makeWarning("Verification has not been confirmed, verification cancelled.").buildEmbed()).setActionRows(Collections.emptyList()).queue();;
                                                verification.remove(robloxId);
                                            }
                                            break;
                                        } else {
                                            originalMessage.editMessageEmbeds(context.makeWarning("Verification code has disappeared. Try again later").buildEmbed()).setActionRows(Collections.emptyList()).queue();;
                                        }
                                        break;
                                    case "❌":
                                        verification.remove(robloxId);
                                        originalMessage.editMessageEmbeds(context.makeWarning("Cancelled the verification (User action)...").buildEmbed()).setActionRows(Collections.emptyList()).queue();;
                                        break;
                                    default:
                                        verification.remove(robloxId);
                                        originalMessage.editMessageEmbeds(context.makeWarning("Cancelled the verification (Invalid Emoji)...").buildEmbed()).setActionRows(Collections.emptyList()).queue();;
                                }
                            }, 5, TimeUnit.MINUTES, () -> {
                                verification.remove(robloxId);
                                originalMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 5 minutes, the verification system has been stopped.").buildEmbed()).queue();;
                            });
                });

    }

    public void runDescriptionVerification(CommandMessage context, Message originalMessage, Long robloxId) {
        String token = generateToken();
        Button b1 = Button.success("check:" + originalMessage.getId(), "Check your status").withEmoji(Emoji.fromUnicode("✅"));
        Button b2 = Button.danger("cancel:" + originalMessage.getId(), "Cancel verification").withEmoji(Emoji.fromUnicode("❌"));
        originalMessage.editMessageEmbeds(context.makeError("Please go to [your profile](https://www.roblox.com/users/:robloxId/profile) and edit your description!\n\nMake sure it contains the following text before confirming you`ve changed it!\n" +
                "```" + token + "```")
                .set("robloxId", robloxId)
                .setImage("https://i.imgur.com/VXoXcIS.png")
                .setThumbnail("https://www.roblox.com/Thumbs/Avatar.ashx?x=150&y=150&Format=Png&userid=" + robloxId).requestedBy(context).buildEmbed())
                .setActionRow(b1.asEnabled(), b2.asEnabled())
                .queue(statusCheck -> avaire.getWaiter().waitForEvent(ButtonInteractionEvent.class,
                        interaction -> interaction.getMember() != null && interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel)
                        , statusButton -> {
                            statusButton.deferEdit().queue();
                        switch (statusButton.getButton().getEmoji().getName()) {
                            case "✅" -> {
                                String status = avaire.getRobloxAPIManager().getUserAPI().getUserStatus(robloxId);
                                if (status != null) {
                                    if (status.contains(token)) {
                                        addAccountToDatabase(context, robloxId, originalMessage, avaire.getRobloxAPIManager().getUserAPI().getUsername(robloxId));
                                    } else {
                                        originalMessage.editMessageEmbeds(context.makeWarning("Your status does not contain the token, verification cancelled.").requestedBy(context).buildEmbed()).setActionRows(Collections.emptyList()).queue();
                                    }
                                } else {
                                    originalMessage.editMessageEmbeds(context.makeWarning("Status is empty, verification cancelled.").requestedBy(context).buildEmbed()).setActionRows(Collections.emptyList()).queue();
                                }
                                return;
                            }
                        }
                            originalMessage.editMessageEmbeds(context.makeWarning("System has been cancelled, if you want to verify again run the !verify command").requestedBy(context).buildEmbed()).queue();
                        }, 5, TimeUnit.MINUTES, () -> originalMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 5 minutes, the verification system has been stopped.").buildEmbed()).queue()));
    }

}
