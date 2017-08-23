package com.avairebot.orion.commands.system;

import com.avairebot.orion.Orion;
import com.avairebot.orion.commands.Command;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Collections;
import java.util.List;

public class EvalCommand extends Command {

    public EvalCommand(Orion orion) {
        super(orion);
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
    public List<String> getTriggers() {
        return Collections.singletonList("eval");
    }

    @Override
    public void onCommand(MessageReceivedEvent event, String[] args) {
        try {
            Object out = createScriptEngine(event).eval("(function() { with (imports) { return " + String.join(" ", args) + "}})();");
            String output = out == null ? "Executed without error, void was returned so there is nothing to show." : out.toString();

            if (output.length() > 1890) {
                output = output.substring(0, 1890) + "...";
            }

            event.getChannel().sendMessage("```xl\n" + output + "```").queue();
        } catch (ScriptException e) {
            event.getChannel().sendMessage("**Error:**\n```xl\n" + e.toString() + "```").queue();
        }
    }

    private ScriptEngine createScriptEngine(MessageReceivedEvent event) throws ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");

        engine.put("event", event);
        engine.put("message", event.getMessage());
        engine.put("channel", event.getChannel());
        engine.put("jda", event.getJDA());

        if (event.isFromType(ChannelType.TEXT)) {
            engine.put("guild", event.getGuild());
            engine.put("member", event.getMember());
        }

        engine.eval("var imports = new JavaImporter(" +
                "java.io," +
                "java.lang," +
                "java.util," +
                "Packages.net.dv8tion.jda.core," +
                "Packages.net.dv8tion.jda.core.entities," +
                "Packages.net.dv8tion.jda.core.entities.impl," +
                "Packages.net.dv8tion.jda.core.managers," +
                "Packages.net.dv8tion.jda.core.managers.impl," +
                "Packages.net.dv8tion.jda.core.utils);");

        return engine;
    }
}
