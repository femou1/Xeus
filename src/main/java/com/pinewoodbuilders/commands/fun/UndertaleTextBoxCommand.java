/*
 * Copyright (c) 2019.
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

package com.pinewoodbuilders.commands.fun;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.chat.SimplePaginator;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("FieldCanBeLocal")
public class UndertaleTextBoxCommand extends Command {

    private final Pattern imageRegex = Pattern.compile("(http(s?):)([/|.|\\w|\\s|-])*\\.(?:jpg|jpeg|gif|png)");

    private final String templateUrl = "https://www.demirramon.com/gen/undertale_box.png?message={0}";
    private final String characterQueryString = "&character={1}";
    private final String urlQueryString = "&url={1}";

    public UndertaleTextBoxCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Undertale TextBox Command";
    }

    @Override
    public String getDescription() {
        return "Create your own Undertale text boxes with any character and text you want, you can also specify a image through a URL that should be used as the avatar instead.!\n" +
            "Generator owned by [Demirramon](https://demirramon.com/). Undertale owned by Toby Fox. All rights reserved.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command list [page]` - Lists some undertale characters the generator supports.",
            "`:command <url> <message>` - Generates the image using the given image url and message.",
            "`:command <character> <message>` - Generates the image using the provided undertale character as the avatar, and the provided message."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command Toriel Greetings, my child`",
            "`:command https://i.imgur.com/ZupgGkI.jpg Want to play?`"
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Collections.singletonList("throttle:channel,2,5");
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("undertale", "ut");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            return sendErrorMessage(context, context.i18n("noArgumentsGiven"));
        }

        if (args.length < 3 && args[0].equalsIgnoreCase("list")) {
            return sendCharacterList(context, args);
        }

        if (args.length == 1) {
            return sendErrorMessage(context, "errors.missingArgument", "message");
        }

        try {
            MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
            EmbedBuilder embedBuilder = context.makeEmbeddedMessage()
                .setImage("attachment://" + getClass().getSimpleName() + "-" + args[0] + ".png")
                .requestedBy(context)
                .build();

            messageBuilder.setEmbeds(embedBuilder.build());

            InputStream stream = getImageInputStream(args);
            context.getMessageChannel().sendMessage(messageBuilder.build()).addFiles(FileUpload.fromData(stream, getClass().getSimpleName() + "-" + args[0] + ".png")).queue();

            return true;
        } catch (IOException e) {
            return sendErrorMessage(context, context.i18n("failedToGenerateImage"));
        }
    }

    private InputStream getImageInputStream(String[] args) throws IOException {
        boolean isValidImageUrl = imageRegex.matcher(args[0]).find();

        return new URL(I18n.format(
            templateUrl + (isValidImageUrl ? urlQueryString : characterQueryString),
            encode(String.join(" ", Arrays.copyOfRange(args, 1, args.length))),
            encode(args[0])
        ) + (isValidImageUrl ? "&character=custom" : "")).openStream();
    }

    private String encode(String string) throws UnsupportedEncodingException {
        return URLEncoder.encode(string, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean sendCharacterList(CommandMessage context, String[] args) {
        SimplePaginator<String> paginator = new SimplePaginator<>(Character.names, 10);
        if (args.length > 1) {
            paginator.setCurrentPage(NumberUtil.parseInt(args[1], 1));
        }

        List<String> messages = new ArrayList<>();
        paginator.forEach((index, key, val) -> messages.add(val));

        context.makeInfo(":characters\n\n:paginator")
            .setTitle(context.i18n("title"))
            .set("characters", String.join("\n", messages))
            .set("paginator", paginator.generateFooter(context.getGuild(), generateCommandTrigger(context.getMessage())))
            .queue();

        return false;
    }

    private enum Character {
        Alphys, Asgore, Asriel, Chara, Flowey, Frisk, Gaster, Grillby, Mettaton,
        MettatonEX, Napstablook, OmegaFlowey, Papyrus, Sans, Temmie, Undyne;

        private static final List<String> names;

        static {
            List<String> characterNames = new ArrayList<>();
            for (Character character : values()) {
                characterNames.add(character.name());
            }
            names = Collections.unmodifiableList(characterNames);
        }
    }
}
