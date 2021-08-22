package com.pinewoodbuilders.scheduler.tasks;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.contracts.scheduler.Task;
import com.pinewoodbuilders.factories.MessageFactory;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.Request;
import okhttp3.Response;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class CheckUserOnlineStatus implements Task {


    // BYPASS USERS
    public static final ArrayList<String> Ids = new ArrayList <>() {{
        add("328858"); // Sneekypo
        add("734271"); // wakuesneek2
        add("877785"); // Sneekypo1
        add("810928"); // Sneekzilla
        add("7550583"); // Sneekypotest
        add("1381302"); // sneekypo2008
        // add("25678803"); // Me
    }};
    public void handle(Xeus avaire) {
        if (!avaire.areWeReadyYet()) {
            return;
        }

        for (String id : Ids) {
            Request.Builder request = new Request.Builder()
                    .addHeader("User-Agent", "Xeus v" + AppInfo.getAppInfo().version)
                    .url("https://api.roblox.com/users/{userId}/onlinestatus/".replace("{userId}", id));

            try (Response response = avaire.getRobloxAPIManager().getClient().newCall(request.build()).execute()) {
                if (response.code() == 200) {
                    HashMap<String, Object> onlineStatus = Xeus.gson.fromJson(
                            response.body().string(),
                            new TypeToken<HashMap <String, Object>>() {
                            }.getType());

                    if (onlineStatus.containsKey("IsOnline")) {
                        if ((Boolean) onlineStatus.getOrDefault("IsOnline", false)) {
                            TextChannel tc = avaire.getShardManager().getTextChannelById("859670820724801566");

                            if (tc != null) {
                                tc.sendMessage("@everyone").embed(MessageFactory.makeEmbeddedMessage(avaire.getShardManager().getTextChannelById("859670820724801566"), new Color(255, 0, 0), "[" + id + " just went online!](https://www.roblox.com/users/"+id+"/profile)").buildEmbed()).queue();
                            }
                        }
                    }

                }
            } catch (IOException e) {
                Xeus.getLogger().error("Failed sending request to Roblox API: " + e.getMessage());
            }

        }
    }
}
