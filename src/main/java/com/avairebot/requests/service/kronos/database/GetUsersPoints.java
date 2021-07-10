package com.avairebot.requests.service.kronos.database;

import java.util.List;

public class GetUsersPoints {

    private List<Data> data;

    public List<Data> getData() {
        return data;
    }

    public boolean hasData() {
        return data.size() > 0;
    }

    public class Data {
        private Integer userId;
        private Integer points;

        public Integer getUserId() {
            return userId;
        }

        public Integer getPoints() {
            return points;
        }
    }
}
