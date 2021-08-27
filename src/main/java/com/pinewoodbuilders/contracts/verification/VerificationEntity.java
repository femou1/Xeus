package com.pinewoodbuilders.contracts.verification;

public class VerificationEntity {

    private long discordId;
    private long robloxId;
    private String robloxUsername;
    private String provider;

    public VerificationEntity(Long id, String username, Long discordId, String provider) {
        this.discordId = discordId;
        this.robloxId = id;
        this.robloxUsername = username;
        this.provider = provider;
    }

    public long getRobloxId() {
        return robloxId;
    }

    public String getRobloxUsername() {
        return robloxUsername;
    }

    public String getProvider() {
        return provider;
    }

    public Long getDiscordId() {
        return discordId;
    }
}
