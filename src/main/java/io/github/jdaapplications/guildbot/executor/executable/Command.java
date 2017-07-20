package io.github.jdaapplications.guildbot.executor.executable;

import io.github.jdaapplications.guildbot.GuildBot;
import org.hjson.JsonObject;

/**
 * @author Aljoscha Grebe
 */
public class Command extends Executable
{
    protected final String executableScript;
    protected final long id;

    public Command(final GuildBot guildBot, final long channel, final JsonObject config, final String script)
    {
        super(guildBot, config, script);
        this.id = channel;
        this.executableScript = this.engine.getScript(script, this.imports);
    }

    public String getExecutableScript()
    {
        return this.executableScript;
    }

    public long getChannelId()
    {
        return id;
    }
}
