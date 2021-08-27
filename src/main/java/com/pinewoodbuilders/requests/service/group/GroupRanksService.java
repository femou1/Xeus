package com.pinewoodbuilders.requests.service.group;

import java.util.ArrayList;
import java.util.List;

public class GroupRanksService {

    private Integer groupId;
    private final List<Role> roles = new ArrayList<>();

    public Integer getGroupId() {
        return groupId;
    }

    public List<Role> getRoles() {
        return roles;
    }


    public class Role {
        private Integer id;
        private String name;
        private Integer rank;
        private Integer memberCount;

        public Integer getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Integer getRank() {
            return rank;
        }

        public Integer getMemberCount() {
            return memberCount;
        }

    }
}
