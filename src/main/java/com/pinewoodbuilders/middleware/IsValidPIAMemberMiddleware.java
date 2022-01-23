package com.pinewoodbuilders.middleware;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.middleware.Middleware;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.utilities.RestActionUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class IsValidPIAMemberMiddleware extends Middleware {

    public IsValidPIAMemberMiddleware(Xeus avaire) {
        super(avaire);
    }
    String rankName = GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getRankName();

    @Override
    public String buildHelpDescription(@Nonnull CommandMessage context, @Nonnull String[] arguments) {
        return "**This command can only be executed by an official MGM Moderator!**";
    }

    @Override
    public boolean handle(@Nonnull Message message, @Nonnull MiddlewareStack stack, String... args) {
        int permissionLevel = XeusPermissionUtil.getPermissionLevel(stack.getDatabaseEventHolder().getGuildSettings(), message.getGuild(), message.getMember()).getLevel();
        if (permissionLevel >= GuildPermissionCheckType.MAIN_GLOBAL_MODERATOR.getLevel()) {
            return stack.next();
        }

        if (args.length == 0) {
            return sendMustBeOfficialPIAMemberMessage(message);
        }

        if (!avaire.getBotAdmins().getUserById(message.getAuthor().getIdLong()).isAdmin()) {
            return sendMustBeOfficialPIAMemberMessage(message);
        }


        return sendMustBeOfficialPIAMemberMessage(message);
    }


    private boolean sendMustBeOfficialPIAMemberMessage(@Nonnull Message message) {
        return runMessageCheck(message, () -> {
            MessageFactory.makeError(message, "<a:alerta:729735220319748117> **This command is only usable by official *PIA* members**!")
                .queue(newMessage -> newMessage.delete().queueAfter(45, TimeUnit.SECONDS), RestActionUtil.ignore);

            return false;
        });
    }
}
