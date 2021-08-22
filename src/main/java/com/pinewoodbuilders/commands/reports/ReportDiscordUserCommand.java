package com.pinewoodbuilders.commands.reports;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandContext;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class ReportDiscordUserCommand extends Command {

    public ReportDiscordUserCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Report Discord User Command";
    }

    @Override
    public String getDescription() {
        return "Report a user who is breaking the discord rules.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Collections.singletonList(
            "`:command` - Report a user on the official PB Discords."
        );
    }

    @Override
    public List <String> getExampleUsage() {
        return Collections.singletonList(
            "`:command` - Report a user on the official PB Discords."
        );
    }


    @Override
    public List <String> getTriggers() {
        return Collections.singletonList("report");
    }

    @Nonnull
    @Override
    public List <CommandGroup> getGroups() {
        return Collections.singletonList(
            CommandGroups.REPORTS
        );
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList(
            "isOfficialPinewoodGuild",
            "throttle:user,1,30"
        );
    }

    EnumSet <Permission> allow = EnumSet.of(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE);
    EnumSet <Permission> allow_bot = EnumSet.of(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL);

    EnumSet <Permission> deny = EnumSet.of(Permission.MESSAGE_READ);

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (!context.getGuild().getId().equals("438134543837560832")) {
            return false;
        }
        if (args.length > 0) {
            if (context.member.hasPermission(Permission.ADMINISTRATOR) || context.member.getId().equals("173839105615069184")) {
                if (args[0].equalsIgnoreCase("setroles") || args[0].equalsIgnoreCase("listroles")) {
                    handleArgs(context, args);
                    return true;
                }
                context.makeInfo("What would you like to do, the only options you'll have are: \n" +
                    "- ``setroles``\n" +
                    "- ``listroles``").queue();
                return true;
            }
        }
        if (context.getGuildTransformer() == null) {
            context.makeError("The guild's setting could not be loaded, if this error pops up often, please contact Stefano#7366").queue();
            return true;
        }

        if (context.member.getRoles().size() < 2) {
            context.makeError("You must be verified in this discord to run this commmand!").queue();
            return true;
        }

        if (context.getGuild().getTextChannelsByName("report-" + context.member.getEffectiveName(), true).size() > 0) {
            context.makeError("You've already got a channel to report someone, please close this one first before opening another one!").queue();
            return true;
        }
        if (!hasCategory(context.getGuildTransformer(), context)) {
            context.getGuild().createCategory("Reports").addMemberPermissionOverride(context.getJDA().getSelfUser().getIdLong(), allow_bot, null).addRolePermissionOverride(context.getGuild().getPublicRole().getIdLong(), null, deny).queue(l -> {
                insertIntoDatabase(context, l);
                setGuildHRPermissions(l, context);
                createReportChannel(l, context);
            });
        } else {
            createReportChannel(context.getGuild().getCategoryById(context.getGuildTransformer().getReportCategory()), context);
        }
        return false;
    }

    private void handleArgs(CommandMessage context, String[] args) {
        switch (args[0]) {
            case "setroles":
                if (context.message.getMentionedRoles().size() > 0) {
                    List <String> roles = context.getGuildTransformer().getReportPermissionRoles();
                    roles.clear();
                    for (Role r : context.message.getMentionedRoles()) {
                        roles.add(r.getId());
                    }
                    insertRolesIntoDatabase(context);

                }
                break;
            case "listroles":
                StringBuilder s = new StringBuilder();
                for (String i : context.getGuildTransformer().getReportPermissionRoles()) {
                    if (context.getGuild().getRoleById(i) != null) {
                        s.append(context.getGuild().getRoleById(i).getAsMention()).append("\n");
                    }
                }
                context.makeSuccess("Roles: \n" + s).queue();
                break;
        }
    }

    private void createReportChannel(Category l, CommandMessage context) {
        l.createTextChannel("report-" + context.member.getEffectiveName()).addMemberPermissionOverride(context.member.getIdLong(), allow, null).queue(o -> {
            setGuildHRPermissions(o, context);
            o.getManager().setTopic(context.member.getId() + " | Closable | DMember").queue();
            o.sendMessage(context.member.getAsMention()).embed(context.makeSuccess("Greetings ``" + context.getMember().getEffectiveName() + "`` to your report \"ticket\", in here special defense members and trainers can ask you questions about the user you'd like to report. Please start with telling us who you'd like to report (It has to be a user on THIS discord)").buildEmbed()).queue(message ->
            {/*message.pin().queue();
                message.addReaction("❌").queue();
                message.addReaction("⭕").queue();
                message.addReaction("\uD83D\uDD3C").queue();*/});
            context.makeSuccess("Your special report channel has been created in " + o.getAsMention()).queue();
        });
    }

    private void insertIntoDatabase(CommandMessage context, Category l) {
        if (context.getGuildTransformer() == null) {
            context.makeError("The guild's setting could not be loaded, if this error pops up often, please contact Stefano#7366").queue();
        }
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .where("id", context.getGuild().getId())
                .update(statement -> statement.set("report_discord_category", l.getIdLong()));
        } catch (SQLException throwable) {
            context.makeError("Something went wrong.").queue();
            Xeus.getLogger().error("ERROR: ", throwable);
        }

        context.getGuildTransformer().setReportCategory(l.getIdLong());
    }

    private void insertRolesIntoDatabase(CommandMessage context) {
        if (context.getGuildTransformer() == null) {
            context.makeError("The guild's setting could not be loaded, if this error pops up often, please contact Stefano#7366").queue();
        }
        try {
            avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                .where("id", context.getGuild().getId())
                .update(statement -> statement.set("report_discord", Xeus.gson.toJson(context.getGuildTransformer().getReportPermissionRoles()), true));
        } catch (SQLException throwable) {
            context.makeError("Something went wrong.").queue();
            Xeus.getLogger().error("ERROR: ", throwable);
        }
    }

    private void setGuildHRPermissions(Category c, CommandMessage context) {
        if (context.getGuildTransformer() == null) {
            context.makeError("The guild's setting could not be loaded, if this error pops up often, please contact Stefano#7366").queue();
            return;
        }
        List <String> roles = context.getGuildTransformer().getReportPermissionRoles();
        if (roles == null || roles.size() < 1) {
            context.makeError("There haven't been setup any permission roles yet!").queue();
            return;
        }

        for (String i : roles) {
            if (context.getGuild().getRoleById(i) != null) {
                if (c.getPermissionOverride(context.getGuild().getRoleById(i)) != null) {
                    continue;
                }
                c.createPermissionOverride(context.getGuild().getRoleById(i)).setAllow(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE).queue();
            } else {
                context.getGuildTransformer().getReportPermissionRoles().remove(i);

                try {
                    avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                        .where("id", context.getGuild().getId())
                        .update(statement -> statement.set("report_discord", Xeus.gson.toJson(context.getGuildTransformer().getReportPermissionRoles()), true));
                } catch (SQLException throwable) {
                    context.makeError("Something went wrong.").queue();
                    Xeus.getLogger().error("ERROR: ", throwable);
                }

            }

        }
    }

    private void setGuildHRPermissions(TextChannel c, CommandMessage context) {
        if (context.getGuildTransformer() == null) {
            context.makeError("The guild's setting could not be loaded, if this error pops up often, please contact Stefano#7366").queue();
            return;
        }
        List <String> roles = context.getGuildTransformer().getReportPermissionRoles();
        if (roles == null || roles.size() < 1) {
            context.makeError("There haven't been setup any permission roles yet!").queue();
            return;
        }

        for (String i : roles) {
            if (context.getGuild().getRoleById(i) != null) {
                if (c.getPermissionOverride(context.getGuild().getRoleById(i)) != null) {
                    continue;
                }

                c.createPermissionOverride(context.getGuild().getRoleById(i)).setAllow(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE).queue();
            } else {
                context.getGuildTransformer().getReportPermissionRoles().remove(i);

                try {
                    avaire.getDatabase().newQueryBuilder(Constants.GUILD_TABLE_NAME)
                        .where("id", context.getGuild().getId())
                        .update(statement -> statement.set("report_discord", Xeus.gson.toJson(context.getGuildTransformer().getReportPermissionRoles()), true));
                } catch (SQLException throwable) {
                    context.makeError("Something went wrong.").queue();
                    Xeus.getLogger().error("ERROR: ", throwable);
                }

            }

        }
    }

    private boolean hasCategory(GuildTransformer g, CommandContext c) {
        return c.getGuild().getCategoryById(g.getReportCategory()) != null;
    }


}
