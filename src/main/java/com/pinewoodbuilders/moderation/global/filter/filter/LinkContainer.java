package com.pinewoodbuilders.moderation.global.filter.filter;


import javax.annotation.Nonnull;

@SuppressWarnings("WeakerAccess")
public record LinkContainer(long mainGroupId, String topLevelDomain, int action) {

    public boolean isSame(@Nonnull LinkContainer container) {
        return isSame(container.getMainGroupId(), container.getTopLevelDomain());
    }


    public boolean isSame(long mainGroupId, String topLevelDomain) {
        return getMainGroupId() == mainGroupId
            && getTopLevelDomain().equals(topLevelDomain);
    }

    public long getMainGroupId() {
        return mainGroupId;
    }

    public String getTopLevelDomain() {
        return topLevelDomain;
    }

    public int getAction() {
        return action;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof LinkContainer && isSame((LinkContainer) obj);
    }

    @Override
    public String toString() {
        return String.format("LinkContainer={MGI=%s, TLD=%s, A=%s}",
            getMainGroupId(), getTopLevelDomain(), getAction()
        );
    }
}
