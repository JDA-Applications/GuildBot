package io.github.jdaapplications.guildbot.executor.executable;

import io.github.jdaapplications.guildbot.GuildBot;
import io.github.jdaapplications.guildbot.executor.Engine;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.hjson.JsonObject;
import org.hjson.JsonValue;

/**
 * @author Aljoscha Grebe
 */
public abstract class Executable
{
    protected final JsonObject config;
    protected final Engine engine;
    protected final GuildBot guildBot;
    protected final Set<String> imports;
    protected final String script;

    public Executable(final GuildBot guildBot, final JsonObject config, final String script)
    {
        this.guildBot = guildBot;
        this.config = config;

        this.engine = Engine.getEngine(config.getString("lang", "js"));

        this.script = this.engine.escapeCodeBlock(script);
        
        final JsonValue importArray = config.get("imports");
        this.imports = Collections.unmodifiableSet(importArray == null
                ? Collections.emptySet()
                : importArray.asArray().values().stream()
                    .map(JsonValue::asString)
                    .collect(Collectors.toSet()));
    }

    public JsonObject getConfig()
    {
        return this.config;
    }

    public Engine getEngine()
    {
        return this.engine;
    }

    public GuildBot getGuildBot()
    {
        return this.guildBot;
    }

    public Set<String> getImports()
    {
        return this.imports;
    }

    public String getScript()
    {
        return this.script;
    }
}
