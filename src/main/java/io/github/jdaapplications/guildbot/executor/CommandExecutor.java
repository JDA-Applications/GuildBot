package io.github.jdaapplications.guildbot.executor;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import io.github.jdaapplications.guildbot.GuildBot;
import io.github.jdaapplications.guildbot.executor.executable.Command;
import io.github.jdaapplications.guildbot.executor.executable.Method;
import io.github.jdaapplications.guildbot.executor.executable.Variables;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.channel.text.GenericTextChannelEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.channel.text.update.TextChannelUpdateNameEvent;
import net.dv8tion.jda.core.events.channel.text.update.TextChannelUpdateTopicEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.hooks.SubscribeEvent;
import net.dv8tion.jda.core.managers.Presence;
import net.dv8tion.jda.core.requests.RestAction;
import org.hjson.JsonObject;
import org.hjson.JsonValue;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.SimpleBindings;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Aljoscha Grebe
 */
public class CommandExecutor
{
    private Map<String, Command> commands;

    private final GuildBot guildBot;
    private Map<String, Method> methods;
    private Map<String, Variables> vars;

    private final Bindings globalStore;

    public CommandExecutor(final GuildBot guildBot)
    {
        this.guildBot = guildBot;

        this.globalStore = new SimpleBindings();

        guildBot.getThreadPool().execute(this::init);
    }

    public Map<String, Command> getCommands()
    {
        return Collections.unmodifiableMap(this.commands);
    }

    public GuildBot getGuildBot()
    {
        return this.guildBot;
    }

    public Map<String, Method> getMethods()
    {
        return Collections.unmodifiableMap(this.methods);
    }

    public Map<String, Variables> getVars()
    {
        return Collections.unmodifiableMap(this.vars);
    }

    @SubscribeEvent
    private void onGuildMessageDelete(final GuildMessageDeleteEvent event)
    {
        this.update(event.getChannel());
    }

    @SubscribeEvent
    private void onGuildMessageReceived(final GuildMessageReceivedEvent event)
    {
        this.update(event.getChannel());
    }

    @SubscribeEvent
    private void onGuildMessageUpdate(final GuildMessageUpdateEvent event)
    {
        this.update(event.getChannel());
    }

    @SubscribeEvent
    private void onMessageReceived(final MessageReceivedEvent event)
    {
        final String prefix = this.guildBot.getConfig().getString("prefix", this.guildBot.getJDA().getSelfUser().getAsMention() + ' ');

        String content = event.getMessage().getRawContent();
        if (!content.startsWith(prefix))
            return;

        content = content.substring(prefix.length());

        final String[] split = content.split("\\s+", 2);

        final String commandName = split[0].toLowerCase();

        final Command command = this.commands.get(commandName);

        if (command == null)
            return;

        final String args = split.length > 1 ? split[1] : "";

        this.execute(command, event, args);
    }

    @SubscribeEvent
    private void onReconnect(final ReconnectedEvent event)
    {
        Guild guild = event.getJDA().getGuildById(this.guildBot.getConfig().getLong("guildId", 0));

        if (guild == null)
            return;

        this.guildBot.getThreadPool().execute(() -> guild.getTextChannels().forEach(this::update));
    }

    @SubscribeEvent
    private void onTextChannelCreate(final TextChannelCreateEvent event)
    {
        this.update(event.getChannel());
    }

    @SubscribeEvent
    private void onTextChannelDelete(final TextChannelDeleteEvent event)
    {
        this.delete(event, event.getChannel().getName());
    }

    @SubscribeEvent
    private void onTextChannelUpdateName(final TextChannelUpdateNameEvent event)
    {
        this.delete(event, event.getOldName());
        this.update(event.getChannel());
    }

    @SubscribeEvent
    private void onTextChannelUpdateTopic(final TextChannelUpdateTopicEvent event)
    {
        this.update(event.getChannel());
    }

    private synchronized void update(final TextChannel channel)
    {
        if (channel.getGuild().getIdLong() != this.guildBot.getConfig().getLong("guildId", 0))
            return;

        if (!CommandExecutor.isScriptChannel(channel))
            return;

        try
        {
            final JsonObject config = CommandExecutor.readConfig(channel);

            Consumer<String> consumer;

            if (channel.getName().startsWith("mthd-"))
            {
                consumer = script ->
                {
                    final String name = channel.getName().substring(5);
                    this.methods.put(name, new Method(this.guildBot, config, name, script));
                };
            }
            else if (channel.getName().startsWith("vars-"))
            {
                consumer = script ->
                {
                    final String name = channel.getName().substring(5);
                    this.vars.put(name, new Variables(this.guildBot, config, script));
                };
            }
            else
            {
                consumer = script ->
                {
                    final Command command = new Command(this.guildBot, channel.getIdLong(), config, script);
                    for (final String name : channel.getName().substring(4).split("-"))
                        this.commands.put(name, command);
                };
            }

            channel.getHistory().retrievePast(config.getInt("length", 1)).queue(l ->
            {
                Collections.reverse(l);
                final String script = l.stream()
                        .map(Message::getRawContent)
                        .collect(Collectors.joining("\n"));
                consumer.accept(script);
            });
        }
        catch (final Exception e)
        {
            final String message = "An error occurred while updating " + channel.getName();
            GuildBot.log.error(message, e);
            this.guildBot.handleThrowable(e, message);
            this.delete(channel);
        }

    }

    private synchronized void delete(final GenericTextChannelEvent event, final String name)
    {
        this.delete(event.getGuild().getIdLong(), name);
    }

    private synchronized void delete(final long guildId, final String name)
    {
        if (guildId != this.guildBot.getConfig().getLong("guildId", 0))
            return;

        if (name.startsWith("mthd-"))
            this.methods.remove(name.substring(5));
        else if (name.startsWith("vars-"))
            this.vars.remove(name.substring(5));
        else if (name.startsWith("cmd-"))
            for (final String cName : name.substring(4).split("\\-"))
                this.commands.remove(cName);
    }

    private synchronized void delete(final TextChannel channel)
    {
        this.delete(channel.getGuild().getIdLong(), channel.getName());
    }

    private synchronized void execute(final Command command, final MessageReceivedEvent event, final String args)
    {
        final EngineMap scriptEngines = new EngineMap();

        final ScriptContext context = scriptEngines.getContext();
        final Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);

        bindings.put("event", event);
        bindings.put("args", args == null ? "" : args);
        bindings.put("guildBot", this.guildBot);
        bindings.put("global", this.globalStore);

        final ScheduledExecutorService pool = this.guildBot.getThreadPool();
        for (final Entry<String, Variables> entry : this.vars.entrySet())
        {
            final Variables variables = entry.getValue();
            final ScriptEngine engine = scriptEngines.get(variables.getEngine());
            try
            {
                final Future<?> future = pool.submit(() -> engine.eval(variables.getExecutableScript()));
                future.get(variables.getConfig().getInt("timeout", this.guildBot.getConfig().getInt("timeout", 5)), TimeUnit.SECONDS);
            }
            catch (final Exception e)
            {
                final String varName = entry.getKey();
                GuildBot.log.error("An error occurred while evaluating the vars \"{}\"\n{}\n{}", varName, variables.getExecutableScript(), e);
                final String varContext = String.format("Trying to evaluate var: %#s", event.getJDA().getTextChannelById(varName));
                this.guildBot.handleThrowable(e, varContext);
            }
        }

        for (final Entry<String, Method> methodEntry : this.methods.entrySet())
        {
            final String methodName = methodEntry.getKey();
            final Method method = methodEntry.getValue();
            bindings.put(methodName, method.getInvokeableMethod(context));

            for (final Entry<Engine, ScriptEngine> engineEntry : scriptEngines.entrySet())
            {
                final Engine engine = engineEntry.getKey();
                final String script = method.getExecutableScript(engine);
                if (script != null)
                    try
                    {
                        engineEntry.getValue().eval(method.getExecutableScript(engine));
                    }
                    catch (final Exception e)
                    {
                        GuildBot.log.error("An error occurred while evaluating the method \"{}\"\n{}\n{}", methodName, method.getExecutableScript(engine), e);
                        final String methodContext = String.format("Trying to evaluate method: %#s", event.getJDA().getTextChannelById(methodName));
                        this.guildBot.handleThrowable(e, methodContext);
                    }
            }
        }

        final Future<?> future = pool.submit(() -> scriptEngines.get(command.getEngine()).eval(command.getExecutableScript()));

        Object result;

        try
        {
            result = future.get(command.getConfig().getInt("timeout", this.guildBot.getConfig().getInt("timeout", 5)), TimeUnit.SECONDS);
        }
        catch (final ExecutionException e)
        {
            result = e;
        }
        catch (TimeoutException | InterruptedException e)
        {
            future.cancel(true);
            result = e;
        }
        catch (final Exception e)
        {
            result = e;
        }

        if (result instanceof RestAction<?>)
            ((RestAction<?>) result).queue();
        else if (result instanceof String)
            event.getChannel().sendMessage((String) result).queue();
        else if (result instanceof Message)
            event.getChannel().sendMessage((Message) result).queue();
        else if (result instanceof MessageEmbed)
            event.getChannel().sendMessage((MessageEmbed) result).queue();
        else if (result instanceof MessageBuilder)
            event.getChannel().sendMessage(((MessageBuilder) result).build()).queue();
        else if (result instanceof EmbedBuilder)
            event.getChannel().sendMessage(((EmbedBuilder) result).build()).queue();
        else if (result instanceof Throwable)
        {
            GuildBot.log.error("An error occurred while execution a command\n{}\n{}", command.getExecutableScript(), result);
            final String commandContext = String.format("Trying to evaluate command: %#s", event.getJDA().getTextChannelById(command.getChannelId()));
            this.guildBot.handleThrowable((Throwable) result, commandContext);
            event.getChannel().sendMessage("An error occurred").queue();
        }
    }

    private synchronized void init()
    {
        final JsonObject config = this.guildBot.getConfig();

        final long guildId = config.getLong("guildId", 0);

        final JDA jda = this.guildBot.getJDA();
        final Guild guild = jda.getGuildById(guildId);

        if (guild == null)
        {
            GuildBot.log.error("Could not find a guild with id " + guildId + ", shutting bot down");
            jda.shutdown();
            return;
        }

        // get all relevant channels

        final List<TextChannel> channels = guild.getTextChannels().stream()
                .filter(CommandExecutor::isScriptChannel)
                .collect(Collectors.toList());

        final int channelCount = channels.size();

        // get configs in channel topic

        final TLongObjectMap<JsonObject> configs = new TLongObjectHashMap<>(channelCount);
        channels.forEach(c -> configs.put(c.getIdLong(), CommandExecutor.readConfig(c)));

        // get messages

        final CountDownLatch latch = new CountDownLatch(channelCount);
        final TLongObjectMap<String> messages = new TLongObjectHashMap<>(channelCount);
        channels.forEach(c -> c.getHistory().retrievePast(configs.get(c.getIdLong()).getInt("length", 1)).queue(l ->
        {
            Collections.reverse(l);
            messages.put(c.getIdLong(), l.stream()
                    .map(Message::getRawContent)
                    .collect(Collectors.joining("\n")));
            latch.countDown();
        }, t ->
        {
            GuildBot.log.error("An error occurred while retrieving the messages of channel \"" + c.getName() + "\"", t);
            this.guildBot.handleThrowable(t, "RestAction failure trying to retrieve history");
            latch.countDown();
        }));

        try
        {
            latch.await();
        }
        catch (final InterruptedException e)
        {
            e.printStackTrace();
        }

        this.methods = new ConcurrentHashMap<>();
        channels.stream().filter(c -> c.getName().startsWith("mthd-")).forEach(c ->
        {
            try
            {
                final String name = c.getName().substring(5);
                this.methods.put(name, new Method(this.guildBot, configs.get(c.getIdLong()), name, messages.get(c.getIdLong())));
            }
            catch (final Exception e)
            {
                this.delete(c);
                GuildBot.log.error("An error occurred while initialising " + c.getName(), e);
                this.guildBot.handleThrowable(e, "Setup for methods");
            }
        });

        this.vars = new ConcurrentHashMap<>();
        channels.stream().filter(c -> c.getName().startsWith("vars-")).forEach(c ->
        {
            try
            {
                final String name = c.getName().substring(5);
                this.vars.put(name, new Variables(this.guildBot, configs.get(c.getIdLong()), messages.get(c.getIdLong())));
            }
            catch (final Exception e)
            {
                this.delete(c);
                GuildBot.log.error("An error occurred while initialising " + c.getName(), e);
                this.guildBot.handleThrowable(e, "Setup for vars");
            }
        });

        this.commands = new ConcurrentHashMap<>();
        channels.stream().filter(c -> c.getName().startsWith("cmd-")).forEach(c ->
        {
            try
            {
                final Command command = new Command(this.guildBot, c.getIdLong(),
                        configs.get(c.getIdLong()), messages.get(c.getIdLong()));
                for (final String name : c.getName().substring(4).split("-"))
                    this.commands.put(name, command);
            }
            catch (final Exception e)
            {
                this.delete(c);
                GuildBot.log.error("An error occurred while initialising " + c.getName(), e);
                this.guildBot.handleThrowable(e, "Setup for commands");
            }
        });

        GuildBot.log.info("Accepting commands now");

        Presence presence = jda.getPresence();
        Game game = Game.of(config.getString("prefix", jda.getSelfUser().getAsMention() + ' ') + "help");
        presence.setPresence(OnlineStatus.ONLINE, game);

        jda.addEventListener(this);
    }

    private static boolean isScriptChannel(final TextChannel channel)
    {
        return channel.getName().startsWith("cmd-") || channel.getName().startsWith("mthd-") || channel.getName().startsWith("vars-");
    }

    private static JsonObject readConfig(final TextChannel channel)
    {
        return channel.getTopic() == null || channel.getTopic().isEmpty() ? new JsonObject() : JsonValue.readHjson(channel.getTopic()).asObject();
    }
}
