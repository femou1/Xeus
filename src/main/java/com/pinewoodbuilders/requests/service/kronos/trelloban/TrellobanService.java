
package com.pinewoodbuilders.requests.service.kronos.trelloban;

import com.pinewoodbuilders.requests.service.kronos.trelloban.trello.Datum;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Generated;
import java.util.List;

@Generated("jsonschema2pojo")
public class TrellobanService {

    @SerializedName("Loaded")
    @Expose
    private Boolean loaded;
    @SerializedName("Data")
    @Expose
    private List<Datum> data = null;
    @SerializedName("ServerVersion")
    @Expose
    private String serverVersion;

    public Boolean getLoaded() {
        return loaded;
    }

    public List<Datum> getData() {
        return data;
    }

    public String getServerVersion() {
        return serverVersion;
    }

}
