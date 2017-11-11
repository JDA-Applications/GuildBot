package io.github.jdaapplications.guildbot;

import io.github.jdaapplications.guildbot.executor.CommandExecutor;
import io.github.jdaapplications.guildbot.util.ExceptionUtils;
import io.github.jdaapplications.guildbot.util.PropertyUtil;
import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ShutdownEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.AnnotatedEventManager;
import net.dv8tion.jda.core.hooks.SubscribeEvent;
import net.dv8tion.jda.webhook.WebhookClient;
import net.dv8tion.jda.webhook.WebhookClientBuilder;
import org.apache.commons.io.FileUtils;
import org.hjson.JsonObject;
import org.hjson.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
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

    private final WebhookClient webhook;
    private final JsonObject config;
    private final JDA jda;
    private final ScheduledThreadPoolExecutor threadPool;

    private CommandExecutor commandExecutor;

    public GuildBot(final File config, final String token, final String webhookURL) throws LoginException, IllegalArgumentException, RateLimitedException, FileNotFoundException, IOException
    {
        this.config = JsonValue.readHjson(FileUtils.readFileToString(config, "UTF-8")).asObject();

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

        this.webhook = webhookURL == null
                ? null
                : new WebhookClientBuilder(webhookURL)
                .setExecutorService(threadPool)
                .setDaemon(true)
                .setThreadFactory(r -> new Thread(r, "Error-Webhook-Thread"))
                .build();

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

        String token = PropertyUtil.getProperty("guildbot.token", Paths.get(".token"));
        if (token == null)
            throw new RuntimeException("could not find a token");

        String webhook = PropertyUtil.getProperty("guildbot.webhook.error", Paths.get(".error-hook"));
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
        try{
            if (this.webhook == null)
                return;

            final String trace = ExceptionUtils.getTrace(throwable);
            final String message = String.format("```\n%.2000s```", trace.replace(getJDA().getToken(), "[REDACTED]"));
            final EmbedBuilder builder = new EmbedBuilder();
            if (context != null)
                builder.setFooter(context, null);

            final MessageEmbed embed = builder
                    .setColor(error)
                    .setDescription(String.format("%.2048s", message))
                    .build();

            webhook.send(embed)
                    .exceptionally(t -> {
                        log.error("Unable to send error to webhook", t);
                        return null;
                    });
        }
        catch (Exception e)
        {
            log.error("Unable to send error to webhook", e);
        }
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
