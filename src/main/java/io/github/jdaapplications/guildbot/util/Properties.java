package io.github.jdaapplications.guildbot.util;

import io.github.jdaapplications.guildbot.GuildBot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Properties
{
    public static String get(final String name, final Path path)
    {
        final String property = System.getProperty(name);
        if (property != null)
            return property;

        try
        {
            if (!Files.exists(path))
            {
                GuildBot.log.warn(path.getFileName() + " file is empty");
                return null;
            }
            if (!Files.isReadable(path))
            {
                GuildBot.log.warn("Unable to read " + path.getFileName());
                return null;
            }
            if (Files.size(path) == 0L)
            {
                GuildBot.log.warn(path.getFileName() + " file is empty");
                return null;
            }
            return Files.newBufferedReader(path).lines().filter(l -> !l.startsWith("#")).findFirst().map(String::trim).orElse(null);
        }
        catch (final IOException e)
        {
            GuildBot.log.error("An error occurred while reading " + path.getFileName(), e);
            return null;
        }
    }
}
