package com.pinewoodbuilders.requests.service.user.inventory;

import java.util.List;

public class RobloxGamePassService {
    public String previousPageCursor;
    public String nextPageCursor;
    public List<Datum> data;

    public List<Datum> getData() {
        return data;
    }

    public boolean hasData() {
        return data.size() > 0;
    }

    public class Datum {
        public String type;
        public int id;
        public String name;
        public Object instanceId;

        public String getType() {
            return type;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Object getInstanceId() {
            return instanceId;
        }
    }
}
