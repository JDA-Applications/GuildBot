package io.github.jdaapplications.guildbot.executor;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 *
 * @author Aljoscha Grebe
 *
 */
public class EngineMap
{
    protected final ScriptContext context;
    protected Map<Engine, ScriptEngine> map;

    public EngineMap()
    {
        this.map = Collections.synchronizedMap(new EnumMap<>(Engine.class));

        final ScriptEngine js = Engine.JAVASCRIPT.newScriptEngine();
        this.map.put(Engine.JAVASCRIPT, js);

        try
        {
            js.eval("engines = {}");
        }
        catch (final ScriptException e)
        {
            throw new RuntimeException(e); // should never happen
        }

        final Bindings engines = (Bindings) js.get("engines");
        engines.put("js", js);

        this.context = js.getContext();
        engines.put("context", this.context);

        for (final Engine engine : Engine.values())
            this.map.computeIfAbsent(engine, e ->
            {
                final ScriptEngine scriptEngine = e.newScriptEngine(this.context);
                ((Bindings) scriptEngine.get("engines")).put(e.getName(), scriptEngine);
                return scriptEngine;
            });

    }

    public Set<Entry<Engine, ScriptEngine>> entrySet()
    {
        return this.map.entrySet();
    }

    public ScriptEngine get(final Engine key)
    {
        return this.map.get(key);
    }

    public ScriptContext getContext()
    {
        return this.context;
    }

}
