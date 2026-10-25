package net.mysterria.reporter.domain.beyonder.service;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import io.javalin.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.domain.beyonder.model.BeyonderFile;
import net.mysterria.reporter.domain.beyonder.model.BeyonderLogs;
import net.mysterria.reporter.domain.beyonder.model.BlessRequest;
import net.mysterria.reporter.domain.beyonder.model.BlessResult;
import net.mysterria.reporter.domain.beyonder.model.RebirthRequest;
import net.mysterria.reporter.domain.pathway.service.PathwayService;
import net.mysterria.reporter.util.api.ApiException;
import net.mysterria.reporter.util.file.ServerFiles;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class BeyonderService {

    private static final int DEFAULT_SPIRITUALITY_BOOST = 200;
    private static final int DEFAULT_ACTING_BOOST = 50;
    private static final int MAX_LOG_LINES = 10_000;

    private final MysterriaReporter plugin;
    private final CircleOfImaginationAPI coi;
    private final PathwayService pathwayService;

    public BlessResult bless(BlessRequest request) {
        UUID uuid = parseUuid(request.getUuid());
        requireName(request.getPlayerName());
        String pathway = coi.getAllPathwayNames().stream()
                .filter(name -> name.equalsIgnoreCase(request.getPathway()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown pathway, expected one of " + coi.getAllPathwayNames()));
        if (request.getSequence() == null || request.getSequence() < 0 || request.getSequence() > 9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "sequence must be between 0 and 9");
        }
        int spirituality = request.getSpiritualityBoost() != null ? request.getSpiritualityBoost() : DEFAULT_SPIRITUALITY_BOOST;
        int acting = request.getActingBoost() != null ? request.getActingBoost() : DEFAULT_ACTING_BOOST;
        if (spirituality < 0 || acting < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "spiritualityBoost and actingBoost cannot be negative");
        }
        if (coi.isBeyonder(uuid)) {
            throw new ApiException(HttpStatus.CONFLICT, "Player is already a Beyonder");
        }
        if (!coi.createBeyonderOffline(uuid, request.getPlayerName(), pathway, request.getSequence())) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CircleOfImagination refused to create the Beyonder");
        }
        if (spirituality > 0 && !coi.addSpiritualityOffline(uuid, spirituality)) {
            plugin.getLogger().warning("Blessed " + request.getPlayerName() + " but could not add " + spirituality + " spirituality");
        }
        if (acting > 0 && !coi.setPrimaryActingOffline(uuid, acting)) {
            plugin.getLogger().warning("Blessed " + request.getPlayerName() + " but could not set primary acting to " + acting);
        }

        pathwayService.invalidate(request.getPlayerName());
        plugin.getLogger().info("Blessed " + request.getPlayerName() + " (" + uuid + ") as " + pathway + " sequence " + request.getSequence());
        return new BlessResult(request.getPlayerName(), uuid.toString(), pathway, request.getSequence(), spirituality, acting);
    }

    public RebirthRequest rebirth(RebirthRequest request) {
        UUID uuid = parseUuid(request.getUuid());
        requireName(request.getPlayerName());
        if (!coi.isBeyonder(uuid)) {
            throw new ApiException(HttpStatus.CONFLICT, "Player is not a Beyonder");
        }
        if (!coi.destroyBeyonderOffline(uuid)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CircleOfImagination refused to destroy the Beyonder");
        }
        pathwayService.invalidate(request.getPlayerName());
        plugin.getLogger().info("Rebirthed " + request.getPlayerName() + " (" + uuid + ")");
        return request;
    }

    public BeyonderFile beyonderFile(String player) {
        return ServerFiles.readCoiFile("data/beyonders/" + player + ".yml")
                .map(data -> new BeyonderFile(player, data))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No beyonder data file for " + player));
    }

    public CompletableFuture<BeyonderLogs> logs(String player, int amount) {
        if (amount < 1 || amount > MAX_LOG_LINES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "amount must be between 1 and " + MAX_LOG_LINES);
        }
        CompletableFuture<Void> flushed = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "coi flush " + player);
            flushed.complete(null);
        });
        return flushed.thenApplyAsync(ignored -> ServerFiles.tailCoiFile("logs/" + player + ".log", amount)
                .map(lines -> new BeyonderLogs(player, amount, lines.size(), lines, !lines.isEmpty()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No activity log for " + player)));
    }

    private static UUID parseUuid(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid uuid is required");
        }
    }

    private static void requireName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "playerName is required");
        }
    }
}
