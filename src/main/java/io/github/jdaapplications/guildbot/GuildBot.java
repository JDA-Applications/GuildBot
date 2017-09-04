package io.github.jdaapplications.guildbot;

import io.github.jdaapplications.guildbot.executor.CommandExecutor;
import io.github.jdaapplications.guildbot.util.Properties;
import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import net.dv8tion.jda.core.entities.impl.MessageEmbedImpl;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ShutdownEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.AnnotatedEventManager;
import net.dv8tion.jda.core.hooks.SubscribeEvent;
import net.dv8tion.jda.core.requests.Requester;
import net.dv8tion.jda.core.utils.IOUtil;
import okhttp3.*;
import org.hjson.JsonObject;
import org.hjson.JsonValue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.login.LoginException;

/**
 * The main class.
 * 
 * @author Aljoscha Grebe
 */
public class GuildBot
{
    public static final Logger log = LoggerFactory.getLogger(GuildBot.class);

    private static final AtomicInteger threadCounter = new AtomicInteger(0);
    private static final Color error = Color.red;

    private final String webhookUrl;
    private final JsonObject config;
    private final JDA jda;
    private final ScheduledThreadPoolExecutor threadPool;

    private CommandExecutor commandExecutor;

    @SuppressWarnings("resource")
    public GuildBot(final File config, final String token, final String errorWebhook) throws LoginException, IllegalArgumentException, RateLimitedException, FileNotFoundException, IOException
    {
        this.config = JsonValue.readHjson(new FileReader(config)).asObject();
        this.webhookUrl = errorWebhook;

        this.threadPool = new ScheduledThreadPoolExecutor(4, r ->
        {
            final Thread t = new Thread(r, "GuildBot-" + GuildBot.threadCounter.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, throwable) ->
            {
                GuildBot.log.error("An error occurred", throwable);
                handleThrowable(throwable, "Uncaught error in thread: " + thread.getName());
            });
            return t;
        });
        this.threadPool.setKeepAliveTime(1, TimeUnit.MINUTES);

        final JDABuilder builder = new JDABuilder(AccountType.BOT);

        builder.setEventManager(new AnnotatedEventManager());

        builder.setToken(token);

        builder.setGame(Game.of("loading..."));
        builder.setStatus(OnlineStatus.DO_NOT_DISTURB);

        builder.addEventListener(this);

        this.jda = builder.buildAsync();
    }

    @SuppressWarnings("unused")
    public static void main(final String[] args) throws Exception
    {
        final File config = new File(System.getProperty("guildbot.config", "config.hjson"));

        String token = Properties.get("guildbot.token", Paths.get(".token"));
        if (token == null)
            throw new RuntimeException("could not find a token");

        String webhook = Properties.get("guildbot.webhook.error", Paths.get(".error-hook"));
        if (webhook == null)
            GuildBot.log.warn("could not find a error webhook token, disabling webhook");

        new GuildBot(config, token, webhook);
    }

    public CommandExecutor getCommandExecutor()
    {
        return this.commandExecutor;
    }

    public JsonObject getConfig()
    {
        return this.config;
    }

    public JDA getJDA()
    {
        return this.jda;
    }

    public ScheduledExecutorService getThreadPool()
    {
        return this.threadPool;
    }

    public void handleThrowable(final Throwable throwable, final String context)
    {
        if (this.webhookUrl == null)
            return;
        final String trace = getTrace(throwable);
        final String message = String.format("```\n%.2000s```", trace.replace(getJDA().getToken(), "[REDACTED]"));
        final EmbedBuilder builder = new EmbedBuilder();
        if (context != null)
            builder.setFooter(context, null);
        try
        {
            final MessageEmbedImpl embed = (MessageEmbedImpl) builder
                    .setColor(error)
                    .setDescription(String.format("%.2048s", message))
                    .build();


            final String body = new JSONObject()
                    .put("embeds", new JSONArray()
                        .put(embed.toJSONObject()))
                    .toString();

            OkHttpClient client = ((JDAImpl) getJDA()).getRequester().getHttpClient();
            Request request = new Request.Builder().url(this.webhookUrl)
                    .post(RequestBody.create(MediaType.parse("application/json"), body))
                    .addHeader("user-agent", "GuildBot (https://github.com/JDA-Applications/GuildBot)")
                    .addHeader("content-type", "application/json")
                    .addHeader("accept-encoding", "gzip")
                    .build();
            Call call = client.newCall(request);
            try (Response response = call.execute())
            {
                if (response.code() >= 300)
                {
                    log.error("Failed to send error hook ({})", response.code());
                    log.error(new String(IOUtil.readFully(Requester.getBody(response))));
                }
            }
        }
        catch (Exception e)
        {
            log.error("Unable to send error to webhook", e);
        }
    }

    private static String getTrace(final Throwable throwable)
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

    @SubscribeEvent
    private void onReady(final ReadyEvent event)
    {
        this.commandExecutor = new CommandExecutor(this);
    }

    @SubscribeEvent
    private void onShutdown(final ShutdownEvent event)
    {
        this.threadPool.setKeepAliveTime(10, TimeUnit.SECONDS);
        this.threadPool.allowCoreThreadTimeOut(true);
    }
}
