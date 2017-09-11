package io.github.jdaapplications.guildbot.util;

public class ExceptionUtils
{
    public static String getTrace(final Throwable throwable)
    {
        StringBuilder builder = new StringBuilder(throwable.getClass().getName())
                .append(": ")
                .append(throwable.getMessage());

        StackTraceElement[] elements = throwable.getStackTrace();
        for (int i = 0; i < elements.length && i < 15; i++)
        {
            StackTraceElement element = elements[i];
            // we don't need to go back further...
            if (element.getClassName().startsWith("net.dv8tion.jda.core.hooks.AnnotatedEventManager"))
            {
                builder.append("\n\n<... omitting jda trace ...>");
                break;
            }
            builder.append("\n\tat ").append(element.toString());
        }

        return builder.toString();
    }
}
