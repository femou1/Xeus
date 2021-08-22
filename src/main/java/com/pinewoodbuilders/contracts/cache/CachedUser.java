package com.pinewoodbuilders.contracts.cache;

import net.dv8tion.jda.api.entities.User;

public class CachedUser
{
    private final boolean isBot;
    private final String mentionable;
    private final String getAsFormattedName;
    private final String getEffectiveAvatarUrl;

    public CachedUser(User user)
    {
        this.isBot = user.isBot();
        this.mentionable = user.getAsMention();
        this.getAsFormattedName = user.getName() + "#" + user.getDiscriminator();
        this.getEffectiveAvatarUrl = user.getEffectiveAvatarUrl();
    }

    public boolean isBot()
    {
        return isBot;
    }

    public String getAsMention()
    {
        return mentionable;
    }

    public String getGetAsFormattedName() {return getAsFormattedName;}

    public String getGetEffectiveAvatarUrl() {return getEffectiveAvatarUrl;}
}
