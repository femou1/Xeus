package com.pinewoodbuilders.requests.service.user;

public class GetRobloxIdFromUsername {


    private int id;
    private String username;
    private Object avatarUri;
    private boolean avatarFinal;
    private boolean isOnline;

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Object getAvatarUri() {
        return avatarUri;
    }

    public boolean isAvatarFinal() {
        return avatarFinal;
    }

    public boolean isOnline() {
        return isOnline;
    }
}
