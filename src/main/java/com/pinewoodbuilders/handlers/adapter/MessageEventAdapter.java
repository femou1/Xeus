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

package com.pinewoodbuilders.handlers.adapter;

import com.avairebot.shared.DiscordConstants;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.MessageCache;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandContainer;
import com.pinewoodbuilders.commands.CommandHandler;
import com.pinewoodbuilders.contracts.cache.CachedMessage;
import com.pinewoodbuilders.contracts.handlers.EventAdapter;
import com.pinewoodbuilders.contracts.moderation.LinkLevel;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.database.collection.Collection;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.database.controllers.*;
import com.pinewoodbuilders.database.query.QueryBuilder;
import com.pinewoodbuilders.database.transformers.ChannelTransformer;
import com.pinewoodbuilders.database.transformers.GlobalSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.database.transformers.GuildTransformer;
import com.pinewoodbuilders.factories.MessageFactory;
import com.pinewoodbuilders.handlers.DatabaseEventHolder;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.middleware.MiddlewareStack;
import com.pinewoodbuilders.middleware.ThrottleMiddleware;
import com.pinewoodbuilders.moderation.global.automute.MuteRatelimit;
import com.pinewoodbuilders.moderation.global.filter.filter.LinkContainer;
import com.pinewoodbuilders.modlog.local.moderation.Modlog;
import com.pinewoodbuilders.modlog.local.shared.ModlogAction;
import com.pinewoodbuilders.modlog.local.shared.ModlogType;
import com.pinewoodbuilders.utilities.ArrayUtil;
import com.pinewoodbuilders.utilities.RestActionUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import okhttp3.HttpUrl;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MessageEventAdapter extends EventAdapter {

    public static final Set <Long> hasReceivedInfoMessageInTheLastMinute = new HashSet <>();
    private static final ExecutorService commandService = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setNameFormat("avaire-command-thread-%d")
            .build()
    );
    private static final Logger log = LoggerFactory.getLogger(MessageEventAdapter.class);
    private static final Pattern userRegEX = Pattern.compile("<@(!|)+[0-9]{16,}+>", Pattern.CASE_INSENSITIVE);
    private static final String mentionMessage = String.join("\n", Arrays.asList(
        "Hi there! I'm **%s**, a multipurpose Discord bot built for fun by %s!",
        "You can see what commands I have by using the `%s` command.",
        "",
        "My original source was from the bot: **Avaire** and has been modified",
        "by %s for Pinewood Builders.",
        "",
        "I am currently running **Xeus v%s**"
    ));

    /**
     * Instantiates the event adapter and sets the avaire class instance.
     *
     * @param avaire The Xeus application class instance.
     */
    public MessageEventAdapter(Xeus avaire) {
        super(avaire);
    }

    public static List <String> replace(List <String> strings) {
        ListIterator <String> iterator = strings.listIterator();
        while (iterator.hasNext()) {
            iterator.set(iterator.next().toLowerCase());
        }
        return strings;
    }

    public void onMessageReceived(MessageReceivedEvent event) {
        if (!isValidMessage(event.getAuthor())) {
            return;
        }

        if (!event.getAuthor().isBot()) {
            if (event.isFromType(ChannelType.TEXT)) {
                MessageCache.getCache(event.getGuild()).set(new CachedMessage(event.getMessage()));
            }
        }

        if (avaire.getBlacklist().isBlacklisted(event.getMessage())) {
            return;
        }

        loadDatabasePropertiesIntoMemory(event).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuild() != null && databaseEventHolder.getPlayer() != null) {
                avaire.getLevelManager().rewardPlayer(event, databaseEventHolder.getGuild(), databaseEventHolder.getPlayer());
            }

            CommandContainer container = CommandHandler.getCommand(avaire, event.getMessage(), event.getMessage().getContentRaw());
            if (container != null && canExecuteCommand(event, container)) {
                invokeMiddlewareStack(new MiddlewareStack(event.getMessage(), container, databaseEventHolder));
                return;
            }

            if (isMentionableAction(event)) {
                container = CommandHandler.getLazyCommand(ArrayUtil.toArguments(event.getMessage().getContentRaw())[1]);
                if (container != null && canExecuteCommand(event, container)) {
                    invokeMiddlewareStack(new MiddlewareStack(event.getMessage(), container, databaseEventHolder, true));
                    return;
                }
            }

            if (isSingleBotMention(event.getMessage().getContentRaw().trim())) {
                sendTagInformationMessage(event);
                return;
            }

            if (!event.getChannelType().isGuild()) {
                sendInformationMessage(event);
            }
        });

    }

    private boolean checkWildcardFilter(String contentStripped, GuildSettingsTransformer guild, Message messageId) {
        String words = contentStripped.toLowerCase();
        List <String> badWordsList = replace(guild.getBadWordsWildcard());
        // system.out.println("UFWords: " + words);
        // system.out.println("FWords: " + badWordsList);

        for (String word : badWordsList) {
            if (words.contains(word)) {
                warnUser(messageId, guild, "**AUTOMOD**: Filter was activated!\n**Type**: " + "``WILDCARD``\n**Word Filtered**: " + word);
                return true;
            }
        }
        return false;
    }

    private boolean checkExactFilter(String contentRaw, GuildSettingsTransformer databaseEventHolder, Message messageId) {
        // system.out.println("FILTER ENABLED");
        List <String> words = replace(Arrays.asList(contentRaw.split(" ")));
        List <String> badWordsList = replace(databaseEventHolder.getBadWordsExact());

        // system.out.println("UWords: " + words);
        // system.out.println("FWords: " + badWordsList);

        boolean b = words.stream().anyMatch(badWordsList::contains);
        if (b) {
            warnUser(messageId, databaseEventHolder, "**AUTOMOD**: Filter was activated!\n**Type**: " + "``EXACT``\n**Sentence Filtered**: \n" + contentRaw);
        }
        return b;
    }

    private boolean checkGlobalWildcardFilter(String contentStripped, GlobalSettingsTransformer guild, Message messageId, GuildSettingsTransformer settings) {
        String words = contentStripped.toLowerCase();
        List <String> badWordsList = replace(guild.getGlobalFilterWildcard());
        //System.out.println("UFWords: " + words);
        //System.out.println("FWords: " + badWordsList);

        for (String word : badWordsList) {
            if (words.contains(word)) {
                warnUserColor(messageId, settings, "**GLOBAL AUTOMOD**: Global Filter was activated!\n**Type**: " + "``WILDCARD``\n**Sentence Filtered**: " + contentStripped, new Color(0, 0, 0), messageId.getChannel());
                return true;
            }
        }
        return false;
    }

    private boolean checkGlobalExactFilter(String contentRaw, GlobalSettingsTransformer databaseEventHolder, Message messageId, GuildSettingsTransformer guild) {
        // system.out.println("FILTER ENABLED");
        List <String> words = replace(Arrays.asList(contentRaw.split(" ")));
        List <String> badWordsList = replace(databaseEventHolder.getGlobalFilterExact());

        // system.out.println("UWords: " + words);
        // system.out.println("FWords: " + badWordsList);

        boolean b = words.stream().anyMatch(badWordsList::contains);
        if (b) {
            return warnUserColor(messageId, guild, "**GLOBAL AUTOMOD**: Global Filter was activated!\n**Type**: " + "``EXACT``\n**Sentence Filtered**: \n" + contentRaw, new Color(0, 0, 0), messageId.getChannel());
        }
        return b;
    }

    private boolean checkLinkFilter(String m) {
        return m.contains("https://") || m.contains("http://") || m.startsWith("porn") || m.contains("www.") || m.contains(".com") || m.contains(".nl") || m.contains(".net") ||
            m.startsWith("http") || m.startsWith("https") || m.contains("http//") || m.contains("https//") || m.matches("[-a-zA-Z\\d@:%._+~#=]{1,256}\\.[a-zA-Z\\d()]{1,6}\\b([-a-zA-Z\\d()@:%_+.~#?&/=]*)") || m.contains("%E2");
    }

    public void onGuildMessageUpdate(MessageUpdateEvent event) {
        loadDatabasePropertiesIntoMemory(event).thenAccept(databaseEventHolder -> {
            if (checkLinkFilter(event.getMessage().getContentRaw())) {
                if (databaseEventHolder.getGuildSettings() == null) return;
                if (databaseEventHolder.getGuildSettings().getOnWatchRole() != 0) {
                    Role watchRole = event.getGuild().getRoleById(databaseEventHolder.getGuildSettings().getOnWatchRole());
                    if (event.getMember().getRoles().contains(watchRole)) {
                        event.getMessage().delete().queue();
                    }
                }
            }
        });
    }


    public void onLocalFilterMessageReceived(MessageReceivedEvent event) {
        loadDatabasePropertiesIntoMemory(event).thenAccept(databaseEventHolder -> checkFilters(event, databaseEventHolder));
    }


    public void onLocalFilterEditReceived(MessageUpdateEvent event) {
        loadDatabasePropertiesIntoMemory(event).thenAccept(databaseEventHolder -> checkFilters(event, databaseEventHolder));
    }

    public void onGlobalFilterMessageReceived(MessageReceivedEvent event) {
        loadDatabasePropertiesIntoMemory(event).thenAccept(databaseEventHolder -> {
                checkPublicFilter(event, databaseEventHolder);
            }
        );
    }

    public void onGlobalFilterEditReceived(MessageUpdateEvent event) {
        loadDatabasePropertiesIntoMemory(event).thenAccept(databaseEventHolder -> {
            checkPublicFilter(event, databaseEventHolder);
        });

    }

    private void checkPublicFilter(GenericMessageEvent genericMessageEvent, DatabaseEventHolder databaseEventHolder) {
        Message event = getActualMessage(genericMessageEvent);
        if (!genericMessageEvent.isFromGuild()) {
            return;
        }

        GuildSettingsTransformer guild = databaseEventHolder.getGuildSettings();
        if (guild == null) {
            System.out.println("Guild is null");
            return;
        }

        GlobalSettingsTransformer settings = guild.getGlobalSettings();
        if (!settings.getGlobalFilter()) {
            return;
        }
        if (!event.getContentRaw().startsWith("debug:") && XeusPermissionUtil.getPermissionLevel(guild, genericMessageEvent.getGuild(), event.getMember()).getLevel() >= GuildPermissionCheckType.LOCAL_GROUP_HR.getLevel()) {
            return;
        }


        String message = event.getContentStripped().replaceAll("[!@#$%^&*()\\[\\]\\-=';/\\\\{}:\"><?|+_`~]", "");

        if (checkGlobalExactFilter(message, settings, event, guild)) {
            System.out.println("Exact Filter removed: `" + message + "` in " + event.getGuild().getName() + " (<#" + event.getChannel().getId() + ">)");
            event.delete().queue();
            MuteRatelimit.hit(ThrottleMiddleware.ThrottleType.USER, event.getAuthor().getIdLong(), event);
            return;
        } else if (checkGlobalWildcardFilter(message, settings, event, guild)) {
            System.out.println("Wildcard Filter removed: `" + message + "` in " + event.getGuild().getName() + " (<#" + event.getChannel().getId() + ">)");
            event.delete().queue();
            MuteRatelimit.hit(ThrottleMiddleware.ThrottleType.USER, event.getAuthor().getIdLong(), event);
            return;
        } else if (checkAutomodFilters(event, guild)) {
            event.delete().queue();
            MuteRatelimit.hit(ThrottleMiddleware.ThrottleType.USER, event.getAuthor().getIdLong(), event);
            return;
        }
        checkPIAInviteFilter(event, settings, databaseEventHolder);
        checkAutoLinkFilter(event, databaseEventHolder);

        if (checkLinkFilter(event.getContentRaw())) {
            if (guild.getOnWatchRole() != 0) {
                Role watchRole = event.getGuild().getRoleById(guild.getOnWatchRole());
                if (event.getMember().getRoles().contains(watchRole)) {
                    event.delete().queue();
                }
            }
        }

    }

    private void checkAutoLinkFilter(Message message, DatabaseEventHolder databaseEventHolder) {
        if (databaseEventHolder.getGuildSettings().getMainGroupId() == 0) {
            return;
        }

        String input = message.getContentRaw();
        LinkExtractor linkExtractor = LinkExtractor.builder()
            .linkTypes(EnumSet.of(LinkType.URL, LinkType.WWW)) // limit to URLs
            .build();

        Iterable <LinkSpan> links = linkExtractor.extractLinks(input);

        for (LinkSpan link : links) {
            String validLink = input.substring(link.getBeginIndex(), link.getEndIndex());
            HttpUrl url = HttpUrl.parse(validLink);
            if (url != null) {
                LinkContainer lc = avaire.getLinkFilterManager().getLinkContainer(databaseEventHolder.getGuildSettings().getMainGroupId(), url.topPrivateDomain());
                if (lc != null) {
                    LinkLevel level = LinkLevel.getLinkLevelFromId(lc.getAction());

                    if (level.isWarn()) {
                        if (databaseEventHolder.getGuildSettings().getLinkFilterLog() == 0) {
                            continue;
                        }
                        if (message.getGuild().getTextChannelById(databaseEventHolder.getGuildSettings().getLinkFilterLog()) == null) {
                            continue;
                        }

                        List <MessageEmbed> lme = new ArrayList <>();

                        PlaceholderMessage phm = MessageFactory.makeEmbeddedMessage(message.getGuild().getTextChannelById(databaseEventHolder.getGuildSettings().getLinkFilterLog()))
                            .setTitle("Link Found - Level " + level.name())
                            .setDescription("""
                                **• Offender**: :offender in :channel
                                **• Deleted**: :hasDeleted
                                **Original Message**:
                                ```:message```
                                **Link**:
                                :link
                                """)
                            .set("offender", message.getAuthor().getAsTag() + " " + message.getAuthor().getAsMention())
                            .set("channel", message.getGuildChannel().getAsMention())
                            .set("hasDeleted", level.isDelete() ? "<:yes:694268114803621908>" : "<:no:694270050257076304>")
                            .set("message", message.getContentRaw())
                            .set("link", validLink)
                            .setFooter("Offender ID: " + message.getAuthor().getId())
                            .setTimestamp(Instant.now())
                            .setColor(level.getColor());

                        lme.add(phm.buildEmbed());

                        if (level.isCheckRedirect()) {
                            try {
                                List <String> redirects = fetchRedirect(validLink, new ArrayList <>());
                                if (redirects.size() > 1) {
                                    lme.add(new EmbedBuilder()
                                        .setDescription(redirects.stream().map(l -> " - " + l + "\n").collect(Collectors.joining())).setColor(level.getColor()).build());
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        message.getGuild()
                            .getTextChannelById(databaseEventHolder.getGuildSettings().getLinkFilterLog())
                            .sendMessageEmbeds(lme)
                            .queue();
                    }

                    if (level.isDelete()) {
                        message.delete().queue();
                    }

                    return;
                }
            }
        }
    }

    private List <String> fetchRedirect(String url, List <String> redirects) throws IOException {
        redirects.add(url);

        HttpURLConnection con = (HttpURLConnection) (new URL(url).openConnection());
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setInstanceFollowRedirects(false);
        con.connect();

        if (con.getHeaderField("Location") == null) {
            return redirects;
        }
        return fetchRedirect(con.getHeaderField("Location"), redirects);
    }

    private boolean checkAutomodFilters(Message message, GuildSettingsTransformer guild) {
        if (guild.getMassMention() > 0) {
            if (message.getMentions().getMembers().size() >= guild.getMassMention()) {
                warnUserColor(message, guild, "**GLOBAL AUTOMOD**: Global Automod was triggered!\n**Type**: " + "``Mass Mention``\n**Sentence Filtered**: \n" + message.getContentRaw(), new Color(0, 0, 0), message.getChannel());
                message.getChannel().sendMessage("Please do not mass mention multiple people. " + message.getMember().getAsMention()).queue();
                return false;
            }
        }
        if (guild.getCharacterSpam() > 0) {
            Pattern pattern = Pattern.compile("(.)\\1{" + (guild.getCharacterSpam() - 1) + ",}", Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(message.getContentRaw());
            if (m.find()) {
                warnUserColor(message, guild, "**GLOBAL AUTOMOD**: Global Automod was triggered!\n**Type**: " + "``Character Spam``\n**Sentence Filtered**: \n" + message.getContentRaw(), new Color(0, 0, 0), message.getChannel());
                return true;
            }
        }
        if (guild.getEmojiSpam() > 0) {
            Pattern pattern = Pattern.compile("(:[^:\\s]*(?:::[^:\\s]*)*:)", Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(message.getContentRaw());

            int count = EmojiParser.extractEmojis(message.getContentRaw()).size();
            while (m.find()) {
                count++;
            }

            if (count >= guild.getEmojiSpam()) {
                warnUserColor(message, guild, "**GLOBAL AUTOMOD**: Global Automod was triggered!\n**Type**: " + "``Emoji Spam``\n**Sentence Filtered**: \n" + message.getContentRaw(), new Color(0, 0, 0), message.getChannel());
                message.delete().queue();
                return true;
            }
        }
        if (guild.getMessageSpam() > 0) {
            List <Message> history = message.getChannel().getIterableHistory().stream().limit(10).filter(msg -> !msg.equals(message)).collect(Collectors.toList());
            int spam = (int) history.stream().filter(m -> m.getAuthor().equals(message.getAuthor()) && !message.getAuthor().isBot()).filter(msg -> (message.getTimeCreated().toEpochSecond() - msg.getTimeCreated().toEpochSecond()) < 10).count();

            if (spam >= guild.getMessageSpam() && !message.getGuild().getOwner().equals(message.getMember())) {
                warnUserColor(message, guild, "**GLOBAL AUTOMOD**: Global Automod was triggered!\n**Type**: " + "``Message Spam``\n**Sentence Filtered**: \n" + message.getContentRaw(), new Color(0, 0, 0), message.getChannel());
                for (Message m : history) {
                    message.getChannel().retrieveMessageById(m.getId()).queue(l -> {
                        l.delete().reason("Auto-Mod Violation").queue();
                    }, null);
                }
                return true;
            }
        }
        if (guild.getImageSpam() > 0) {
            List <Message> history = message.getChannel().getIterableHistory().stream().limit(10).filter(msg -> !msg.equals(message)).collect(Collectors.toList());
            int spam = (int) history.stream().filter(m -> m.getAuthor().equals(message.getAuthor()) && !message.getAuthor().isBot()).filter(msg -> (message.getTimeCreated().toEpochSecond() - msg.getTimeCreated().toEpochSecond()) < 10 && (msg.getAttachments().size() > 0 && message.getAttachments().size() > 0)).count();
            if (spam >= guild.getImageSpam() && !message.getGuild().getOwner().equals(message.getMember())) {
                warnUserColor(message, guild, "**GLOBAL AUTOMOD**: Global Automod was triggered!\n**Type**: " + "``Image Spam``\n**Sentence Filtered**: \n" + message.getContentRaw(), new Color(0, 0, 0), message.getChannel());
                for (Message m : history) {
                    message.getChannel().retrieveMessageById(m.getId()).queue(l -> {
                        l.delete().reason("Auto-Mod Violation").queue();
                    }, failure -> {

                    });
                }
                return true;
            }
        }
        if (guild.getLinkSpam() > 0) {
            List <Message> history = message.getChannel().getIterableHistory().stream().limit(10).filter(msg -> !msg.equals(message)).collect(Collectors.toList());
            int spam = (int) history.stream().filter(m -> m.getAuthor().equals(message.getAuthor()) && !message.getAuthor().isBot()).filter(msg -> (message.getTimeCreated().toEpochSecond() - msg.getTimeCreated().toEpochSecond()) < 10 && (message.getContentRaw().contains("http://") || message.getContentRaw().contains("https://"))).count();
            if (spam >= guild.getLinkSpam() && !message.getGuild().getOwner().equals(message.getMember())) {
                for (Message m : history) {
                    message.getChannel().retrieveMessageById(m.getId()).queue(l -> {
                        l.delete().reason("Auto-Mod Violation").queue();
                    }, failure -> {

                    });
                }
                return true;
            }
        }
        return false;
    }

    private Message getActualMessage(GenericMessageEvent genericMessageEvent) {
        if (genericMessageEvent instanceof MessageReceivedEvent) {
            return ((MessageReceivedEvent) genericMessageEvent).getMessage();
        } else {
            return ((MessageUpdateEvent) genericMessageEvent).getMessage();
        }
    }


    private void checkFilters(GenericMessageEvent event, DatabaseEventHolder databaseEventHolder) {
        MessageReceivedEvent finalMessageEvent = (MessageReceivedEvent) event;
        GuildSettingsTransformer guild = databaseEventHolder.getGuildSettings();
        if (guild != null) {

            if (!guild.getLocalFilter()) {
                return;
            }

            int permissionLevel = XeusPermissionUtil.getPermissionLevel(databaseEventHolder.getGuildSettings(), event.getGuild(), finalMessageEvent.getMember()).getLevel();
            if (permissionLevel >= GuildPermissionCheckType.LOCAL_GROUP_HR.getLevel()) {
                return;
            }

            String message = finalMessageEvent.getMessage().getContentStripped().replaceAll("[,.!@#$%^&*()\\[\\]\\-=';/\\\\{}:\"><?|+_`~]", "");
            if (checkExactFilter(message, guild, finalMessageEvent.getMessage())) {
                System.out.println("[EF] Exact Filter removed: " + message);
                finalMessageEvent.getMessage().delete().queue();

            } else if (checkWildcardFilter(message, guild, finalMessageEvent.getMessage())) {
                System.out.println("[WCF] Wildcard Filter removed: " + message);
                finalMessageEvent.getMessage().delete().queue();

            } else if (checkExactFilter(finalMessageEvent.getMessage().getContentStripped(), guild, finalMessageEvent.getMessage())) {
                System.out.println("[EEF] Exact Filter removed: " + message);
                finalMessageEvent.getMessage().delete().queue();
            } else if (checkWildcardFilter(finalMessageEvent.getMessage().getContentStripped(), guild, finalMessageEvent.getMessage())) {
                System.out.println("[EWCF] Wildcard Filter removed: " + message);
                finalMessageEvent.getMessage().delete().queue();
            }
        }
    }

    private void checkPIAInviteFilter(Message message, GlobalSettingsTransformer settings, DatabaseEventHolder databaseEventHolder) {
        for (String i : message.getInvites()) {
            Invite.resolve(message.getJDA(), i).queue(v -> {
                List <Guild> g = avaire.getRobloxAPIManager().getVerification().getGuildsByMainGroupId(settings.getMainGroupId());

                if (g.stream().noneMatch(guild -> guild.getId().equals(v.getGuild().getId()))) {
                    message.delete().queue();
                    warnUserColor(message, databaseEventHolder.getGuildSettings(), "**AUTOMOD**: Filter was activated!\n**Type**: " + "``INVITE``\n" +
                        "**Guild**: " + v.getGuild().getName() + "\n" +
                        "**Invite**: [Click here!](" + v.getUrl() + ")\n" +
                        "**Inviter**:" + v.getInviter(), new Color(0, 0, 0), message.getChannel());
                    MuteRatelimit.hit(ThrottleMiddleware.ThrottleType.USER, message.getAuthor().getIdLong(), message);
                }
            }, f -> {
                message.delete().queue();
                warnUserColor(message, databaseEventHolder.getGuildSettings(), "**AUTOMOD**: Filter was activated!\n**Type**: " + "``INVITE``\n" +
                    "**Guild**: " + "INVALID (Xeus is banned from the guild, or cannot check this invite)" + "\n" +
                    "**Violator**:" + message.getMember().getEffectiveName(), new Color(0, 0, 0), message.getChannel());
                MuteRatelimit.hit(ThrottleMiddleware.ThrottleType.USER, message.getAuthor().getIdLong(), message);
            });
        }
    }

    private boolean isValidMessage(User author) {
        return !author.isBot() || author.getIdLong() == DiscordConstants.SENITHER_BOT_ID;
    }

    private void warnUser(Message m, GuildSettingsTransformer databaseEventHolder, String reason) {
        /*if (databaseEventHolder.getFilterLog() == null) {
            return;
        }
*/

        ModlogAction modlogAction = new ModlogAction(
            ModlogType.WARN,
            m.getJDA().getSelfUser(),
            m.getAuthor(),
            reason
        );

        Modlog.notifyUser(m.getAuthor(), m.getGuild(), modlogAction, "FILTER");
    }

    private boolean warnUserColor(Message m, GuildSettingsTransformer databaseEventHolder, String reason, Color color, MessageChannel c) {
        /*if (databaseEventHolder.getFilterLog() == null) {
            return;
        }*/

        ModlogAction modlogAction = new ModlogAction(
            ModlogType.WARN,
            m.getJDA().getSelfUser(),
            m.getAuthor(),
            reason
        );

        Modlog.notifyUser(m.getAuthor(), m.getGuild(), modlogAction, "FILTER", color);

        EmbedBuilder builder = MessageFactory.createEmbeddedBuilder()
            .setTitle(I18n.format("{0} {1} | Case #{2}",
                ":loudspeaker:",
                m.getGuild().getName(),
                "FILTER"
            ))
            .setColor(color)
            .setTimestamp(Instant.now())
            .addField("User", m.getMember().getEffectiveName(), true)
            .addField("Moderator", "Xeus", true)
            .addField("Channel", c.getAsMention() + " (`#" + c.getName() + "`)", true)
            .addField("Reason", reason, false)
            .addField("Note on the side", "Filter violations do NOT count against your warning total. These are not logged. **However**, we still recieve notifications about filter violations.", false);

        if (databaseEventHolder.getLocalFilterLog() != 0) {
            TextChannel tc = m.getGuild().getTextChannelById(databaseEventHolder.getLocalFilterLog());
            if (tc != null) {
                tc.sendMessageEmbeds(builder.build()).queue();
                return true;
            }
            return true;
        }
        return true;
    }

    private void invokeMiddlewareStack(MiddlewareStack stack) {
        commandService.submit(stack::next);
    }

    private boolean canExecuteCommand(MessageReceivedEvent event, CommandContainer container) {
        if (event.getChannel() instanceof ThreadChannel) return true;
        if (!container.getCommand().isAllowedInDM() && !event.getChannelType().isGuild()) {
            MessageFactory.makeWarning(event.getMessage(), "<a:alerta:729735220319748117> You can not use this command in direct messages!").queue();
            return false;
        }

        return true;
    }

    private boolean canExecuteCommand(SlashCommandInteractionEvent event, CommandContainer container) {
        if (!container.getCommand().isAllowedInDM() && !event.getChannelType().isGuild()) {
            MessageFactory.makeEmbeddedMessage(event.getChannel(), new Color(1, 1, 1), "<a:alerta:729735220319748117> You can not use this command in direct messages!").queue();
            return false;
        }

        return true;
    }

    private boolean isMentionableAction(MessageReceivedEvent event) {
        if (!event.getMessage().getMentions().isMentioned(avaire.getSelfUser())) {
            return false;
        }

        String[] args = event.getMessage().getContentRaw().split(" ");
        return args.length >= 2 &&
            userRegEX.matcher(args[0]).matches() &&
            event.getMessage().getMentions().getUsers().get(0).getId().equals(avaire.getSelfUser().getId());

    }

    private boolean isSingleBotMention(String rawContent) {
        return rawContent.equals("<@" + avaire.getSelfUser().getId() + ">") ||
            rawContent.equals("<@!" + avaire.getSelfUser().getId() + ">");
    }

    private boolean isAIEnabledForChannel(MessageReceivedEvent event, GuildTransformer transformer) {
        if (transformer == null) {
            return true;
        }

        ChannelTransformer channel = transformer.getChannel(event.getChannel().getId());
        return channel == null || channel.getAI().isEnabled();
    }

    private void sendTagInformationMessage(MessageReceivedEvent event) {
        String author = "**Senither#0001**";
        String editor = "**Stefano#7366**";
        if (event.getMessage().getChannelType().isGuild() && event.getGuild().getMemberById(173839105615069184L) != null) {
            editor = "<@173839105615069184>";
        }

        MessageFactory.makeEmbeddedMessage(event.getMessage().getChannel(), Color.decode("#E91E63"), String.format(mentionMessage,
                avaire.getSelfUser().getName(),
                author,

                CommandHandler.getLazyCommand("help").getCommand().generateCommandTrigger(event.getMessage()),
                editor,
                AppInfo.getAppInfo().version
            ))
            .setFooter("This message will be automatically deleted in one minute.")
            .queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES, null, RestActionUtil.ignore));
    }

    @SuppressWarnings("ConstantConditions")
    private void sendInformationMessage(MessageReceivedEvent event) {
        log.info("Private message received from user(ID: {}) that does not match any commands!",
            event.getAuthor().getId()
        );

        if (hasReceivedInfoMessageInTheLastMinute.contains(event.getAuthor().getIdLong())) {
            return;
        }

        hasReceivedInfoMessageInTheLastMinute.add(event.getAuthor().getIdLong());

        try {
            ArrayList <String> strings = new ArrayList <>(Arrays.asList(
                "To invite me to your server, use this link:",
                "*:oauth*",
                "",
                "You can use `{0}help` to see a list of all the categories of commands.",
                "You can use `{0}help category` to see a list of commands for that category.",
                "For specific command help, use `{0}help command` (for example `{0}help {1}{2}`,\n`{0}help {2}` also works)"
            ));

            strings.add("\n**Full list of commands**\n*https://xeus.pinewood-builders.com/commands*");
            strings.add("\nXeus Support Server:\n*https://xeus.pinewood-builders.com/support*");

            /*CommandContainer commandContainer = CommandHandler.getCommands().stream()
                .filter(container -> !container.getCategory().isGlobalOrSystem())
                .findAny()
                .get();*/

            /*MessageFactory.makeEmbeddedMessage(event.getMessage(), Color.decode("#E91E63"), I18n.format(
                String.join("\n", strings),
                CommandHandler.getCommand(HelpCommand.class).getCategory().getPrefix(event.getMessage()),
                commandContainer.getCategory().getPrefix(event.getMessage()),
                commandContainer.getTriggers().iterator().next()
            ))
                .set("oauth", avaire.getConfig().getString("discord.oauth"))
                .set("botId", avaire.getSelfUser().getId())
                .queue();*/
        } catch (Exception ex) {
            Xeus.getLogger().error("ERROR: ", ex);
        }
    }

    private CompletableFuture <DatabaseEventHolder> loadDatabasePropertiesIntoMemory(final MessageReceivedEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            if (!event.getChannelType().isGuild()) {
                return new DatabaseEventHolder(null, null, null, null);
            }

            GuildTransformer guild = GuildController.fetchGuild(avaire, event.getMessage());
            GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild());

            if (settings.getMainGroupId() == 0) {
                return new DatabaseEventHolder(guild, null, VerificationController.fetchGuild(avaire, event.getMessage()), settings);
            }

            return new DatabaseEventHolder(guild, PlayerController.fetchPlayer(avaire, event.getMessage()), VerificationController.fetchGuild(avaire, event.getMessage()), GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild()));
        });
    }

    private CompletableFuture <DatabaseEventHolder> loadDatabasePropertiesIntoMemory(final MessageUpdateEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            if (!event.getChannelType().isGuild()) {
                return new DatabaseEventHolder(null, null, null, null);
            }

            GuildTransformer guild = GuildController.fetchGuild(avaire, event.getMessage());
            GuildSettingsTransformer settings = GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild());

            if (settings.getMainGroupId() == 0) {
                return new DatabaseEventHolder(guild, null, VerificationController.fetchGuild(avaire, event.getMessage()), settings);
            }

            return new DatabaseEventHolder(guild, PlayerController.fetchPlayer(avaire, event.getMessage()), VerificationController.fetchGuild(avaire, event.getMessage()), GuildSettingsController.fetchGuildSettingsFromGuild(avaire, event.getGuild()));
        });
    }

    public void onMessageDelete(MessageChannel messageChannel, List <String> messageIds) {
        if (!(messageChannel instanceof GuildChannel)) {
            return;
        }

        Collection reactions = ReactionController.fetchReactions(avaire, ((GuildChannel) messageChannel).getGuild());
        if (reactions.isEmpty()) {
            return;
        }

        List <String> removedReactionMessageIds = new ArrayList <>();
        for (DataRow row : reactions) {
            for (String messageId : messageIds) {
                if (Objects.equals(row.getString("message_id"), messageId)) {
                    removedReactionMessageIds.add(messageId);
                }
            }
        }

        if (removedReactionMessageIds.isEmpty()) {
            return;
        }

        QueryBuilder builder = avaire.getDatabase().newQueryBuilder(Constants.REACTION_ROLES_TABLE_NAME);
        for (String messageId : removedReactionMessageIds) {
            builder.orWhere("message_id", messageId);
        }

        try {
            builder.delete();

            ReactionController.forgetCache(
                ((GuildChannel) messageChannel).getGuild().getIdLong()
            );
        } catch (SQLException e) {
            log.error("Failed to delete {} reaction messages for the guild with an ID of {}",
                removedReactionMessageIds.size(), ((GuildChannel) messageChannel).getGuild().getId(), e
            );
        }
    }

    private Guild getGuildFromMessageChannel(MessageChannel channel) {
        if (channel instanceof TextChannel) {
            return ((TextChannel) channel).getGuild();
        }
        if (channel instanceof ThreadChannel) {
            return ((ThreadChannel) channel).getGuild();
        }
        return null;
    }

    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        Collection reactions = ReactionController.fetchReactions(avaire, event.getGuild());
        if (reactions == null) {
            return;
        }

        if (reactions.where("message_id", event.getMessage().getId()).isEmpty()) {
            return;
        }

        try {
            String messageContent = event.getMessage().getContentStripped();
            if (messageContent.trim().length() == 0 && !event.getMessage().getEmbeds().isEmpty()) {
                messageContent = event.getMessage().getEmbeds().get(0).getDescription();
            }

            String finalMessageContent = messageContent;
            avaire.getDatabase().newQueryBuilder(Constants.REACTION_ROLES_TABLE_NAME)
                .where("guild_id", event.getGuild().getId())
                .where("message_id", event.getMessage().getId())
                .update(statement -> {
                    statement.set("snippet", finalMessageContent.substring(
                        0, Math.min(finalMessageContent.length(), 64)
                    ), true);
                });

            ReactionController.forgetCache(event.getGuild().getIdLong());
        } catch (SQLException e) {
            log.error("Failed to update the reaction role message with a message ID of {}, error: {}",
                event.getMessage().getId(), e.getMessage(), e
            );
        }
    }

    public void onNoLinksFilterMessageReceived(MessageReceivedEvent event) {
        loadDatabasePropertiesIntoMemory(event).thenAccept(databaseEventHolder -> {
            if (databaseEventHolder.getGuildSettings().getNoLinksRoles().size() < 1) {
                return;
            }

            ArrayList <Role> list = new ArrayList <>();

            for (Long r : databaseEventHolder.getGuildSettings().getNoLinksRoles()) {
                if (event.getGuild().getRoleById(r) != null) {
                    list.add(event.getGuild().getRoleById(r));
                }
            }

            if (event.getMember().getRoles().stream().anyMatch(list::contains)) {
                if (event.getGuild().getId().equals("438134543837560832")) {
                    if (event.getMember().getRoles().contains(event.getGuild().getRoleById("768310651768537099"))) {
                        if (checkLinkFilter(event.getMessage().getContentStripped())) {
                            cadetRemoveLinksMessage(event.getMessage(), event.getMessage(),
                                "Hey there! It seems like you just tried to send a link in the PBST discord. However this is not possible due to [this recent change](https://discordapp.com/channels/438134543837560832/459764670782504961/768310524927672380).\n" +
                                    "If you'd like to send a link in the discord. Please earn 10 points, and then run ``k!mp`` in the PBST discord.");
                        }
                    }
                } else {
                    event.getMessage().delete().queue();
                }
            }
        });
    }

    private void cadetRemoveLinksMessage(Message message, Message event, String sendMessage) {
        message.delete().queue();
        event.getAuthor().openPrivateChannel().queue(pc -> {
            pc.sendMessageEmbeds(MessageFactory.makeWarning(message, sendMessage).buildEmbed()).queue();
        });
        MuteRatelimit.hit(ThrottleMiddleware.ThrottleType.USER, message.getAuthor().getIdLong(), event);

    }

    public void onEventGalleryMessageSent(MessageReceivedEvent event) {
        if (event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            return;
        }

        if (event.getMessage().getContentRaw().contains("/attachments/")
            || event.getMessage().getContentRaw().contains("https://cdn.discordapp.com/")) {
            return;
        }
        if (event.getMessage().getAttachments().size() > 0) {
            return;
        }

        event.getMessage().delete().queue();
        event.getAuthor().openPrivateChannel().queue(privateChannel -> {
            privateChannel.sendMessageEmbeds(MessageFactory.makeWarning(event.getMessage(), "Sorry, but you're only allowed to post screenshots in this channel. Make sure these are actual screenshots, made during either a raid or patrol.").buildEmbed()).queue();
        });

        event.getChannel().sendMessage(event.getMember().getAsMention()).setEmbeds(MessageFactory.makeError(event.getMessage(), "<a:ALERTA:720439101249290250> **Only post actual event images here, don't talk in this channel!** <a:ALERTA:720439101249290250>").setTimestamp(Instant.now()).setThumbnail(event.getAuthor().getEffectiveAvatarUrl()).setFooter("This message self-destructs after 30 seconds.").buildEmbed()).queue(
            r -> r.delete().queueAfter(30, TimeUnit.SECONDS)
        );


    }

    public void sendPBACRaidVoteEmojis(MessageReceivedEvent event) {
        event.getMessage().addReaction("pbstaye:873681160901918740").queue();
        event.getMessage().addReaction("pbstabstain:873681268494176277").queue();
        event.getMessage().addReaction("pbstnay:873681394499457085").queue();
        event.getMessage().addReaction("petaye:873681238471360532").queue();
        event.getMessage().addReaction("petabstain:873681363784564796").queue();
        event.getMessage().addReaction("petnay:873681453316206673").queue();
        event.getMessage().addReaction("tmsaye:873681202048016385").queue();
        event.getMessage().addReaction("tmsabstain:873681310881837106").queue();
        event.getMessage().addReaction("tmsnay:873681430054588456").queue();
        if (event.getMessage().getMentions().getRoles().stream().anyMatch(role -> role.getName().equals("PIA"))) {
            event.getMessage().addReaction("piaaye:900484641037881405").queue();
            event.getMessage().addReaction("piaabstain:900484610771812352").queue();
            event.getMessage().addReaction("pianay:900484582032420955").queue();
        }
    }

    public void onPIAAdminMessageEvent(MessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) {
            return;
        } // Ignore human messages

        if (!(event.getMessage().getEmbeds().size() > 0)) {
            return;
        } // Ignore normal messages

        GuildSettingsTransformer gst = GuildSettingsController.fetchGuild(avaire, event.getMessage());
        if (gst == null) return;
        if (gst.getMainGroupId() == 0) return;
        List <MessageEmbed> message = event.getMessage().getEmbeds();
        MessageEmbed me = message.get(0);
        List <MessageEmbed.Field> fields = me.getFields();
        if (fields.size() < 1) return;

        boolean isGameBan = false;
        String username = null;
        String reason = null;
        String moderator = null;

        for (MessageEmbed.Field field : fields) {
            if (field.getName() == null || field.getValue() == null) continue;
            if (field.getName().equals("Action") && field.getValue().equals("Gameban")) isGameBan = true;
            if (field.getName().equals("Player")) username = field.getValue();
            if (field.getName().equals("Moderator")) moderator = field.getValue();
            if (field.getName().equals("Reason")) reason = field.getValue();
            if (reason == null) continue;
            if (moderator == null) continue;
            if (username == null) continue;
            if (!isGameBan) continue;

            Pattern pattern = Pattern.compile("(?<=\\[)[^\\]\\[]*(?=])");
            Matcher newUsername = pattern.matcher(username);
            Matcher newModerator = pattern.matcher(moderator);

            String usernameFinal = null;
            String moderatorFinal = null;

            long bannedRobloxId = 0;
            long moderatorUserId = 0;
            if (newUsername.find()) usernameFinal = newUsername.group();
            if (newModerator.find()) moderatorFinal = newModerator.group();

            if (usernameFinal == null || moderatorFinal == null) return;

            bannedRobloxId = avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(usernameFinal);
            moderatorUserId = avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(moderatorFinal);
            //⭕     - User is already in the global-ban database.
            //✅     - User was on discords and is now banned
            //☑️  - User was not in the discords, but is added to the roblox global-bans.
            //❓     - Somehow, the moderator or punished user doesn't have a roblox account.
            //❌     - User coudn't be banned due to an issue, ping stefano if this happens.

            Message msg = event.getMessage();

            if (bannedRobloxId == 0 || moderatorUserId == 0) {
                msg.addReaction("❓").queue();
                return;
            } // React with ❓

            VerificationEntity bannedEntity = avaire.getRobloxAPIManager().getVerification().callDiscordUserFromDatabaseAPI(bannedRobloxId);
            VerificationEntity moderatorEntity = avaire.getRobloxAPIManager().getVerification().callDiscordUserFromDatabaseAPI(moderatorUserId);

            if (moderatorEntity == null) {
                msg.addReaction("\uD83E\uDD26\u200D♀️").queue();
                return;
            } // React with 🤦‍♀️(:woman_facepalming:)

            if (avaire.getGlobalPunishmentManager().isRobloxGlobalBanned(gst.getMainGroupId(), bannedRobloxId)) {
                msg.addReaction("⭕").queue();
                return;
            } // React with ⭕

            try {

                String finalReason = reason;
                long finalBannedRobloxId = bannedRobloxId;
                String finalUsernameFinal = usernameFinal;
                Button approveBan = Button.primary("ban:" + finalBannedRobloxId, "Ban " + finalUsernameFinal);

                event.getChannel().sendMessage("<@" + moderatorEntity.getDiscordId() + ">\n" +
                        "You just banned " + finalUsernameFinal + " for: ```" + reason + "```\n\n" +
                        "Would you like to issue a global-ban? (This message will dissapear in 5 minutes)")
                    .setActionRow(approveBan).queue(
                        message1 -> {
                            avaire.getWaiter().waitForEvent(ButtonInteractionEvent.class, e -> e.getMessage().getId().equals(message1.getId()), e -> {
                                if (
                                    e.getButton().getId().equals("ban:" + finalBannedRobloxId)
                                ) {

                                    if (e.getMember().getIdLong() != moderatorEntity.getDiscordId()) {
                                        e.reply("You where not the person who issued the ban... <@" + moderatorEntity.getDiscordId() + "> has to push the button.").queue();
                                    }

                                    try {
                                        avaire.getGlobalPunishmentManager().registerGlobalBan(String.valueOf(moderatorEntity.getDiscordId()), gst.getMainGroupId(), bannedEntity != null && bannedEntity.getRobloxId() != 0 ? String.valueOf(bannedEntity.getDiscordId()) : null, finalBannedRobloxId, finalUsernameFinal, finalReason);
                                        if (bannedEntity != null) {
                                            List <Guild> guilds = avaire.getRobloxAPIManager().getVerification().getGuildsByMainGroupId(gst.getMainGroupId(), false);
                                            for (Guild g : guilds) {
                                                if (g.getIdLong() == gst.getGlobalSettings().getModerationServerId())
                                                    continue;

                                                if (g.getIdLong() == gst.getGlobalSettings().getAppealsDiscordId())
                                                    continue;

                                                if (!g.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) continue;

                                                g.ban(UserSnowflake.fromId(bannedEntity.getDiscordId()), 0).reason(finalReason).queue();
                                            }
                                        }
                                    } catch (SQLException ex) {
                                        e.reply("Hi, something went wrong in the ban system.\n" +
                                            "Please contact stefano... PLEASE. CONTACT HIM... NOWWWWW!!!!!?!?!?!?").queue();
                                        throw new RuntimeException(ex);
                                    }
                                    message1.delete().queue();
                                    e.reply("<@" + moderatorEntity.getDiscordId() + ">\n" +
                                        "You just global-banned " + finalUsernameFinal + " for: ```" + finalReason + "```").queue(del -> del.deleteOriginal().queueAfter(15, TimeUnit.SECONDS));

                                    if (bannedEntity == null) {
                                        msg.addReaction("☑").queue();
                                    } else {
                                        msg.addReaction("✅").queue();
                                    }

                                }
                            }, 5, TimeUnit.MINUTES, () -> {message1.delete().queue();});
                        }
                    );

                return;

            } catch (Exception e) {
                e.printStackTrace(); // React with ❌
                msg.addReaction("❌").queue();
            }
            return;
        }

    }
}


