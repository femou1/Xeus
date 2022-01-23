/*
 * Copyright (c) 2018.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders.middleware.global;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.Category;
import com.pinewoodbuilders.commands.CommandHandler;
import com.pinewoodbuilders.commands.administration.ToggleCategoryCommand;
import com.pinewoodbuilders.contracts.middleware.Middleware;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.database.transformers.ChannelTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.middleware.MiddlewareStack;
import com.pinewoodbuilders.utilities.RestActionUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.ThreadChannel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class IsCategoryEnabled extends Middleware {

    private static final HashMap<String, String> disabledCategories;

    static {
        disabledCategories = new HashMap<>();
    }

    public IsCategoryEnabled(Xeus avaire) {
        super(avaire);
    }

    public static void enableCategory(Category category) {
        disabledCategories.remove(category.getName());
    }

    public static void disableCategory(Category category, @Nullable String reason) {
        disabledCategories.put(category.getName(), reason);
    }

    public static HashMap<String, String> getDisabledCategories() {
        return disabledCategories;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public boolean handle(@Nonnull Message message, @Nonnull MiddlewareStack stack, String... args) {
        if (disabledCategories.containsKey(stack.getCommandContainer().getCategory().getName())) {
            if (avaire.getBotAdmins().getUserById(message.getAuthor().getIdLong()).isAdmin()) {
                return stack.next();
            }

            String disabledMessage = disabledCategories.get(stack.getCommandContainer().getCategory().getName());
            if (disabledMessage == null) {
                disabledMessage = "The :category command category is currently disable globally by a bot administrator.";
            }

            String finalDisabledMessage = disabledMessage;
            return runMessageCheck(message, () -> {
                MessageFactory.makeError(message, finalDisabledMessage)
                    .set("category", stack.getCommandContainer().getCategory().getName())
                    .queue(success -> success.delete().queueAfter(15, TimeUnit.SECONDS, null, RestActionUtil.ignore));

                return false;
            });
        }

        if (!message.getChannelType().isGuild()) {
            return stack.next();
        }

        if (isCategoryCommands(stack) || stack.getCommandContainer().getCategory().isGlobalOrSystem()) {
            return stack.next();
        }

        GuildTransformer transformer = stack.getDatabaseEventHolder().getGuild();
        if (transformer == null) {
            return stack.next();
        }

        ChannelTransformer channel = (message.getChannel() instanceof ThreadChannel threadChannel) ? transformer.getChannel(threadChannel.getParentChannel().getId()) : transformer.getChannel(message.getChannel().getId());
        if (channel == null) {
            return stack.next();
        }

        if (channel.isCategoryDisabled(stack.getCommandContainer().getCategory()) && !isModOrHigher(stack, message)) {
            if (isHelpCommand(stack) && stack.isMentionableCommand()) {
                MessageFactory.makeError(message, "The help command is disabled in this channel, you can enable it by using the `:category` command.")
                    .set("category", CommandHandler.getCommand(ToggleCategoryCommand.class).getCommand().generateCommandTrigger(message))
                    .queue(success -> success.delete().queueAfter(15, TimeUnit.SECONDS, null, RestActionUtil.ignore));
            }

            return false;
        }

        return stack.next();
    }

    private boolean isModOrHigher(MiddlewareStack stack, Message message) {
        int permissionLevel = XeusPermissionUtil.getPermissionLevel(stack.getDatabaseEventHolder().getGuildSettings(), message.getGuild(), message.getMember()).getLevel();
        return permissionLevel >= GuildPermissionCheckType.LOCAL_GROUP_HR.getLevel();
    }

    private boolean isCategoryCommands(MiddlewareStack stack) {
        return stack.getCommand().getClass().getTypeName().equals("com.pinewoodbuilders.commands.administration.ToggleCategoryCommand") ||
            stack.getCommand().getClass().getTypeName().equals("com.pinewoodbuilders.commands.administration.CategoriesCommand") ||
            stack.getCommand().getClass().getTypeName().equals("com.pinewoodbuilders.commands.administration.ModifyAdminCommandsChannelCommand");
    }

    private boolean isHelpCommand(MiddlewareStack stack) {
        return stack.getCommand().getClass().getTypeName().equals("com.pinewoodbuilders.commands.help.HelpCommand");
    }
}
