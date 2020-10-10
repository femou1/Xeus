package com.avairebot.middleware;

import com.avairebot.AvaIre;
import com.avairebot.Constants;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.middleware.Middleware;
import com.avairebot.factories.MessageFactory;
import com.avairebot.utilities.RestActionUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class IsManagerOrHigherMiddleware extends Middleware {

    public IsManagerOrHigherMiddleware(AvaIre avaire) {
        super(avaire);
    }

    ArrayList <String> guilds = Constants.guilds;

    @Override
    public String buildHelpDescription(@Nonnull CommandMessage context, @Nonnull String[] arguments) {
        return "**This command can only be executed in official Pinewood servers!**";
    }

    @Override
    public boolean handle(@Nonnull Message message, @Nonnull MiddlewareStack stack, String... args) {
        if (avaire.getBotAdmins().getUserById(message.getAuthor().getIdLong(), true).isAdmin()) {
            return stack.next();
        }

        if (isManagerOrHigher(stack, message) && !message.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            return stack.next();
        }

        if (args.length == 0) {
            return sendMustBeManagerOrHigherMessage(message);
        }

        if (!avaire.getBotAdmins().getUserById(message.getAuthor().getIdLong()).isAdmin()) {
            return sendMustBeManagerOrHigherMessage(message);
        }

        return stack.next();
    }

    private boolean isManagerOrHigher(MiddlewareStack stack, Message message) {
        Set <Long> adminRoles = stack.getDatabaseEventHolder().getGuild().getAdministratorRoles();
        Set <Long> managerRoles = stack.getDatabaseEventHolder().getGuild().getManagerRoles();

        List <Role> roles = new ArrayList <>();

        for (Long i : managerRoles) {
            Role r = message.getGuild().getRoleById(i);
            if (r != null) {
                roles.add(r);
            }
        }

        for (Long i : adminRoles) {
            Role r = message.getGuild().getRoleById(i);
            if (r != null) {
                roles.add(r);
            }
        }
        return roles.stream().anyMatch(message.getMember().getRoles()::contains);
    }

    private boolean sendMustBeManagerOrHigherMessage(@Nonnull Message message) {
        return runMessageCheck(message, () -> {
            MessageFactory.makeError(message, "<a:alerta:729735220319748117> This command is only allowed to be executed by a manager or higher!")
                .queue(newMessage -> newMessage.delete().queueAfter(45, TimeUnit.SECONDS), RestActionUtil.ignore);

            return false;
        });
    }
}