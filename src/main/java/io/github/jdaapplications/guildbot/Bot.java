/*
 * Copyright 2017 John Grosh (john.a.grosh@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jdaapplications.guildbot;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.utils.SimpleLog;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Bot extends ListenerAdapter
{
    public static final String GUILDID = "197072474679017472";
    public static final String ENV     = "202761691367211008";
    public static final String PREFIX  = "&&";
    public static final SimpleLog LOG  = SimpleLog.getLog("Core");
    private final ThreadPoolExecutor executor;

    public Bot()
    {
        executor =new ThreadPoolExecutor(5, 15, 2L, TimeUnit.MINUTES, new LinkedBlockingQueue<>(), r ->
        {
            final Thread t = new Thread(r, "Command Execution");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    public void onReady(ReadyEvent event)
    {
        System.getProperties().setProperty("guildbot_start_time", ""+System.currentTimeMillis());
        LOG.info("Logged in as "+event.getJDA().getSelfUser().getName()+" on "+event.getJDA().getGuilds().size()+" guilds");
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) 
    {
        String content0 = event.getMessage().getRawContent();
        if(content0.startsWith("&&") && !event.getAuthor().isBot())
        {
            executor.submit(() ->
            {
                final String content = content0.substring(2);
                String[] split = content.split("\\s+",2);
                String command = split[0];
                String args = split.length==1 || split[1]==null ? "" : split[1];
                Guild guild = event.getJDA().getGuildById(GUILDID);
                List<TextChannel> channels = new LinkedList<>(guild.getTextChannels()); // Thread safe
                if(command.equalsIgnoreCase("help"))
                {
                    String str = "**HELP FILE**";
                    for(TextChannel tchan : channels)
                        if(tchan.getName().startsWith("cmd-"))
                            str+="\n`"+tchan.getName().substring(4).split("-")[0]+"` - "+(tchan.getTopic()==null ? "no description" : tchan.getTopic());
                    event.getChannel().sendMessage(str).queue();
                }
                else
                    for(TextChannel tchan : channels)
                    {
                        if(tchan.getName().startsWith("cmd-") && Arrays.asList(tchan.getName().substring(4).split("-")).contains(command.toLowerCase()))
                        {
                            String script;
                            try
                            {
                                MessageHistory mh = new MessageHistory(tchan);
                                script = mh.retrievePast(1).complete().get(0).getRawContent();
                            } catch(Exception e)
                            {
                                event.getChannel().sendMessage("That command is not written!").queue();
                                return;
                            }
                            ScriptEngineManager manager = new ScriptEngineManager();
                            ScriptEngine engine = manager.getEngineByName ("Nashorn");
                            engine.put("jda",event.getJDA());
                            engine.put("event",event);
                            engine.put("guild",event.getGuild());
                            engine.put("channel",event.getChannel());
                            engine.put("me",event.getJDA().getSelfUser());
                            engine.put("author",event.getAuthor());
                            engine.put("message",event.getMessage());
                            engine.put("args",args);
                            putFunctions(engine, guild);
                            putConstants(engine, guild);
                            String result = null;
                            try 
                            {
                                Object o = engine.eval(script);
                                if(o!=null)
                                    result = o.toString();
                            } catch (ScriptException e) {
                                result = "SCRIPTERROR```\n"+e.toString()+"```";
                            } catch (Exception e)
                            {
                                result = "ERROR```\n"+e.toString()+"```";
                            }
                            try
                            {
                                if(result!=null)
                                    event.getChannel().sendMessage(result).queue();
                            } catch(Exception e)
                            {
                                event.getChannel().sendMessage("SENDERROR```\n"+e.toString()+"```").queue();
                            }
                        
                        }
                    }
            });
        }
        
    }
    
    private void putFunctions(ScriptEngine engine, Guild guild)
    {
        new LinkedList<>(guild.getTextChannels())
            .stream()
            .filter(
                channel -> 
                {
                    return channel.getName().startsWith("mthd-") && channel.getName().length() > "mthd-".length();
                })
            .forEach(
                channel -> 
                {
                   try 
                   {
                       List<Message> list = new MessageHistory(channel).retrievePast(1).complete();
                       if(list.isEmpty()) return;
                       String content = list.get(0).getRawContent();
                       engine.eval(String.format("function %s(%s) {\n%s\n};", channel.getName().substring("mthd-".length()), channel.getTopic(), content));
                   } catch(Exception e) 
                   {
                        LOG.warn(e.getMessage());
                   }
                });
    }
    
    private void putConstants(ScriptEngine engine, Guild guild)
    {
        assert guild != null && engine != null;
        TextChannel channel = guild.getJDA().getTextChannelById(ENV);
        List<Message> hist = channel.getHistory().retrievePast(1).complete();
        Message vars = (hist.isEmpty() ? null : hist.get(0));
        if (vars == null)
            return;
        try
        {
            engine.eval(vars.getRawContent());
        } catch (Exception e)
        {
            LOG.fatal(e);
        }
    }
}
