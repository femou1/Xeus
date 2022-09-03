package com.pinewoodbuilders.contracts.oauth;

public record OAuthRequest(Long discordId, Long robloxId, String state) {}
