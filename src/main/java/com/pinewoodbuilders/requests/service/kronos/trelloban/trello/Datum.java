
package com.pinewoodbuilders.requests.service.kronos.trelloban.trello;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Generated;
import java.util.List;

@Generated("jsonschema2pojo")
public class Datum {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("closed")
    @Expose
    private Boolean closed;
    @SerializedName("softLimit")
    @Expose
    private Object softLimit;
    @SerializedName("subscribed")
    @Expose
    private Boolean subscribed;
    @SerializedName("cards")
    @Expose
    private List<Card> cards = null;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Boolean getClosed() {
        return closed;
    }

    public Object getSoftLimit() {
        return softLimit;
    }

    public Boolean getSubscribed() {
        return subscribed;
    }

    public List<Card> getCards() {
        return cards;
    }

}
