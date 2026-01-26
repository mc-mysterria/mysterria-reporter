package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeRequestBody;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.model.request.RebirthRequest;
import net.mysterria.reporter.model.response.RebirthResponse;
import net.mysterria.reporter.util.BlessUtil;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * Endpoint for rebirthing players (destroying Beyonder status for offline or online players)
 */
public class RebirthEndpoint {

    private final BlessUtil blessUtil;

    public RebirthEndpoint(CircleOfImaginationAPI coiAPI) {
        this.blessUtil = new BlessUtil(coiAPI);
    }

    @OpenApi(
            path = "/rebirth",
            methods = HttpMethod.POST,
            summary = "Rebirth a player",
            description = "Destroy Beyonder status for an online or offline player. Requires UUID and player name.",
            tags = {"Reporter"}
    )
    @BridgeEventHandler(requiresAuth = false, description = "Rebirth a player by destroying Beyonder", logRequests = true)
    public BridgeApiResponse<RebirthResponse> rebirthPlayer(@BridgeRequestBody RebirthRequest request) {
        try {
            // Validate required parameters
            if (request.getUuid() == null || request.getPlayerName() == null) {
                return BridgeApiResponse.success(RebirthResponse.builder()
                        .playerName(request.getPlayerName())
                        .uuid(request.getUuid())
                        .success(false)
                        .message("Missing required parameters: uuid, playerName")
                        .build());
            }

            // Parse UUID
            UUID playerUUID;
            try {
                playerUUID = UUID.fromString(request.getUuid());
            } catch (IllegalArgumentException e) {
                return BridgeApiResponse.success(RebirthResponse.builder()
                        .playerName(request.getPlayerName())
                        .uuid(request.getUuid())
                        .success(false)
                        .message("Invalid UUID format")
                        .build());
            }

            // Check if player is a Beyonder
            if (!blessUtil.isBeyonder(playerUUID)) {
                return BridgeApiResponse.success(RebirthResponse.builder()
                        .playerName(request.getPlayerName())
                        .uuid(request.getUuid())
                        .success(false)
                        .message("Player is not a Beyonder")
                        .build());
            }

            // Execute rebirth asynchronously to avoid blocking
            final UUID finalUUID = playerUUID;

            Bukkit.getScheduler().runTaskAsynchronously(
                    MysterriaReporter.getInstance(),
                    () -> {
                        boolean success = blessUtil.rebirthPlayer(finalUUID);

                        if (success) {
                            MysterriaReporter.getInstance().log(
                                    "Successfully rebirthed player " + request.getPlayerName() +
                                    " (" + request.getUuid() + ") - Beyonder status destroyed"
                            );
                        } else {
                            MysterriaReporter.getInstance().getLogger().warning(
                                    "Failed to rebirth player " + request.getPlayerName() +
                                    " (" + request.getUuid() + ") - player may not be a Beyonder or destruction failed"
                            );
                        }
                    }
            );

            // Return immediate response (async execution)
            return BridgeApiResponse.success(RebirthResponse.builder()
                    .playerName(request.getPlayerName())
                    .uuid(request.getUuid())
                    .success(true)
                    .message("Rebirth request submitted successfully")
                    .build());

        } catch (Exception e) {
            return BridgeApiResponse.success(RebirthResponse.builder()
                    .playerName(null)
                    .uuid(null)
                    .success(false)
                    .message("Error processing request: " + e.getMessage())
                    .build());
        }
    }
}
