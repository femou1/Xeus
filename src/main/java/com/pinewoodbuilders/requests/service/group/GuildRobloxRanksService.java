package com.pinewoodbuilders.requests.service.group;

import java.util.List;

public class GuildRobloxRanksService {

    public List<GroupRankBinding> groupRankBindings;

    public List<GroupRankBinding> getGroupRankBindings() {
        return groupRankBindings;
    }

    public void setGroupRankBindings(List<GroupRankBinding> binds) {
        this.groupRankBindings = binds;
    }

    public static class Group {
        public Group(String groupId, List<Integer> groupRanks){
            this.id = groupId;
            this.ranks = groupRanks;
        }

        private String id;
        private List<Integer> ranks;

        public String getId() {
            return id;
        }

        public List<Integer> getRanks() {
            return ranks;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setRanks(List<Integer> ranks) {
            this.ranks = ranks;
        }
    }

    public static class GroupRankBinding {
        public GroupRankBinding(String roleId, List<Group> groups){
            this.role = roleId;
            this.groups = groups;
        }

        private String role;
        private List<Group> groups;

        public String getRole() {
            return role;
        }

        public List<Group> getGroups() {
            return groups;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public void setGroups(List<Group> groups) {
            this.groups = groups;
        }
    }

}
