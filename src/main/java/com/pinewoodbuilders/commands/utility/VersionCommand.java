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

package com.pinewoodbuilders.commands.utility;

import com.pinewoodbuilders.AppInfo;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.cache.CacheType;
import com.pinewoodbuilders.chat.PlaceholderMessage;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.commands.CommandGroup;
import com.pinewoodbuilders.contracts.commands.CommandGroups;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.google.gson.internal.LinkedTreeMap;
import org.jsoup.Jsoup;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class VersionCommand extends Command {

    public VersionCommand(Xeus avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Version Command";
    }

    @Override
    public String getDescription() {
        return "Displays the current version of Ava that is running. If the version is outdated the new version will be shown as well as what type of changes have been made.";
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList("`:command` - Gets the current version of the bot, and displays any changes compared to the master branch if there is any.");
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("version");
    }

    @Override
    public List<String> getMiddleware() {
        return Collections.singletonList(
            "throttle:channel,1,5"
        );
    }

    @Nonnull
    @Override
    public List<CommandGroup> getGroups() {
        return Collections.singletonList(CommandGroups.BOT_INFORMATION);
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        SemanticVersion latestVersion = getLatestVersion();
        if (latestVersion == null) {
            return sendErrorMessage(context, "Failed to fetch the latest version of Xeus, try again later.");
        }

        String template = String.join("\n",
            context.i18n("current"),
            "",
            ":message",
            "",
            context.i18n("getLatest")
        );

        PlaceholderMessage versionMessage = null;
        SemanticVersion currentVersion = new SemanticVersion(AppInfo.getAppInfo().version);
        if (latestVersion.major > currentVersion.major) {
            versionMessage = context.makeError(template)
                .set("message", context.i18n("versions.major"))
                .set("difference", latestVersion.major - currentVersion.major)
                .set("type", context.i18n("versions.majorName"));
        } else if (latestVersion.minor > currentVersion.minor) {
            versionMessage = context.makeWarning(template)
                .set("message", context.i18n("versions.minor"))
                .set("difference", latestVersion.minor - currentVersion.minor)
                .set("type", context.i18n("versions.minorName"));
        } else if (latestVersion.patch > currentVersion.patch) {
            versionMessage = context.makeInfo(template)
                .set("message", context.i18n("versions.patch"))
                .set("difference", latestVersion.patch - currentVersion.patch)
                .set("type", context.i18n("versions.patchName"));
        }

        if (versionMessage == null) {
            versionMessage = context.makeSuccess(context.i18n("usingLatest"));
        }

        addAndFormatLatestCommits(context, versionMessage)
            .setTitle("v" + AppInfo.getAppInfo().version)
            .setFooter(context.i18n("latestVersion", latestVersion))
            .queue();

        return true;
    }

    @SuppressWarnings("unchecked")
    private PlaceholderMessage addAndFormatLatestCommits(CommandMessage context, PlaceholderMessage message) {
        if (avaire.getCache().getAdapter(CacheType.FILE).has("gitlab.commits")) {
            List<LinkedTreeMap<String, Object>> items = (List<LinkedTreeMap<String, Object>>) avaire.getCache()
                .getAdapter(CacheType.FILE).get("gitlab.commits");

            StringBuilder commitChanges = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                LinkedTreeMap<String, Object> item = items.get(i);
                //LinkedTreeMap<String, Object> commit = (LinkedTreeMap<String, Object>) item.get("commit");

                commitChanges.append(String.format("[`%s`](%s) %s\n",
                    item.get("short_id").toString().substring(0, 7),
                    item.get("web_url"),
                    item.get("message").toString().split("\n")[0].trim()
                ));
            }

            message.addField(context.i18n("latestChanges"), commitChanges.toString(), false);
        }
        return message;
    }

    private SemanticVersion getLatestVersion() {
        Object version = avaire.getCache().getAdapter(CacheType.FILE).remember("gitlab.version", 1800, () -> {
            try {
                return Jsoup.connect("https://gitlab.com/pinewood-builders/discord/xeus/-/raw/master/build.gradle")
                    .execute().body().split("version = '")[1].split("'")[0];
            } catch (IOException e) {
                Xeus.getLogger().error("Failed to get latest version from gitlab", e);
                return null;
            }
        });

        if (version == null) {
            return null;
        }
        return new SemanticVersion(version.toString());
    }

    class SemanticVersion {

        private int major;
        private int minor;
        private int patch;

        SemanticVersion(String version) {
            String[] split = version.split("\\.");

            if (split.length > 0) {
                major = NumberUtil.parseInt(split[0]);
            }
            if (split.length > 1) {
                minor = NumberUtil.parseInt(split[1]);
            }
            if (split.length > 2) {
                patch = NumberUtil.parseInt(split[2]);
            }
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
