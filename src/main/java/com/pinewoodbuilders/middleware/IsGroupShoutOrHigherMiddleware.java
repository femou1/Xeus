package com.pinewoodbuilders.middleware;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.middleware.Middleware;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.utilities.RestActionUtil;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class IsGroupShoutOrHigherMiddleware extends Middleware {

    public IsGroupShoutOrHigherMiddleware(Xeus avaire) {
        super(avaire);
    }

    String rankName = GuildPermissionCheckType.GROUP_SHOUT.getRankName();

    @Override
    public String buildHelpDescription(@Nonnull CommandMessage context, @Nonnull String[] arguments) {
        return "**This command can only be executed by a ``"+ rankName +"`` or higher (Pinewood)!**";
    }

    @Override
    public boolean handle(@Nonnull Message message, @Nonnull MiddlewareStack stack, String... args) {
        if (!message.getChannelType().isGuild()) {
            return stack.next();
        }

        if (avaire.getBotAdmins().getUserById(message.getAuthor().getIdLong(), true).isAdmin()) {
            return stack.next();
        }

        int permissionLevel = XeusPermissionUtil.getPermissionLevel(stack.getDatabaseEventHolder().getGuildSettings(), message.getGuild(), message.getMember()).getLevel();
        if (permissionLevel >= GuildPermissionCheckType.GROUP_SHOUT.getLevel()) {
            return stack.next();
        }

        if (args.length == 0) {
            return sendMustBeGroupShoutOrHigherMessage(message);
        }

        if (!avaire.getBotAdmins().getUserById(message.getAuthor().getIdLong()).isAdmin()) {
            return sendMustBeGroupShoutOrHigherMessage(message);
        }

        return stack.next();
    }

    private boolean sendMustBeGroupShoutOrHigherMessage(@Nonnull Message message) {
        return runMessageCheck(message, () -> {
            MessageFactory.makeError(message, "<a:alerta:729735220319748117> This command is only allowed to be executed by a ``"+rankName+"`` or higher!")
                .queue(newMessage -> newMessage.delete().queueAfter(45, TimeUnit.SECONDS), RestActionUtil.ignore);

            return false;
        });
    }
}
