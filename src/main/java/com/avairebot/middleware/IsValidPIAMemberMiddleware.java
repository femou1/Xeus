package com.avairebot.middleware;

import com.avairebot.AvaIre;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.middleware.Middleware;
import com.avairebot.factories.MessageFactory;
import com.avairebot.utilities.CheckPermissionUtil;
import com.avairebot.utilities.RestActionUtil;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class IsValidPIAMemberMiddleware extends Middleware {

    public IsValidPIAMemberMiddleware(AvaIre avaire) {
        super(avaire);
    }
    String rankName = CheckPermissionUtil.GuildPermissionCheckType.PIA.getRankName();

    @Override
    public String buildHelpDescription(@Nonnull CommandMessage context, @Nonnull String[] arguments) {
        return "**This command can only be executed by an official PIA Moderator!**";
    }

    @Override
    public boolean handle(@Nonnull Message message, @Nonnull MiddlewareStack stack, String... args) {
        int permissionLevel = CheckPermissionUtil.getPermissionLevel(stack.getDatabaseEventHolder().getGuild(), message.getGuild(), message.getMember()).getLevel();
        if (permissionLevel >= CheckPermissionUtil.GuildPermissionCheckType.PIA.getLevel()) {
            return stack.next();
        }

        if (args.length == 0) {
            return sendMustBeOfficialPIAMemberMessage(message);
        }

        if (!avaire.getBotAdmins().getUserById(message.getAuthor().getIdLong()).isAdmin()) {
            return sendMustBeOfficialPIAMemberMessage(message);
        }


        return stack.next();
    }


    private boolean sendMustBeOfficialPIAMemberMessage(@Nonnull Message message) {
        return runMessageCheck(message, () -> {
            MessageFactory.makeError(message, "<a:alerta:729735220319748117> **This command is only usable by official *PIA* members**!")
                .queue(newMessage -> newMessage.delete().queueAfter(45, TimeUnit.SECONDS), RestActionUtil.ignore);

            return false;
        });
    }
}
