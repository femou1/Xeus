package com.pinewoodbuilders.requests.service.kronos.blacklist;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class KronosCheckIfUserIsBlacklistedService {

    public HashMap<Integer, Boolean> data;

    public HashMap<Integer, Boolean> getData() {
        return data;
    }

    public boolean hasData() {
        return getData() != null && !getData().isEmpty();
    }
}
