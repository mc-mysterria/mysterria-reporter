package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.model.response.BeyonderLogsResponse;
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
            @BridgePathParam("player") String player,
            @BridgePathParam("amount") String amount) {

        String sanitizedName = FileReaderUtil.sanitizePlayerName(player);
        int amountInt;

        try {
            amountInt = Integer.parseInt(amount);
            if (amountInt <= 0 || amountInt > 10000) {
                amountInt = Math.clamp(amountInt, 1, 10000);
            }
        } catch (NumberFormatException e) {
            return BridgeApiResponse.success(BeyonderLogsResponse.builder()
                    .player(player)
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
        List<String> logLines = FileReaderUtil.readLastLines(filePath, amountInt);

        return BridgeApiResponse.success(BeyonderLogsResponse.builder()
                .player(player)
                .requested(amountInt)
                .returned(logLines.size())
                .logs(logLines)
                .found(!logLines.isEmpty())
                .build());
    }
}
