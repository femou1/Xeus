package com.pinewoodbuilders.commands.fun;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.commands.CommandPriority;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.factories.RequestFactory;
import com.pinewoodbuilders.modlog.Modlog;
import com.pinewoodbuilders.modlog.ModlogAction;
import com.pinewoodbuilders.modlog.ModlogType;
import com.pinewoodbuilders.onwatch.onwatchlog.OnWatchAction;
import com.pinewoodbuilders.onwatch.onwatchlog.OnWatchType;
import com.pinewoodbuilders.onwatch.onwatchlog.OnWatchlog;
import com.pinewoodbuilders.requests.Response;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.RoleUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import okhttp3.ResponseBody;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class NineteenEightyFourCommand extends Command {

    public NineteenEightyFourCommand(Xeus avaire) {
        super(avaire, false);
    }

    @Override
    public String getName() {
        return "Nineteen Eighty-Four (1984)";
    }

    @Override
    public String getDescription() {
        return "Today we celebrate the first glorious anniversary of the Pinewood Builders Inc.\nWe have created for the first time in all history a garden of pure ideology, where each worker may bloom, secure from the pests of any contradictory true thoughts.\n" +
            "\n" +
            "Our Unification of Thoughts is more powerful a weapon than any fleet or army on earth.\n" +
            "\n" +
            "We are one people, with one will, one resolve, one cause.\n" +
            "\n" +
            "Our enemies shall talk themselves to death and we will bury them with their own confusion.\n" +
            "***We shall prevail!***";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList("`:command` - WE SHALL PREVAIL");
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("1984", "NineteenEightyFour");
    }

    @Override
    public CommandPriority getCommandPriority() {
        return CommandPriority.HIDDEN;
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (!context.getGuild().getId().equals("572104809973415943")) {
            return false;
        }
        RequestFactory.makeGET("https://media1.tenor.com/images/ae10d37f424aedb3c36027346d4adf62/tenor.gif")
            .send((Consumer <Response>) response -> {
                ResponseBody body = response.getResponse().body();

                if (body == null) {
                    return;
                }

                context.getChannel().sendMessage(
                    new MessageBuilder().setEmbeds(
                        new EmbedBuilder()
                            .setImage("attachment://1984.gif")
                            .setDescription("We will prevail!!!")
                            .setFooter("0_0", null)
                            .build()
                    ).build()).addFile(body.byteStream(),
                    "1984.gif"
                ).queue();
            });

        return rankUserWithOnWatchAndMuted(context);
    }

    private boolean rankUserWithOnWatchAndMuted(CommandMessage context) {
        if (!rankUserOnWatch(context)) {
            return false;
        }

        return rankUserMuted(context);
    }

    private boolean rankUserMuted(CommandMessage context) {
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "Server settings could not be loaded");
        }

        if (transformer.getModlog() == null) {
            return sendErrorMessage(context, "Modlog channel has not been set...");
        }

        if (transformer.getMuteRole() == null) {
            return sendErrorMessage(context, "Mute role is not set...");
        }

        Role muteRole = context.getGuild().getRoleById(transformer.getMuteRole());
        if (muteRole == null) {
            return sendErrorMessage(context, "Role doesn't exist...");
        }

        if (!context.getGuild().getSelfMember().canInteract(muteRole)) {
            return sendErrorMessage(context, "I can't interact with the mute role...");
        }

        User user = context.getAuthor();
        if (user == null) {
            return sendErrorMessage(context, "You don't exist. Hmm?");
        }

        /*if (userHasHigherRole(user, context.getMember())) {
            return sendErrorMessage(context, "I cannot rank you.");
        }*/

        Carbon expiresAt = Carbon.now().addMinutes(5);
        if (expiresAt != null && expiresAt.copy().subSeconds(61).isPast()) {
            return sendErrorMessage(context, context.i18n("invalidTimeGiven"));
        }

        String reason = generateMessage();
        ModlogType type = expiresAt == null ? ModlogType.MUTE : ModlogType.TEMP_MUTE;

        final Carbon finalExpiresAt = expiresAt;
        context.getGuild().addRoleToMember(
            context.getGuild().getMember(user), muteRole
        ).reason(reason).queue(aVoid -> {
            ModlogAction modlogAction = new ModlogAction(
                type, context.getAuthor(), user,
                finalExpiresAt != null
                    ? finalExpiresAt.toDayDateTimeString() + " (" + finalExpiresAt.diffForHumans(true) + ")" + "\n" + reason
                    : "\n" + reason
            );

            String caseId = Modlog.log(avaire, context, modlogAction);
            Modlog.notifyUser(user, context.getGuild(), modlogAction, caseId);

            try {
                avaire.getMuteManger().registerMute(caseId, context.getGuild().getIdLong(), user.getIdLong(), finalExpiresAt);

            } catch (SQLException e) {
                Xeus.getLogger().error(e.getMessage(), e);
                context.makeError("Failed to save the guild settings: " + e.getMessage()).queue();
            }
        });
        return true;
    }

    private boolean rankUserOnWatch(CommandMessage context) {
        GuildTransformer transformer = context.getGuildTransformer();
        if (transformer == null) {
            return sendErrorMessage(context, "Guild settings couldn't be pulled");
        }

        if (transformer.getOnWatchLog() == null) {
            return sendErrorMessage(context, "On Watch log has not been set, ask an admin to fix this.");
        }

        if (transformer.getOnWatchRole() == null) {
            return sendErrorMessage(context, "On Watch role has doesn't exist anymore, ask an admin to fix this.");
        }

        Role on_watch_role = context.getGuild().getRoleById(transformer.getOnWatchRole());
        if (on_watch_role == null) {
            return sendErrorMessage(context, "On Watch role has doesn't exist anymore, ask an admin to fix this.");
        }

        if (!context.getGuild().getSelfMember().canInteract(on_watch_role)) {
            return sendErrorMessage(context, "I'm unable to give someone the on watch role, as it's higher then mine.");
        }

        User user = context.getAuthor();
        if (user == null) {
            return sendErrorMessage(context, "Hmm, you don't exist? This is wierd.");
        }

        /*if (userHasHigherRole(user, context.getMember())) {
            return sendErrorMessage(context, "The on watch role is higher then");
        }*/

        Carbon expiresAt = Carbon.now().addMinutes(10);

        String reason = generateMessage();
        OnWatchType type = expiresAt == null ? OnWatchType.ON_WATCH : OnWatchType.TEMP_ON_WATCH;

        final Carbon finalExpiresAt = expiresAt;
        context.getGuild().addRoleToMember(
            context.getGuild().getMember(user), on_watch_role
        ).reason(reason).queue(aVoid -> {
            OnWatchAction onWatchAction = new OnWatchAction(
                type, context.getAuthor(), user,
                finalExpiresAt != null
                    ? finalExpiresAt.toDayDateTimeString() + " (" + finalExpiresAt.diffForHumans(true) + ")" + "\n" + reason
                    : "\n" + reason
            );

            String caseId = OnWatchlog.log(avaire, context, onWatchAction);
            OnWatchlog.notifyUser(user, context.getGuild(), onWatchAction, caseId);

            try {
                avaire.getOnWatchManger().registerOnWatch(caseId, context.getGuild().getIdLong(), user.getIdLong(), finalExpiresAt);

            } catch (SQLException e) {
                Xeus.getLogger().error(e.getMessage(), e);
                context.makeError("Failed to save the guild settings: " + e.getMessage()).queue();
            }
        });
        return true;
    }

    private boolean userHasHigherRole(User user, Member author) {
        Role role = RoleUtil.getHighestFrom(author.getGuild().getMember(user));
        return role != null && RoleUtil.isRoleHierarchyHigher(author.getRoles(), role);
    }

    private String generateMessage() {
        return
            "[1984] We shall prevail... (You ran the 1984 command)";
    }
}
