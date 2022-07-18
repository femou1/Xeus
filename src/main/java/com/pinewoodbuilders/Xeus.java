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

package com.pinewoodbuilders;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import com.avairebot.shared.DiscordConstants;
import com.avairebot.shared.ExitCodes;
import com.avairebot.shared.SentryConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.pinewoodbuilders.admin.BotAdmin;
import com.pinewoodbuilders.blacklist.bot.Blacklist;
import com.pinewoodbuilders.blacklist.features.FeatureBlacklist;
import com.pinewoodbuilders.blacklist.kronos.BlacklistManager;
import com.pinewoodbuilders.cache.CacheManager;
import com.pinewoodbuilders.cache.CacheType;
import com.pinewoodbuilders.chat.ConsoleColor;
import com.pinewoodbuilders.commands.Category;
import com.pinewoodbuilders.commands.CategoryDataContext;
import com.pinewoodbuilders.commands.CategoryHandler;
import com.pinewoodbuilders.commands.CommandHandler;
import com.pinewoodbuilders.config.*;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.database.migrations.Migration;
import com.pinewoodbuilders.contracts.database.seeder.Seeder;
import com.pinewoodbuilders.contracts.scheduler.Job;
import com.pinewoodbuilders.database.DatabaseManager;
import com.pinewoodbuilders.database.serializer.PlaylistSongSerializer;
import com.pinewoodbuilders.database.transformers.PlaylistTransformer;
import com.pinewoodbuilders.exceptions.InvalidApplicationEnvironmentException;
import com.pinewoodbuilders.exceptions.InvalidPluginException;
import com.pinewoodbuilders.exceptions.InvalidPluginsPathException;
import com.pinewoodbuilders.handlers.EventEmitter;
import com.pinewoodbuilders.handlers.MainEventHandler;
import com.pinewoodbuilders.handlers.PluginEventHandler;
import com.pinewoodbuilders.handlers.events.ApplicationShutdownEvent;
import com.pinewoodbuilders.imagegen.RankBackgroundHandler;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.level.LevelManager;
import com.pinewoodbuilders.metrics.Metrics;
import com.pinewoodbuilders.middleware.*;
import com.pinewoodbuilders.middleware.global.IsCategoryEnabled;
import com.pinewoodbuilders.moderation.global.filter.LinkFilterManager;
import com.pinewoodbuilders.moderation.global.globalmute.GlobalMuteManager;
import com.pinewoodbuilders.moderation.global.globalwatch.GlobalWatchManager;
import com.pinewoodbuilders.moderation.global.punishments.GlobalPunishmentManager;
import com.pinewoodbuilders.moderation.local.ban.BanManager;
import com.pinewoodbuilders.moderation.local.mute.MuteManager;
import com.pinewoodbuilders.moderation.local.warn.WarnsManager;
import com.pinewoodbuilders.moderation.local.watch.WatchManager;
import com.pinewoodbuilders.pinewood.VoiceWhitelistManager;
import com.pinewoodbuilders.plugin.PluginLoader;
import com.pinewoodbuilders.plugin.PluginManager;
import com.pinewoodbuilders.roblox.RobloxAPIManager;
import com.pinewoodbuilders.scheduler.ScheduleHandler;
import com.pinewoodbuilders.servlet.WebServlet;
import com.pinewoodbuilders.servlet.routes.v1.delete.DeleteAccountVerificationLink;
import com.pinewoodbuilders.servlet.routes.v1.get.*;
import com.pinewoodbuilders.servlet.routes.v1.post.PostAccountVerificationLink;
import com.pinewoodbuilders.servlet.routes.v1.post.PostEvalAnswers;
import com.pinewoodbuilders.servlet.routes.v1.post.PostGuildCleanup;
import com.pinewoodbuilders.shard.ShardEntityCounter;
import com.pinewoodbuilders.time.Carbon;
import com.pinewoodbuilders.utilities.AutoloaderUtil;
import com.pinewoodbuilders.utilities.EventWaiter;
import com.pinewoodbuilders.vote.VoteManager;
import io.sentry.Sentry;
import io.sentry.logback.SentryAppender;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.SessionControllerAdapter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ScheduledFuture;

public class Xeus {

    public static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(
            PlaylistTransformer.PlaylistSong.class,
            new PlaylistSongSerializer()
        )
        .disableHtmlEscaping()
        .serializeNulls()
        .setLenient()
        .create();

    private static final Logger log = LoggerFactory.getLogger("Xeus");

    protected static Xeus avaire;

    private static Environment applicationEnvironment;
    private final EventWaiter waiter;
    private final Settings settings;
    private final Configuration config;
    private final ConstantsConfiguration constants;
    private final CacheManager cache;
    private final Blacklist blacklist;
    private final DatabaseManager database;
    private final LevelManager levelManager;
    private final PluginManager pluginManager;
    private final VoteManager voteManager;
    private final MuteManager muteManger;
    private final BanManager banManager;
    private final GlobalMuteManager globalMuteManager;
    private final WatchManager onWatchManger;
    private final WarnsManager warnsManager;
    private final ShardEntityCounter shardEntityCounter;
    private final EventEmitter eventEmitter;
    private final BotAdmin botAdmins;
    private final WebServlet servlet;
    private final FeatureBlacklist featureBlacklist;
    private final RobloxAPIManager robloxApiManager;
    private final BlacklistManager blacklistManager;
    private final VoiceWhitelistManager voiceWhitelistManager;
    private final GlobalPunishmentManager globalPunishmentManager;
    private final GlobalWatchManager globalWatchManager;
    private final LinkFilterManager linkFilterManager;

    private Carbon shutdownTime = null;
    private int shutdownCode = ExitCodes.EXIT_CODE_RESTART;
    private ShardManager shardManager = null;


    public Xeus(Settings settings) throws IOException, SQLException, InvalidApplicationEnvironmentException {
        this.settings = settings;
        Xeus.avaire = this;

        System.out.println(getVersionInfo(settings));

        log.debug("====================================================");
        log.debug("Starting the application with debug logging enabled!");
        log.debug("====================================================\n");

        log.info("Bootstrapping Xeus v" + AppInfo.getAppInfo().version);

        this.eventEmitter = new EventEmitter(this);
        this.cache = new CacheManager(this);
        this.levelManager = new LevelManager();
        this.waiter = new EventWaiter();

        log.info("Loading configuration");
        constants = new ConstantsConfiguration(this);
        config = new Configuration(this, null, "config.yml");
        if (!config.exists() || !constants.exists()) {
            getLogger().info("The {} or {} configuration files is missing!", "config.yml", "constants.yml");
            getLogger().info(settings.isGenerateJsonFileMode()
                ? "Creating files and skipping terminating process due to command generation flag. "
                : "Creating files and terminating program..."
            );

            config.saveDefaultConfig();
            constants.saveDefaultConfig();

            if (!settings.isGenerateJsonFileMode()) {
                System.exit(ExitCodes.EXIT_CODE_NORMAL);
            }
        }

        if (settings.useEnvOverride()) {
            log.debug("Environment override is enabled, looking for environment variables and replacing the config equivalent values");
            EnvironmentMacros.registerDefaults();
            EnvironmentOverride.overrideWithPrefix("AVA", config);
            EnvironmentOverride.overrideWithPrefix("AVA", constants);
        }

        botAdmins = new BotAdmin(this, Collections.unmodifiableSet(new HashSet <>(
            config.getStringList("botAccess")
        )));

        applicationEnvironment = Environment.fromName(config.getString("environment", Environment.PRODUCTION.getName()));
        if (applicationEnvironment == null) {
            throw new InvalidApplicationEnvironmentException(config.getString("environment", "production"));
        }

        log.info("Starting application in \"{}\" mode", applicationEnvironment.getName());
        if (applicationEnvironment.equals(Environment.DEVELOPMENT)) {
            RestAction.setPassContext(true);
            // Setting the default failure to print stack trace will prevent
            // Sentry catching the error, which makes it pretty hard to
            // debug the errors that are happening.
            //
            // RestAction.DEFAULT_FAILURE = Throwable::printStackTrace;
            log.info("Enabling rest action context parsing and printing stack traces for optimal debugging");
        }

        log.info("Registering database, query builder, schema builder, and services");
        database = new DatabaseManager(this);

        log.info("Registering database table migrations");
        AutoloaderUtil.load(Constants.PACKAGE_MIGRATION_PATH, migration -> {
            database.getMigrations().register((Migration) migration);
        }, false);

        log.info("Registering database table seeders");
        AutoloaderUtil.load(Constants.PACKAGE_SEEDER_PATH, seeder -> {
            database.getSeeder().register((Seeder) seeder);
        }, true);

        log.info("Registering default middlewares");
        MiddlewareHandler.initialize(this);
        MiddlewareHandler.register("hasRole", new HasRoleMiddleware(this));
        MiddlewareHandler.register("hasAnyRole", new HasAnyRoleMiddleware(this));
        MiddlewareHandler.register("hasVoted", new HasVotedTodayMiddleware(this));
        MiddlewareHandler.register("isBotAdmin", new IsBotAdminMiddleware(this));
        MiddlewareHandler.register("require", new RequirePermissionMiddleware(this));
        MiddlewareHandler.register("requireOne", new RequireOnePermissionMiddleware(this));
        MiddlewareHandler.register("throttle", new ThrottleMiddleware(this));
        MiddlewareHandler.register("isDMMessage", new IsDMMessageMiddleware(this));

        MiddlewareHandler.register("isPinewoodGuild", new IsOfficialPinewoodGuildMiddleware(this));
        MiddlewareHandler.register("isValidMGMMember", new IsValidPIAMemberMiddleware(this));
        MiddlewareHandler.register("isAdminOrHigher", new IsAdminOrHigherMiddleware(this));
        MiddlewareHandler.register("isGuildLeadership", new IsManagerOrHigherMiddleware(this));
        MiddlewareHandler.register("isGuildHROrHigher", new IsModOrHigherMiddleware(this));
        MiddlewareHandler.register("isGroupShoutOrHigher", new IsGroupShoutOrHigherMiddleware(this));
        MiddlewareHandler.register("usedInAdminChannel", new IsAdminCommandChannelMiddleware(this));

        String defaultPrefix = getConfig().getString("default-prefix", DiscordConstants.DEFAULT_COMMAND_PREFIX);
        if (getConfig().getString("system-prefix", DiscordConstants.DEFAULT_SYSTEM_PREFIX).equals(defaultPrefix)) {
            log.error("The default prefix and the system prefix is the same.");
            log.error("The system and default prefix can not be the same, stopping the bot");
            System.exit(ExitCodes.EXIT_CODE_NORMAL);
        }

        log.info("Registering default command categories");
        CategoryHandler.addCategory(this, "Administration", defaultPrefix);
        CategoryHandler.addCategory(this, "Automod", defaultPrefix);
        CategoryHandler.addCategory(this, "OnWatch", defaultPrefix);
        CategoryHandler.addCategory(this, "Help", defaultPrefix);
        CategoryHandler.addCategory(this, "Fun", defaultPrefix);
        CategoryHandler.addCategory(this, "GlobalMod", defaultPrefix);
        CategoryHandler.addCategory(this, "Search", defaultPrefix);
        CategoryHandler.addCategory(this, "Utility", defaultPrefix);
        CategoryHandler.addCategory(this, "Evaluations", defaultPrefix);
        CategoryHandler.addCategory(this, "Reports", defaultPrefix);
        CategoryHandler.addCategory(this, "Roblox", defaultPrefix);
        CategoryHandler.addCategory(this, "Verification", defaultPrefix);
        CategoryHandler.addCategory(this, "Settings", defaultPrefix);
        CategoryHandler.addCategory(this, "Appeals", "$");
        CategoryHandler.addCategory(this, "System", getConfig().getString(
            "system-prefix", DiscordConstants.DEFAULT_SYSTEM_PREFIX
        ));

        log.info("Registering commands...");

        AutoloaderUtil.load(Constants.PACKAGE_COMMAND_PATH, command -> {
            CommandHandler.register((Command) command);
        });

        log.info(String.format("\tRegistered %s commands successfully!", CommandHandler.getCommands().size()));

        log.info("Loading command category states");
        Object commandCategoryStates = cache.getAdapter(CacheType.FILE).get("command-category.toggle");
        if (commandCategoryStates != null) {
            //noinspection SingleStatementInBlock,unchecked,unchecked
            ((LinkedTreeMap <String, String>) commandCategoryStates).forEach((key, value) -> {
                Category category = CategoryHandler.fromLazyName(key);
                if (category != null) {
                    IsCategoryEnabled.disableCategory(category, value);
                    log.debug("{} has been disabled for \"{}\"", key, value);
                }
            });
        }

        log.info("Registering jobs...");
        AutoloaderUtil.load(Constants.PACKAGE_JOB_PATH, job -> ScheduleHandler.registerJob((Job) job));
        log.info(String.format("\tRegistered %s jobs successfully!", ScheduleHandler.entrySet().size()));

        log.info("Preparing I18n");
        I18n.start(this);

        log.info("Creating rank backgrounds");
        RankBackgroundHandler.getInstance().start();

        log.info("Creating plugin manager and registering plugins...");
        pluginManager = new PluginManager(this);

        try {
            pluginManager.loadPlugins(this);

            if (pluginManager.getPlugins().isEmpty()) {
                log.info("\tNo plugins was found");
            } else {
                log.info(String.format("\t%s plugins was loaded, invoking all plugins", pluginManager.getPlugins().size()));
                for (PluginLoader plugin : pluginManager.getPlugins()) {
                    int commands = CommandHandler.getCommands().size();
                    int categories = CategoryHandler.getValues().size();
                    int migrations = getDatabase().getMigrations().getMigrations().size();

                    plugin.invokePlugin(this);

                    log.info("\t\t\"{}\" has been enabled with {} Command(s), {} Command Categories, {} Database Migration(s)",
                        plugin.getName(),
                        CommandHandler.getCommands().size() - commands,
                        CategoryHandler.getValues().size() - categories,
                        getDatabase().getMigrations().getMigrations().size() - migrations
                    );
                }
            }
        } catch (InvalidPluginsPathException | InvalidPluginException e) {
            log.error(e.getMessage(), e);
            System.exit(ExitCodes.EXIT_CODE_ERROR);
        }

        if (settings.isGenerateJsonFileMode()) {
            log.info("");
            log.info("Preparations for generating the command file have finished!");
            log.info("Generating file...");

            LinkedHashMap <String, CategoryDataContext> map = CommandHandler.generateCommandMapFrom(null);

            try (FileWriter file = new FileWriter("commandMap.json")) {
                file.write(Xeus.gson.toJson(map));
                log.info("The `commandMap.json` file has been generated successfully!");
            } catch (IOException e) {
                log.error("Something went wrong while trying to save the command map: {}", e.getMessage(), e);
                System.exit(ExitCodes.EXIT_CODE_ERROR);
            }

            System.exit(ExitCodes.EXIT_CODE_NORMAL);
        }

        log.info("Connecting to database & Running migrations & Seeders");
        database.getMigrations().up();
        database.getSeeder().run();

        if (settings.usePluginsIndex()) {
            log.info("Loads plugins from the plugin index");
            pluginManager.loadPluginsFromIndex(avaire);
        }

        log.info("Preparing blacklist and syncing the list with the database");
        blacklist = new Blacklist(this);
        blacklist.syncBlacklistWithDatabase();

        log.info("Preparing verification and checking cache.");
        robloxApiManager = new RobloxAPIManager(this);

        log.info("Preparing report blacklist and syncing the list with the database");
        featureBlacklist = new FeatureBlacklist(this);
        featureBlacklist.syncBlacklistWithDatabase();

        log.info("Preparing user blacklist and syncing the list with the manager");
        blacklistManager = new BlacklistManager(this);

        log.info("Prepairing global-banlist and syncing the list with the manager");
        globalPunishmentManager = new GlobalPunishmentManager(this);

        log.info("Preparing and setting up web servlet");
        servlet = new WebServlet(config.getInt("web-servlet.port",
            config.getInt("metrics.port", WebServlet.defaultPort)
        ));

        if (getConfig().getBoolean("web-servlet.api-routes.leaderboard", true)) {
            servlet.registerGet("/leaderboard/:id", new GetLeaderboardPlayers());
        }

        if (getConfig().getBoolean("web-servlet.api-routes.players", true)) {
            servlet.registerGet("/players/cleanup", new GetPlayerCleanup());
        }

        if (getConfig().getBoolean("web-servlet.api-routes.guilds", true)) {
            servlet.registerPost("/guilds/cleanup", new PostGuildCleanup());
            servlet.registerGet("/guilds/cleanup", new GetGuildCleanup());
            servlet.registerGet("/guilds/:ids/exists", new GetGuildsExists());
            servlet.registerGet("/guilds/:ids", new GetGuilds());
        }

        if (getConfig().getBoolean("web-servlet.api-routes.stats", true)) {
            servlet.registerGet("/stats", new GetStats());
        }

        if (getConfig().getBoolean("web-servlet.api-routes.roblox-verification", true)) {
            servlet.registerGet("/verification/discord/:discordId", new GetRobloxUserByDiscordId());
            servlet.registerGet("/verification/roblox/:robloxId", new GetDiscordIdsByRobloxId());
            servlet.registerPost("/verification/verify/:robloxId", new PostAccountVerificationLink());
            servlet.registerGet("/verification/verify/:robloxId", new GetAccountVerificationLink());
            servlet.registerDelete("/verification/verify/:robloxId", new DeleteAccountVerificationLink());
        }

        if (getConfig().getBoolean("web-servlet.api-routes.evaluations", true)) {
            servlet.registerGet("/evaluations/status/:guildId/:robloxId", new GetEvaluationStatus());
            servlet.registerGet("/evaluations/questions/:guildId", new GetEvaluationQuestions());
            servlet.registerPost("/evaluations/answers/:guildId", new PostEvalAnswers());
        }

        servlet.registerGet("/devforum/handbooks", new GetTextDevforumPost());

        log.info("Preparing and setting up metrics");
        Metrics.setup(this);

        String sentryDsn = config.getString("sentryDsn", "").trim();
        if (sentryDsn.length() > 0) {
            log.info("SentryDSN found, initializing Sentry.io");
            Sentry.init(sentryClient -> {
                sentryClient.setDsn(sentryDsn);

                sentryClient.setEnvironment(getEnvironment().getName());
                switch (getEnvironment()) {
                    case PRODUCTION:
                        sentryClient.setRelease(GitInfo.getGitInfo().commitId);
                        break;

                    default:
                        sentryClient.setRelease(AppInfo.getAppInfo().version);
                        break;
                }

            });
            getSentryLogbackAppender().start();
        } else {
            getSentryLogbackAppender().stop();
        }

        log.info("Preparing vote manager");
        voteManager = new VoteManager(this);

        log.info("Preparing mute manager");
        muteManger = new MuteManager(this);

        log.info("Preparing link filter manager");
        linkFilterManager = new LinkFilterManager(this);

        log.info("Preparing global mute manager");
        globalMuteManager = new GlobalMuteManager(this);

        log.info("Preparing ban manager");
        banManager = new BanManager(this);

        log.info("Preparing on watch manager");
        onWatchManger = new WatchManager(this);

        log.info("Preparing global mute manager");
        globalWatchManager = new GlobalWatchManager(this);

        log.info("Preparing warnings manager");
        warnsManager = new WarnsManager(this);

        log.info("Preparing voice whitelist manager.");
        voiceWhitelistManager = new VoiceWhitelistManager(this);

        log.info("Creating bot instance and connecting to Discord network");

        shardEntityCounter = new ShardEntityCounter(this);

        try {
            shardManager = buildShardManager();
        } catch (LoginException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }

    public static Logger getLogger() {
        return log;
    }

    public final EventWaiter getWaiter() {
        return waiter;
    }

    public static Environment getEnvironment() {
        return applicationEnvironment;
    }

    public static Xeus getInstance() {
        return avaire;
    }

    static String getVersionInfo(@Nullable Settings settings) {
        return ConsoleColor.format("%red\n\n" +
            "\n" +
            " __   __  ______   _    _    _____ \n" +
            " \\ \\ / / |  ____| | |  | |  / ____|\n" +
            "  \\ V /  | |__    | |  | | | (___  \n" +
            "   > <   |  __|   | |  | |  \\___ \\ \n" +
            "  / . \\  | |____  | |__| |  ____) |\n" +
            " /_/ \\_\\ |______|  \\____/  |_____/ \n" +
            "                                   \n" +
            "                                   \n"
            + "%reset"
            + "\n\tVersion:          " + AppInfo.getAppInfo().version
            + "\n\tJVM:              " + System.getProperty("java.version")
            + "\n\tJDA:              " + JDAInfo.VERSION
            + "\n"
        );
    }

    /**
     * Checks if we're ready yet by checking if all the shards are connected and ready to serve events.
     *
     * @return <code>True</code> if all shards has connected and are ready, <code>False</code> otherwise.
     */
    public boolean areWeReadyYet() {
        if (getShardManager() == null) {
            return false;
        }

        for (JDA shard : getShardManager().getShards()) {
            if (shard.getStatus() != JDA.Status.CONNECTED) {
                return false;
            }
        }
        return true;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public ShardEntityCounter getShardEntityCounter() {
        return shardEntityCounter;
    }

    @Nullable
    public SelfUser getSelfUser() {
        for (JDA shard : getShardManager().getShards()) {
            if (shard.getStatus().equals(JDA.Status.CONNECTED)) {
                return shard.getSelfUser();
            }
        }
        return null;
    }

    public Settings getSettings() {
        return settings;
    }

    public Configuration getConfig() {
        return config;
    }

    public ConstantsConfiguration getConstants() {
        return constants;
    }

    public CacheManager getCache() {
        return cache;
    }

    public Blacklist getBlacklist() {
        return blacklist;
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public VoteManager getVoteManager() {
        return voteManager;
    }

    public MuteManager getMuteManger() {
        return muteManger;
    }

    public BanManager getBanManger() {
        return banManager;
    }

    public WatchManager getOnWatchManger() {
        return onWatchManger;
    }

    public GlobalPunishmentManager getGlobalPunishmentManager() {
        return globalPunishmentManager;
    }

    public WebServlet getServlet() {
        return servlet;
    }

    public EventEmitter getEventEmitter() {
        return eventEmitter;
    }

    public GlobalMuteManager getGlobalMuteManager() {return globalMuteManager;}

    public GlobalWatchManager getGlobalWatchManager() {return globalWatchManager;}

    public BotAdmin getBotAdmins() {
        return botAdmins;
    }

    public FeatureBlacklist getFeatureBlacklist() {
        return featureBlacklist;
    }

    public RobloxAPIManager getRobloxAPIManager() {
        return robloxApiManager;
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    public void shutdown() {
        shutdown(ExitCodes.EXIT_CODE_RESTART);
    }

    public void shutdown(int exitCode) {
        if (shardManager != null && !shardManager.getShards().isEmpty()) {
            eventEmitter.push(new ApplicationShutdownEvent(shardManager.getShards().get(0), exitCode));
        }

        FeatureToggleContextHandler.saveToStorage();

        for (ScheduledFuture <?> scheduledFuture : ScheduleHandler.entrySet()) {
            scheduledFuture.cancel(false);
        }

        long shutdownDelay = 1500L;

        getLogger().info("Shutting down bot instance gracefully with exit code " + exitCode);

        try {
            if (shutdownDelay > 5000L) {
                // If the shutdown delay is anymore than 5 seconds, we just set it to a
                // fixed 5 seconds delay, this should help prevent the bot staying up
                // for longer than it really should, even when there is a lot
                // of things to shutdown.
                shutdownDelay = 5000L;
            }

            log.info("Shutting down processes, waiting {} milliseconds for processes to finish shutting down.", shutdownDelay);
            Thread.sleep(shutdownDelay);
        } catch (InterruptedException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }

        if (getShardManager() != null) {
            for (JDA shard : getShardManager().getShards()) {
                shard.shutdown();
            }
        }

        for (ScheduledFuture <?> job : ScheduleHandler.entrySet()) {
            job.cancel(true);
        }

        try {
            getDatabase().getConnection().close();
        } catch (SQLException ex) {
            getLogger().error("Failed to close database connection during shutdown: ", ex);
        }

        System.exit(exitCode);
    }

    public void scheduleShutdown(Carbon time, int exitCode) {
        shutdownTime = time;
        shutdownCode = exitCode;
    }

    public Carbon getShutdownTime() {
        return shutdownTime;
    }

    public int getShutdownCode() {
        return shutdownCode;
    }

    private ShardManager buildShardManager() throws LoginException {
        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.create(EnumSet.of(
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_BANS,
            GatewayIntent.GUILD_EMOJIS,
            GatewayIntent.GUILD_INVITES,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_MESSAGE_REACTIONS,
            GatewayIntent.GUILD_VOICE_STATES,
            GatewayIntent.DIRECT_MESSAGES,
            GatewayIntent.DIRECT_MESSAGE_REACTIONS
        ))
            .setToken(getConfig().getString("discord.token"))
            .setSessionController(new SessionControllerAdapter())
            .setActivity(Activity.watching("pinewood dominate the world!"))
            .setBulkDeleteSplittingEnabled(false)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.NONE)
            .disableCache(CacheFlag.ACTIVITY, CacheFlag.ONLINE_STATUS, CacheFlag.CLIENT_STATUS)
            .setEnableShutdownHook(true)
            .setAutoReconnect(true)
            .setContextEnabled(true)
            .setShardsTotal(settings.getShardCount());


        if (settings.getShards() != null) {
            builder.setShards(settings.getShards());
        }

        builder
            .addEventListeners(new MainEventHandler(this))
            .addEventListeners(new PluginEventHandler(this))
            .addEventListeners(waiter);

        return builder.build();
    }


    private synchronized SentryAppender getSentryLogbackAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        SentryAppender sentryAppender = (SentryAppender) root.getAppender(SentryConstants.SENTRY_APPENDER_NAME);
        if (sentryAppender == null) {
            sentryAppender = new SentryAppender();
            sentryAppender.setName(SentryConstants.SENTRY_APPENDER_NAME);

            ThresholdFilter warningsOrAboveFilter = new ThresholdFilter();
            warningsOrAboveFilter.setLevel(Level.WARN.levelStr);
            warningsOrAboveFilter.start();
            sentryAppender.addFilter(warningsOrAboveFilter);

            sentryAppender.setContext(loggerContext);
            root.addAppender(sentryAppender);
        }
        return sentryAppender;
    }

    public VoiceWhitelistManager getVoiceWhitelistManager() {
        return voiceWhitelistManager;
    }


    public WarnsManager getWarningsManager() {
        return warnsManager;
    }

    public LinkFilterManager getLinkFilterManager() {
        return linkFilterManager;
    }
}
