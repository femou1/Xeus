package com.pinewoodbuilders.contracts.verification;

public enum VerificationProviders {
    ROVER ("rover", "<:rover:738065752866947194>"),
    BLOXLINK ("bloxlink", "<:bloxlink:863168888900812811>"),
    PINEWOOD ("pinewood", "<:xeus:801483709592240160>");

    public String provider;
    public String emoji;

    VerificationProviders(String provider, String emoji) {
        this.emoji = emoji;
        this.provider = provider;
    }

    public static VerificationProviders resolveProviderFromProvider(String provider) {
        if (provider.equalsIgnoreCase("pinewood")) {
            return PINEWOOD;
        } else if (provider.equalsIgnoreCase("rover")) {
            return ROVER;
        } else if (provider.startsWith("bloxlink")) {
            return BLOXLINK;
        }
        return null;
    }
}
