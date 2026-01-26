package net.mysterria.reporter.util;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;

import java.util.UUID;

/**
 * Utility class for blessing and rebirthing players (online or offline)
 */
public class BlessUtil {

    private final CircleOfImaginationAPI coiAPI;

    public BlessUtil(CircleOfImaginationAPI coiAPI) {
        this.coiAPI = coiAPI;
    }

    /**
     * Bless a player by creating a Beyonder with specified pathway and sequence
     * Works for both online and offline players
     *
     * @param playerUUID        UUID of the player
     * @param playerName        Name of the player
     * @param pathway           Pathway to assign (e.g., "fool", "door", "sun")
     * @param sequence          Starting sequence (0-9, where 9 is lowest)
     * @param spiritualityBoost Initial spirituality to grant
     * @param actingBoost       Initial acting points to grant
     * @return true if successful, false if player is already a Beyonder or creation failed
     */
    public boolean blessPlayer(UUID playerUUID, String playerName, String pathway, int sequence, int spiritualityBoost, int actingBoost) {
        // Check if already a Beyonder
        if (coiAPI.isBeyonder(playerUUID)) {
            return false;
        }

        // Create the Beyonder using offline API
        boolean created = coiAPI.createBeyonderOffline(playerUUID, playerName, pathway, sequence);

        if (!created) {
            return false;
        }

        // Grant initial boosts
        if (spiritualityBoost > 0) {
            coiAPI.addSpiritualityOffline(playerUUID, spiritualityBoost);
        }

        if (actingBoost > 0) {
            coiAPI.setPrimaryActingOffline(playerUUID, actingBoost);
        }

        return true;
    }

    /**
     * Rebirth a player by destroying their Beyonder status
     * Works for both online and offline players
     *
     * @param playerUUID UUID of the player
     * @return true if successful, false if player is not a Beyonder or destruction failed
     */
    public boolean rebirthPlayer(UUID playerUUID) {
        // Check if player is a Beyonder
        if (!coiAPI.isBeyonder(playerUUID)) {
            return false;
        }

        // Destroy the Beyonder using offline API
        return coiAPI.destroyBeyonderOffline(playerUUID);
    }

    /**
     * Check if a player is a Beyonder
     *
     * @param playerUUID UUID of the player
     * @return true if player is a Beyonder
     */
    public boolean isBeyonder(UUID playerUUID) {
        return coiAPI.isBeyonder(playerUUID);
    }
}
