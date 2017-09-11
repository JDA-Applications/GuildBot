package io.github.jdaapplications.guildbot.executor;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author Aljoscha Grebe
 */
public enum Engine
{
    GROOVY("groovy")
    {
        @Override
        public String getProxyMethod(final String methodName, final Class<?> type, final List<Pair<String, ? extends Class<?>>> params)
        {
            return type.getTypeName() + ' ' + methodName + '(' 
                    + params.stream()
                        .map(e -> e.getValue().getTypeName() + ' ' + e.getKey())
                        .collect(Collectors.joining(", ")) 
                    + ") { " + methodName + ".invoke(" 
                    + params.stream()
                        .map(Pair::getKey)
                        .collect(Collectors.joining(", "))
                    + ") }";
        }

        @Override
        public String getScript(final String script, final Collection<String> imports)
        {
            return imports.stream().map(s -> "import " + s + ".*;").collect(Collectors.joining(" ")) + '\n' + script;
        }

        @Override
        public ScriptEngine newScriptEngine()
        {
            return Engine.SCRIPT_ENGINE_MANAGER.getEngineByName("groovy");
        }

        @Override
        public String escapeCodeBlock(String script)
        {
            return escapeCodeBlock(script, "groovy");
        }
    },
    JAVASCRIPT("js")
    {
        @Override
        public String getProxyMethod(final String methodName, final Class<?> type, final List<Pair<String, ? extends Class<?>>> params)
        {
            return null;
        }

        @Override
        public String getScript(final String script, final Collection<String> imports)
        {
            return "with(new JavaImporter(" + imports.stream().collect(Collectors.joining(", ")) + ")) {" + script + "}";
        }

        @Override
        public ScriptEngine newScriptEngine()
        {
            return Engine.SCRIPT_ENGINE_MANAGER.getEngineByName("nashorn");
        }

        @Override
        public String escapeCodeBlock(String script)
        {
            return escapeCodeBlock(script, "js");
        }
    };

    private static final Map<String, Engine> ENGINES = new HashMap<>(Engine.values().length);

    protected static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();

    protected final String name;

    static
    {
        for (final Engine engine : Engine.values())
            Engine.ENGINES.put(engine.getName(), engine);
    }

    Engine(final String name)
    {
        this.name = name;
    }

    public static Engine getEngine(final String name)
    {
        return Engine.ENGINES.get(name.toLowerCase());
    }

    public String getName()
    {
        return this.name;
    }

    public abstract String getProxyMethod(String methodName, Class<?> type, List<Pair<String, ? extends Class<?>>> params);

    public abstract String getScript(String script, Collection<String> imports);

    public abstract ScriptEngine newScriptEngine();

    public ScriptEngine newScriptEngine(final ScriptContext context)
    {
        final ScriptEngine engine = this.newScriptEngine();
        engine.setContext(context);
        return engine;
    }

    public abstract String escapeCodeBlock(String script);

    protected static String escapeCodeBlock(String script, String langName)
    {
        return script.replaceAll("^```(?:" + langName + "\\n)?([\\S\\s]+)\\n?```$", "$1");
    }
}
