package com.pinewoodbuilders.contracts.verification;

public enum VerificationProviders {
    ROVER ("rover", "<:rover:738065752866947194>"),
    BLOXLINK ("bloxlink", "<:bloxlink:863168888900812811>"),
    ROWIFI("rowifi", "<:rowifi:914145206780190770>"),
    PINEWOOD ("pinewood", "<:xeus:801483709592240160>");

    public String provider;
    public String emoji;

    VerificationProviders(String provider, String emoji) {
        this.emoji = emoji;
        this.provider = provider;
    }

    public static VerificationProviders resolveProviderFromProvider(String name){
        for(VerificationProviders provider : VerificationProviders.values()){
            if(name.equals(provider.getProvider())) return provider;
        }
        return null;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }
}
