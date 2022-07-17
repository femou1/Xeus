package com.pinewoodbuilders.contracts.verification;

public class VerificationEntity {

    private final long discordId;
    private final long robloxId;
    private final String robloxUsername;
    private final String provider;
    private final boolean mainAccount;

    public VerificationEntity(Long id, String username, Long discordId, String provider) {
        this.discordId = discordId;
        this.robloxId = id;
        this.robloxUsername = username;
        this.provider = provider;
        this.mainAccount = false;
    }

    public VerificationEntity(Long id, String username, Long discordId, String provider, boolean mainAccount) {
        this.discordId = discordId;
        this.robloxId = id;
        this.robloxUsername = username;
        this.provider = provider;
        this.mainAccount = mainAccount;
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

    public long getDiscordId() {
        return discordId;
    }

    public boolean isMainAccount() {
        return mainAccount;
    }
}
