package io.github.jdaapplications.guildbot.util;

import net.dv8tion.jda.core.entities.TextChannel;
import org.hjson.JsonObject;
import org.hjson.JsonValue;

public class GuildBotUtils
{
    public static boolean isScriptChannel(final TextChannel channel)
    {
        return channel.getName().startsWith("cmd-") || channel.getName().startsWith("mthd-") || channel.getName().startsWith("vars-");
    }

    public static JsonObject readConfig(final TextChannel channel)
    {
        return channel.getTopic() == null || channel.getTopic().isEmpty() ? new JsonObject() : JsonValue.readHjson(channel.getTopic()).asObject();
    }
}
