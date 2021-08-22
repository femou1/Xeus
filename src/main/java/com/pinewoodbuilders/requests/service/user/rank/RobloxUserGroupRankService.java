package com.pinewoodbuilders.requests.service.user.rank;

import java.util.Date;
import java.util.List;

public class RobloxUserGroupRankService {

    public List<Data> data;

    public List<Data> getData() {
        return data;
    }

    public boolean hasData() {
        return getData() != null && !getData().isEmpty();
    }

    public class Data {
        private Group group;
        private Role role;

        public Group getGroup() {
            return group;
        }

        public Role getRole() {
            return role;
        }

        public class Owner {
            public String getBuildersClubMembershipType() {
                return buildersClubMembershipType;
            }

            public long getUserId() {
                return userId;
            }

            public String getUsername() {
                return username;
            }

            public String getDisplayName() {
                return displayName;
            }

            private String buildersClubMembershipType;
            private long userId;
            private String username;
            private String displayName;
        }

        public class Poster {
            public String getBuildersClubMembershipType() {
                return buildersClubMembershipType;
            }

            public long getUserId() {
                return userId;
            }

            public String getUsername() {
                return username;
            }

            public String getDisplayName() {
                return displayName;
            }

            private String buildersClubMembershipType;
            private long userId;
            private String username;
            private String displayName;
        }

        public class Shout {
            private String body;
            private Poster poster;
            private Date created;
            private Date updated;

            public String getBody() {
                return body;
            }

            public Poster getPoster() {
                return poster;
            }

            public Date getCreated() {
                return created;
            }

            public Date getUpdated() {
                return updated;
            }
        }

        public class Group {
            private int id;
            private String name;
            private String description;
            private Owner owner;
            private Shout shout;
            private long memberCount;
            private boolean isBuildersClubOnly;
            private boolean publicEntryAllowed;

            public long getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public String getDescription() {
                return description;
            }

            public Owner getOwner() {
                return owner;
            }

            public Shout getShout() {
                return shout;
            }

            public long getMemberCount() {
                return memberCount;
            }

            public boolean isBuildersClubOnly() {
                return isBuildersClubOnly;
            }

            public boolean isPublicEntryAllowed() {
                return publicEntryAllowed;
            }
        }

        public class Role {
            private int id;
            private String name;
            private int rank;
            private long memberCount;

            public int getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public int getRank() {
                return rank;
            }

            public long getMemberCount() {
                return memberCount;
            }
        }

        public class Datum {
            private Group group;
            private Role role;
            private boolean isPrimaryGroup;

            public Group getGroup() {
                return group;
            }

            public Role getRole() {
                return role;
            }

            public boolean isPrimaryGroup() {
                return isPrimaryGroup;
            }
        }

    }
}
