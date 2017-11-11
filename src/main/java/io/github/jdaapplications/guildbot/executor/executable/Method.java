package io.github.jdaapplications.guildbot.executor.executable;

import io.github.jdaapplications.guildbot.GuildBot;
import io.github.jdaapplications.guildbot.executor.Engine;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import org.apache.commons.collections4.map.LazyMap;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hjson.JsonObject;

/**
 * @author Aljoscha Grebe
 */
public class Method extends Executable
{
    protected final Map<Engine, String> executableScripts;
    protected final String name;
    protected final List<Pair<String, ? extends Class<?>>> params;
    protected final String proxyScript;
    protected final Class<?> type;

    public Method(final GuildBot guildBot, final JsonObject config, final String name, final String script)
    {
        super(guildBot, config, script);
        this.name = name;

        try
        {
            this.type = ClassUtils.getClass(config.getString("type", "void"));
        }
        catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }

        final JsonObject paramList = (JsonObject) config.get("params");

        this.params = Collections.unmodifiableList(paramList == null 
                ? Collections.emptyList() 
                : StreamSupport.stream(paramList.spliterator(), false)
                    .filter(m -> m.getValue().isString() && !m.getValue().asString().isEmpty())
                    .map(m -> {
                        try
                        {
                            return Pair.of(m.getName(), ClassUtils.getClass(m.getValue().asString()));
                        }
                        catch (ClassNotFoundException e)
                        {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList()));

        this.executableScripts = Collections.unmodifiableMap(LazyMap.lazyMap(new HashMap<>(Engine.values().length), e -> e.getProxyMethod(name, this.type, this.params)));

        this.proxyScript = this.engine.getScript(this.getScript(), this.imports);
    }

    public String getExecutableScript(final Engine targetEngine)
    {
        return this.executableScripts.get(targetEngine);
    }

    public Map<Engine, String> getExecutableScripts()
    {
        return this.executableScripts;
    }

    public InvokeableMethod getInvokeableMethod(final ScriptContext context)
    {
        return args -> Method.this.invoke(context, args);
    }

    public String getName()
    {
        return this.name;
    }

    public List<Pair<String, ? extends Class<?>>> getParams()
    {
        return this.params;
    }

    public String getProxyScript()
    {
        return this.proxyScript;
    }

    public Class<?> getType()
    {
        return this.type;
    }

    protected Object invoke(final ScriptContext context, final Object... args)
    {
        try
        {
            final ScriptEngine scriptEngine = this.engine.newScriptEngine();
            scriptEngine.getContext().getBindings(ScriptContext.ENGINE_SCOPE).putAll(context.getBindings(ScriptContext.ENGINE_SCOPE));

            if (args != null)
                for (int i = 0; i < this.params.size(); i++)
                    scriptEngine.put(this.params.get(i).getKey(), args[i]);

            final Future<?> future = this.guildBot.getThreadPool().submit(() -> scriptEngine.eval(this.proxyScript));

            final Object result = future.get(this.config.getInt("timeout", this.guildBot.getConfig().getInt("timeout", 5)), TimeUnit.SECONDS);

            return this.type == Void.TYPE ? null : result;
        }
        catch (InterruptedException | TimeoutException e)
        {
            throw new RuntimeException("The execution of method \"" + this.name + "\" timed out\n" + this.proxyScript, e);
        }
        catch (final ExecutionException e)
        {
            throw new RuntimeException("The execution of method \"" + this.name + "\" threw an error\n" + this.proxyScript, e.getCause());
        }
        catch (final Exception e)
        {
            throw new RuntimeException("The execution of method \"" + this.name + "\" threw an error\n" + this.proxyScript, e);
        }
    }

    @FunctionalInterface
    public interface InvokeableMethod
    {
        Object invoke(final Object... args);
    }
}
