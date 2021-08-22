package com.pinewoodbuilders.requests.service.kronos.database;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

public class GetAllPointsFromDatabaseService {

    private final List <Data> data = new LinkedList<>();
    public boolean hasData() {
        return data.size() > 0;
    }

    public List <Data> getData() {
        return data;
    }

    public class Data {
        @SerializedName("UserId")
        @Expose
        private Integer userId;
        @SerializedName("Points")
        @Expose
        private Integer points;
        @SerializedName("ExtraData")
        @Expose
        private ExtraData extraData;

        public Integer getUserId() {
            return userId;
        }

        public Integer getPoints() {
            return points;
        }

        @Nullable
        public ExtraData getExtraData() {
            return extraData;
        }
    }

    public class ExtraData {
        @SerializedName("Ranklock")
        @Expose
        private Integer ranklock;
        @SerializedName("Notes")
        @Expose
        private String notes;
        @SerializedName("TierEval")
        @Expose
        private Integer tierEval;

        @Nullable
        public Integer getRanklock() {
            return ranklock;
        }

        public String getNotes() {
            return notes;
        }

        @Nullable
        public Integer getTierEval() {
            return tierEval;
        }
    }
}
