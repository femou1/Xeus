package com.pinewoodbuilders.moderation.global.filter;

import com.pinewoodbuilders.Constants;
import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.database.collection.DataRow;
import com.pinewoodbuilders.language.I18n;
import com.pinewoodbuilders.moderation.global.filter.filter.LinkContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class LinkFilterManager {

    private final Logger log = LoggerFactory.getLogger(LinkFilterManager.class);
    private final HashMap <Long, HashSet <LinkContainer>> links = new HashMap<>();

    private final Xeus avaire;

    /**
     * Creates the mute manager instance with the given Xeus application instance,
     * the mute manager will sync the links entities from the database into memory.
     *
     * @param avaire The main Xeus instance.
     */
    public LinkFilterManager(Xeus avaire) {
        this.avaire = avaire;

        syncWithDatabase();
    }
    
    public void registerLink(Long mainGroupId, String topLevelDomain, int action) throws SQLException {
        if (!links.containsKey(mainGroupId)) {
            links.put(mainGroupId, new HashSet<>());
        }


        if (hasLink(mainGroupId, topLevelDomain)) {
            removeLink(mainGroupId, topLevelDomain);
        }

        avaire.getDatabase().newQueryBuilder(Constants.LINK_FILTER_TABLE_NAME).insert(statement -> {
            statement.set("hostname", topLevelDomain);
            statement.set("action", action);
            statement.set("main_group_id", mainGroupId);
        });

        links.get(mainGroupId).add(new LinkContainer(mainGroupId, topLevelDomain, action));
    }

    public void removeLink(long mainGroupId, String topLevelDomain) throws SQLException {
        if (!links.containsKey(mainGroupId)) {
            return;
        }

        final boolean[] removedEntities = { false };
        synchronized (links) {
            links.get(mainGroupId).removeIf(next -> {

                if (!next.isSame(mainGroupId, topLevelDomain)) {
                    return false;
                }

                removedEntities[0] = true;
                return true;
            });
        }

        if (removedEntities[0]) {
            avaire.getDatabase().newQueryBuilder(Constants.LINK_FILTER_TABLE_NAME).where("main_group_id", mainGroupId).andWhere("hostname", topLevelDomain).delete();
        }
    }



    public boolean hasLink(long mainGroupId, String topLevelDomain) {
        if (!links.containsKey(mainGroupId)) {
            return false;
        }

        for (LinkContainer link : links.get(mainGroupId)) {
            if (link.getTopLevelDomain().equals(topLevelDomain)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Gets the total amount of links currently stored in memory, this includes
     * permanent and temporary links.
     *
     * @return The total amount of links stored.
     */
    public int getTotalAmountOfLinks() {
        int totalLinks = 0;
        for (Map.Entry <Long, HashSet <LinkContainer>> entry : links.entrySet()) {
            totalLinks += entry.getValue().size();
        }
        return totalLinks;
    }



    /**
     * Gets the map of links currently stored, where the key is the guild ID for the
     * links, and the value is a set of mute containers, which holds the information
     * about each individual mute.
     *
     * @return The complete map of links currently stored.
     */
    public HashMap<Long, HashSet<LinkContainer>> getLinks() {
        return links;
    }

    public LinkContainer getLinkContainer(long mainGroupId, String topLevelDomain) {
        if (!links.containsKey(mainGroupId)) {
            return new LinkContainer(mainGroupId, topLevelDomain, 0);
        }

        for (LinkContainer link : links.get(mainGroupId)) {
            if (link.getTopLevelDomain().equals(topLevelDomain)) {
                return link;
            }
        }
        return new LinkContainer(mainGroupId, topLevelDomain, 0);
    }

    private void syncWithDatabase() {
        log.info("Syncing links with the filter database...");

        String query = I18n.format(
            "SELECT `main_group_id`, `hostname`, `action` FROM `{0}`;",
            Constants.LINK_FILTER_TABLE_NAME);

        try {
            int size = getTotalAmountOfLinks();
            for (DataRow row : avaire.getDatabase().query(query)) {
                long mgi = row.getLong("main_group_id");

                if (!links.containsKey(mgi)) {
                    links.put(mgi, new HashSet<>());
                }

                links.get(mgi).add(new LinkContainer(mgi, row.getString("hostname"), row.getInt("action")));
            }

            log.info("Syncing complete! {} links entries found.",
                getTotalAmountOfLinks() - size);
        } catch (SQLException e) {
            Xeus.getLogger().error("ERROR: ", e);
        }
    }

}
