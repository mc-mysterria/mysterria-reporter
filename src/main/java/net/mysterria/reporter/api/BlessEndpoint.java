package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeRequestBody;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.model.request.BlessRequest;
import net.mysterria.reporter.model.response.BlessResponse;
import net.mysterria.reporter.util.BlessUtil;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * Endpoint for blessing players (creating Beyonders for offline or online players)
 */
public class BlessEndpoint {

    private final BlessUtil blessUtil;

    public BlessEndpoint(CircleOfImaginationAPI coiAPI) {
        this.blessUtil = new BlessUtil(coiAPI);
    }

    @OpenApi(
            path = "/bless",
            methods = HttpMethod.POST,
            summary = "Bless a player",
            description = "Create a Beyonder for an online or offline player. Requires UUID, player name, pathway, sequence, and optional spirituality/acting boosts.",
            tags = {"Reporter"}
    )
    @BridgeEventHandler(description = "Bless a player by creating Beyonder", logRequests = true)
    public BridgeApiResponse<BlessResponse> blessPlayer(@BridgeRequestBody BlessRequest request) {
        try {
            // Validate required parameters
            if (request.getUuid() == null || request.getPlayerName() == null ||
                    request.getPathway() == null || request.getSequence() == null) {
                return BridgeApiResponse.success(BlessResponse.builder()
                        .playerName(request.getPlayerName())
                        .uuid(request.getUuid())
                        .success(false)
                        .message("Missing required parameters: uuid, playerName, pathway, sequence")
                        .build());
            }

            // Parse UUID
            UUID playerUUID;
            try {
                playerUUID = UUID.fromString(request.getUuid());
            } catch (IllegalArgumentException e) {
                return BridgeApiResponse.success(BlessResponse.builder()
                        .playerName(request.getPlayerName())
                        .uuid(request.getUuid())
                        .success(false)
                        .message("Invalid UUID format")
                        .build());
            }

            // Validate sequence range (0-9)
            if (request.getSequence() < 0 || request.getSequence() > 9) {
                return BridgeApiResponse.success(BlessResponse.builder()
                        .playerName(request.getPlayerName())
                        .uuid(request.getUuid())
                        .success(false)
                        .message("Sequence must be between 0 and 9")
                        .build());
            }

            // Check if already a Beyonder
            if (blessUtil.isBeyonder(playerUUID)) {
                return BridgeApiResponse.success(BlessResponse.builder()
                        .playerName(request.getPlayerName())
                        .uuid(request.getUuid())
                        .success(false)
                        .message("Player is already a Beyonder")
                        .build());
            }

            // Execute blessing asynchronously to avoid blocking
            final UUID finalUUID = playerUUID;
            final int spiritualityBoost = request.getSpiritualityBoost() != null ? request.getSpiritualityBoost() : 200;
            final int actingBoost = request.getActingBoost() != null ? request.getActingBoost() : 50;

            Bukkit.getScheduler().runTaskAsynchronously(
                    MysterriaReporter.getInstance(),
                    () -> {
                        boolean success = blessUtil.blessPlayer(
                                finalUUID,
                                request.getPlayerName(),
                                request.getPathway(),
                                request.getSequence(),
                                spiritualityBoost,
                                actingBoost
                        );

                        if (success) {
                            MysterriaReporter.getInstance().log(
                                    "Successfully blessed player " + request.getPlayerName() +
                                            " (" + request.getUuid() + ") with pathway " + request.getPathway() +
                                            " at sequence " + request.getSequence()
                            );
                        } else {
                            MysterriaReporter.getInstance().getLogger().warning(
                                    "Failed to bless player " + request.getPlayerName() +
                                            " (" + request.getUuid() + ") - player may already be a Beyonder or creation failed"
                            );
                        }
                    }
            );

            // Return immediate response (async execution)
            return BridgeApiResponse.success(BlessResponse.builder()
                    .playerName(request.getPlayerName())
                    .uuid(request.getUuid())
                    .success(true)
                    .message("Blessing request submitted successfully")
                    .pathway(request.getPathway())
                    .sequence(request.getSequence())
                    .spiritualityGranted(spiritualityBoost)
                    .actingGranted(actingBoost)
                    .build());

        } catch (Exception e) {
            return BridgeApiResponse.success(BlessResponse.builder()
                    .playerName(null)
                    .uuid(null)
                    .success(false)
                    .message("Error processing request: " + e.getMessage())
                    .build());
        }
    }
}
