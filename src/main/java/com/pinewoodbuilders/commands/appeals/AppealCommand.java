package com.pinewoodbuilders.commands.appeals;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.appeals.AppealType;
import com.pinewoodbuilders.contracts.commands.Command;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AppealCommand extends Command {
    public AppealCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Appeal Management Command";
    }

    @Override
    public String getDescription() {
        return "Manage the appeal with a few commands.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return List.of("`:command close` - Close the current appeal.",
            "`:command open` - Reopen the current appeal.",
            "`:command delete` - List all open appeals.");
    }

    @Override
    public List <String> getTriggers() {
        return Arrays.asList("appeal", "appeal-management");
    }

    @Override
    public List <String> getMiddleware() {
        return List.of(
            "isGuildHROrHigher"
        );
    }


    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (context.getGuildChannel().getType() != ChannelType.TEXT)
            return sendErrorMessage(context, "You can only use this command in a text channel.");

        String[] information = context.getTextChannel().getTopic() != null ? context.getTextChannel().getTopic().split(" - ") : new String[0];
        if (information.length != 4) return sendErrorMessage(context, "This is not an appeal channel.");

        if (args.length < 1) {
            return sendErrorMessage(context, "You must specify a command you want to use");
        }

        return switch (args[0]) {
            case "close" -> closeAppeal(context, args, information);
            case "open" -> openAppeal(context, information);
            case "delete" -> deleteAppeal(context, args, information);
            default -> sendErrorMessage(context, "You must specify a command you want to use");
        };
    }

    private boolean deleteAppeal(CommandMessage context, String[] args, String[] information) {
        String type = information[0];
        AppealType appealType = AppealType.fromName(type);
        if (appealType == null)
            return sendErrorMessage(context, "Unable to obtain the type. Please request help of the developer.");

        String userId = information[1];
        Member appealer = context.getGuild().getMemberById(userId);
        if (appealer == null)
            return sendErrorMessage(context, "Appealer seems to have left the appeal server. Please `$appeal delete` this appeal.");

        String state = information[3];
        if (state.equals("DELETING")) return sendErrorMessage(context, "This appeal is already being deleted.");

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (reason.isEmpty()) reason = "No reason given, appeal will be deleted in 30 seconds.";

        context.makeWarning("Watch out, are you sure you want to delete this appeal for: ```" + reason + "```.")
            .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
            .setTitle("Pinewood - Appeal System").queue();

        JSONArray messages = new JSONArray();

        context.getTextChannel().getIterableHistory().reverse()
            .forEachAsync(message -> {


                JSONObject userIdObject = new JSONObject();
                userIdObject.put("name", message.getAuthor().getName());
                userIdObject.put("tag", message.getAuthor().getDiscriminator());
                userIdObject.put("nick", message.getMember().getEffectiveName());
                userIdObject.put("avatar", message.getAuthor().getAvatarId());

                JSONObject discordData = new JSONObject();
                discordData.put(message.getAuthor().getId(), userIdObject);

                JSONArray attachments = new JSONArray();

                if (message.getAttachments().size() > 0) {
                    for (Message.Attachment attachment : message.getAttachments()) {
                        JSONObject attachmentObject = new JSONObject();
                        attachmentObject.put("name", attachment.getFileName());
                        attachmentObject.put("url", attachment.getUrl());
                        attachmentObject.put("height", attachment.getHeight());
                        attachmentObject.put("width", attachment.getWidth());
                        attachmentObject.put("size", attachment.getSize());
                        try {
                            attachment.getProxy().downloadToFile(File.createTempFile(attachment.getFileName(), "storage/images")).whenComplete((file, error) -> {
                                try {
                                    attachmentObject.put("base64", Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath())));
                                } catch (IOException e) {
                                    attachmentObject.put("base64", "data:image/png;base64,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");
                                }
                            });
                        } catch (IOException e) {
                            attachmentObject.put("base64", "data:image/png;base64,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");
                        }

                        attachments.put(attachmentObject);
                    }
                }

                JSONArray embeds = new JSONArray();
                if (message.getEmbeds().size() > 0) {
                    for (MessageEmbed messageEmbed : message.getEmbeds()) {
                        JSONObject embed = new JSONObject();
                        embed.put("title", messageEmbed.getTitle());
                        embed.put("description", messageEmbed.getDescription());
                        embed.put("color", messageEmbed.getColor() != null ?
                            String.format("#%02x%02x%02x", messageEmbed.getColor().getRed(), messageEmbed.getColor().getGreen(), messageEmbed.getColor().getBlue())
                            : null);
                        embed.put("timestamp", messageEmbed.getTimestamp());
                        embed.put("title", messageEmbed.getTitle());

                        JSONObject thumbnail = new JSONObject();
                        if (messageEmbed.getThumbnail() != null) {
                            thumbnail.put("url", messageEmbed.getThumbnail().getUrl());
                        }
                        embed.put("thumbnail", thumbnail);


                        JSONObject footer = new JSONObject();
                        if (messageEmbed.getFooter() != null) {
                            footer.put("text", messageEmbed.getFooter().getText());
                            footer.put("iconURL", messageEmbed.getFooter().getIconUrl());
                        }
                        embed.put("footer", footer);

                        JSONObject fields = new JSONObject();
                        if (messageEmbed.getFields().size() > 0) {
                            for (MessageEmbed.Field field : messageEmbed.getFields()) {
                                JSONObject fieldObject = new JSONObject();
                                fieldObject.put("name", field.getName());
                                fieldObject.put("value", field.getValue());
                                fields.put(field.getName(), fieldObject);
                            }
                        }
                        embed.put("fields", fields);
                        embeds.put(embed);
                    }
                }

                JSONObject reactions = new JSONObject();
                JSONArray components = new JSONArray();

                JSONObject messageJson = new JSONObject();
                messageJson.put("discordData", discordData);
                messageJson.put("attachments", attachments);
                messageJson.put("reactions", reactions);
                messageJson.put("embeds", embeds);
                messageJson.put("content", message.getContentRaw());
                messageJson.put("components", components);
                messageJson.put("user_id", message.getAuthor().getId());
                messageJson.put("bot", message.getAuthor().isBot());
                messageJson.put("verified", true);
                messageJson.put("username", message.getAuthor().getName());
                messageJson.put("nick", message.getMember().getEffectiveName());
                messageJson.put("tag", message.getAuthor().getDiscriminator());
                messageJson.put("avatar", message.getAuthor().getAvatarId());
                messageJson.put("id", message.getId());
                messageJson.put("created", message.getTimeCreated().toEpochSecond());
                messageJson.put("edited", message.isEdited());


                messages.put(messageJson);
                return true;
            })
            .thenRun(() -> {
                JSONObject channel = new JSONObject();
                JSONObject server = new JSONObject();

                channel.put("name", context.getMessageChannel().getName());
                channel.put("id", context.getMessageChannel().getId());

                server.put("name", context.getGuild().getName());
                server.put("id", context.getGuild().getId());
                server.put("icon", context.getGuild().getIconId());

                System.out.println(messages);
                String html = String.format(
                        """
                        <Server-Info>
                            Server: %s (%s)
                            Channel: %s (%s)
                            Messages: %s


                        <User-Info>
                            To be made
                                                
                        <Base-Transcript>
                            <script src="https://tickettool.xyz/transcript/transcript.bundle.min.obv.js"></script><script type="text/javascript">let channel = %s;let server = %s;let messages = %s;window.Convert(messages, channel, server)</script>
                        """, server.getString("name"), server.getString("id"), channel.getString("name"), channel.getString("id"), messages.length(), channel, server, messages);

                File file = new File("transcript.html");
                try {
                    FileWriter writer = new FileWriter(file);
                    writer.write(html);

                    context.getTextChannel().sendMessage("Transcript created!").addFile(file).queue();

                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }


            });

        context.getTextChannel().getManager().setTopic(String.format("%s - %s - %s - DELETING", appealType.getName(), userId, information[2])).queue();
        /*context.makeError("appeal will be closed in 30 seconds.")
            .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
            .setTitle("Pinewood - Appeal System").queue(message -> message.getTextChannel().delete().queueAfter(30, TimeUnit.SECONDS));*/
        return false;
    }

    private boolean openAppeal(CommandMessage context, String[] information) {
        String type = information[0];
        AppealType appealType = AppealType.fromName(type);
        if (appealType == null)
            return sendErrorMessage(context, "Unable to obtain the type. Please request help of the developer.");

        String userId = information[1];
        Member appealer = context.getGuild().getMemberById(userId);
        if (appealer == null)
            return sendErrorMessage(context, "Appealer seems to have left the appeal server. Please `$appeal delete` this appeal.");

        String state = information[3];
        if (state.equals("CLOSED"))
            return sendErrorMessage(context, "This appeal is already opened, please use `$appeal close/delete`.");

        context.getTextChannel().getManager().setTopic(String.format("%s - %s - %s - OPEN", appealType.getName(), userId, information[2])).queue();

        context.getTextChannel().upsertPermissionOverride(appealer).setDenied(Permission.MESSAGE_SEND).queue();

        context.makeInfo("Appeal opened by " + context.getMember().getAsMention())
            .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
            .setTitle("Pinewood - Appeal System")
            .setThumbnail(appealType.getEmoteImage())
            .queue();

        return true;
    }

    private boolean closeAppeal(CommandMessage context, String[] args, String[] information) {
        String type = information[0];
        AppealType appealType = AppealType.fromName(type);
        if (appealType == null)
            return sendErrorMessage(context, "Unable to obtain the type. Please request help of the developer.");

        String userId = information[1];
        Member appealer = context.getGuild().getMemberById(userId);
        if (appealer == null)
            return sendErrorMessage(context, "Appealer seems to have left the appeal server. Please `$appeal delete` this appeal.");

        String state = information[3];
        if (state.equals("CLOSED"))
            return sendErrorMessage(context, "This appeal has already been closed, please use `$appeal open`.");

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (reason.isEmpty())
            reason = "No reason given, assume the moderators are discussing your appeal. Please wait patiently for a response.";

        context.getTextChannel().getManager().setTopic(String.format("%s - %s - %s - CLOSED", appealType.getName(), userId, information[2])).queue();

        context.getTextChannel().upsertPermissionOverride(appealer).setDenied(Permission.MESSAGE_SEND).queue();

        context.makeInfo("Appeal closed by " + context.getMember().getAsMention() + " for:\n```" + reason + "```")
            .setFooter("Pinewood Intelligence Agency", Constants.PIA_LOGO_URL)
            .setTitle("Pinewood - Appeal System")
            .setThumbnail(appealType.getEmoteImage())
            .queue();

        return true;
    }

}
