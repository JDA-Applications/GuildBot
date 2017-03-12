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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.exceptions.RateLimitedException;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class GuildBot
{
    public static void main(String[] args) throws IOException, LoginException, IllegalArgumentException, RateLimitedException
    {
        new JDABuilder(AccountType.BOT)
                .setToken(Files.readAllLines(Paths.get(".token")).get(0))
                .addListener(new Bot())
                .setGame(Game.of("loading..."))
                .setStatus(OnlineStatus.DO_NOT_DISTURB)
                .buildAsync();
    }
}
