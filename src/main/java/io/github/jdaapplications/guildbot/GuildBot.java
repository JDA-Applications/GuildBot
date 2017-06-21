package io.github.jdaapplications.guildbot;

import io.github.jdaapplications.guildbot.executor.CommandExecutor;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ShutdownEvent;
import net.dv8tion.jda.core.hooks.AnnotatedEventManager;
import net.dv8tion.jda.core.hooks.SubscribeEvent;
import org.hjson.JsonObject;
import org.hjson.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Aljoscha Grebe
 *
 */
public class GuildBot
{
    public static final Logger log = LoggerFactory.getLogger(GuildBot.class);
    private static AtomicInteger threadCounter = new AtomicInteger(0);

    private CommandExecutor commandExecutor;

    private final JsonObject config;
    private JDA jda;
    private ScheduledThreadPoolExecutor threadPool;

    public GuildBot(final File config, final String token) throws Exception
    {
        this.config = JsonValue.readHjson(new FileReader(config)).asObject();

        final JDABuilder builder = new JDABuilder(AccountType.BOT);

        builder.setEventManager(new AnnotatedEventManager());

        builder.setToken(token);
        builder.setGame(Game.of("loading..."));
        builder.setStatus(OnlineStatus.DO_NOT_DISTURB);

        builder.addEventListener(this);

        this.jda = builder.buildAsync();
    }

    public static void main(final String[] args) throws Exception
    {
        final File config = new File(System.getProperty("guildbot.config", "config.hjson"));

        String token = System.getProperty("guildbot.token");
        if (token == null)
        {
            Path path = Paths.get(".token");

            if (!path.toFile().exists())
                throw new RuntimeException("could not find a token");

            token = Files.readAllLines(path).get(0);
        }

        new GuildBot(config, token);
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

    @SubscribeEvent
    public void onReady(final ReadyEvent event)
    {
        this.jda = event.getJDA();

        this.threadPool = new ScheduledThreadPoolExecutor(4, r ->
        {
            final Thread t = new Thread(r, "GuildBot-" + GuildBot.threadCounter.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, throwable) -> GuildBot.log.error("An error occured", throwable));
            return t;
        });
        this.threadPool.setKeepAliveTime(1, TimeUnit.MINUTES);

        this.commandExecutor = new CommandExecutor(this);
    }

    @SubscribeEvent
    public void onShutdown(final ShutdownEvent event)
    {
        this.threadPool.setKeepAliveTime(10, TimeUnit.SECONDS);
        this.threadPool.allowCoreThreadTimeOut(true);
    }
}
