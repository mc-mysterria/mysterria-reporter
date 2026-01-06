package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.model.BeyonderLogsResponse;
import net.mysterria.reporter.util.FileReaderUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.List;

public class BeyonderLogsEndpoint {

    @OpenApi(
            path = "/beyonder/logs/{player}/{amount}",
            methods = HttpMethod.GET,
            summary = "Get beyonder activity logs",
            description = "Returns the last N lines from a beyonder's activity log file",
            tags = {"Reporter"}
    )
    @BridgeEventHandler(description = "Get beyonder's activity logs", logRequests = true)
    public BridgeApiResponse<BeyonderLogsResponse> getBeyonderLogs(
            @BridgePathParam("player") String playerName,
            @BridgePathParam("amount") String amountStr) {

        String sanitizedName = FileReaderUtil.sanitizePlayerName(playerName);
        int amount;

        try {
            amount = Integer.parseInt(amountStr);
            if (amount <= 0 || amount > 10000) {
                amount = Math.min(Math.max(amount, 1), 10000);
            }
        } catch (NumberFormatException e) {
            return BridgeApiResponse.success(BeyonderLogsResponse.builder()
                    .player(playerName)
                    .requested(0)
                    .returned(0)
                    .logs(Collections.emptyList())
                    .found(false)
                    .build());
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "coi flush " + sanitizedName);

            }
        }.runTask(MysterriaReporter.getInstance());

        String filePath = "plugins/CircleOfImagination/logs/" + sanitizedName + ".log";
        List<String> logLines = FileReaderUtil.readLastLines(filePath, amount);

        return BridgeApiResponse.success(BeyonderLogsResponse.builder()
                .player(playerName)
                .requested(amount)
                .returned(logLines.size())
                .logs(logLines)
                .found(!logLines.isEmpty())
                .build());
    }
}
