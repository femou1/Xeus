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

package com.pinewoodbuilders.commands.system;

import com.oracle.truffle.js.scriptengine.GraalJSEngineFactory;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.SystemCommand;
import com.pinewoodbuilders.utilities.NumberUtil;
import net.dv8tion.jda.api.entities.ChannelType;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class EvalCommand extends SystemCommand {

    private static final Logger log = LoggerFactory.getLogger(EvalCommand.class);

    @Nullable
    private Future lastTask;
    private final ScriptEngine engine;

    public EvalCommand(Xeus avaire) {
        super(avaire);


        Context.Builder context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(s -> true)
            .allowExperimentalOptions(true)
            .option("js.nashorn-compat", "true");
        GraalJSEngineFactory factory = new GraalJSEngineFactory();
        engine = GraalJSScriptEngine.create(factory.getPolyglotEngine(), context);

//        engine = new ScriptEngineManager().getEngineByName("graal.js");


        try {
            engine.eval("var imports = new JavaImporter(" +
                "java.io," +
                "java.lang," +
                "java.util," +
                "Packages.net.dv8tion.jda.api," +
                "Packages.net.dv8tion.jda.api.entities," +
                "Packages.net.dv8tion.jda.api.entities.impl," +
                "Packages.net.dv8tion.jda.api.managers," +
                "Packages.net.dv8tion.jda.api.managers.impl," +
                "Packages.net.dv8tion.jda.api.utils," +
                "Packages.net.dv8tion.jda.api.interactions," +
                "Packages.net.dv8tion.jda.api.interactions.components," +
                "Packages.com.pinewoodbuilders.database.controllers," +
                "Packages.com.pinewoodbuilders.permissions," +
                "Packages.com.pinewoodbuilders.utilities," +
                "Packages.com.pinewoodbuilders.factories," +
                "Packages.com.pinewoodbuilders.language," +
                "Packages.com.pinewoodbuilders.metrics," +
                "Packages.com.pinewoodbuilders.logger," +
                "Packages.com.pinewoodbuilders.cache," +
                "Packages.com.pinewoodbuilders.audio," +
                "Packages.com.pinewoodbuilders.time);");
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "Eval Command";
    }

    @Override
    public String getDescription() {
        return "Evaluates and executes code.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <code>` - Evaluates and executes the given code.",
            "`:command <kill|-k>` - Kills the last task if it is still running.",
            "`:command <timeout|-t> <timeout lenght> <code>` - Evaluates and executes the given code with the given timeout."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
            "`:command context.makeInfo(\"Hello, World\").queue();`",
            "`:command -t 10 return \"Some Code\"`"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("eval");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeWarning("No arguments given, there are nothing to evaluate.").queue();
            return false;
        }

        if (args.length == 1 && (args[0].equals("kill") || args[0].equals("-k"))) {
            return killLastTask(context);
        }

        final long started = System.currentTimeMillis();

        context.getMessageChannel().sendTyping().queue();
        int timeout = args[0].equals("timeout") || args[0].equals("-t")
            ? NumberUtil.parseInt(args[1], -1) : -1;

        String[] parts = context.getMessage().getContentRaw().split(" ");
        final String source = String.join(" ", Arrays.copyOfRange(
            parts, calculateSourceLength(context, timeout), parts.length
        ));

        engine.put("context", context);
        engine.put("message", context.getMessage());
        engine.put("channel", context.getChannel());
        engine.put("jda", context.getJDA());
        engine.put("avaire", avaire);

        if (context.getMessage().isFromType(ChannelType.TEXT)) {
            engine.put("guild", context.getGuild());
            engine.put("member", context.getMember());
        }

        ScheduledExecutorService service = Executors.newScheduledThreadPool(1, r -> new Thread(r, "Eval command execution"));

        Future<?> future = service.submit(() -> {
            Object out;
            try {
                out = engine.eval(
                    "(function() {"
                        + "with (imports) {\n" + source + "\n}"
                        + "})();");

                Xeus.getLogger().debug("Eval output: {}", out == null ? "NULL" : out.toString());
                String output = out == null ? ":thumbsup::skin-tone-3:" : "```\n" + out.toString() + "\n```";

                context.getMessageChannel().sendMessage(String.format("**Input** ```java\n%s```\n**Output**\n%s\nEval took _%sms_",
                    source, output, System.currentTimeMillis() - started
                )).queue();
            } catch (Exception ex) {
                log.debug("Failed to execute eval command, error: {}", ex.getMessage(), ex);

                context.getChannel().sendMessage(String.format("**Input** ```java\n%s```\n**Error Output**\n```%s```\nEval took _%sms_",
                    source, ex.getMessage(), System.currentTimeMillis() - started)
                ).queue();
            }
        });
        this.lastTask = future;

        Thread script = new Thread("Eval comm waiter") {
            @Override
            public void run() {
                try {
                    if (timeout > -1) {
                        future.get(timeout, TimeUnit.SECONDS);
                    }
                } catch (final TimeoutException ex) {
                    future.cancel(true);
                    context.makeWarning("Task exceeded time limit of " + timeout + " seconds.").queue();
                } catch (final Exception ex) {
                    context.makeError(String.format("`%s`\n\n`%sms`",
                        ex.getMessage(), System.currentTimeMillis() - started)
                    ).queue();
                }
            }
        };
        script.start();

        return true;
    }

    private boolean killLastTask(CommandMessage context) {
        if (lastTask == null) {
            context.makeWarning("No task found to kill.").queue();
            return false;
        }

        if (lastTask.isDone() || lastTask.isCancelled()) {
            context.makeWarning("Task isn't running.").queue();
            return false;
        }

        lastTask.cancel(true);
        context.makeSuccess("Task has been killed.").queue();

        return true;
    }

    private int calculateSourceLength(CommandMessage context, int timeout) {
        int sourceLength = context.isMentionableCommand() ? 2 : 1;
        if (timeout > 0) {
            sourceLength += 2;
        }
        return sourceLength;
    }
}
