package net.mysterria.reporter.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.util.FileReaderUtil;
import org.bukkit.Bukkit;

public class BalanceReportEndpoint {

    private static final String BALANCE_REPORT_PATH = "plugins/CircleOfImagination/balance/export-latest.json";

    @OpenApi(
            path = "/balance/report",
            methods = HttpMethod.GET,
            summary = "Get the latest balance report",
            description = "Returns the latest CircleOfImagination balance export as JSON",
            tags = {"Reporter"}
    )
    @BridgeEventHandler(description = "Get the latest CircleOfImagination balance report", logRequests = true)
    public BridgeApiResponse<JsonElement> getBalanceReport() {
        String fileContent = FileReaderUtil.readYamlFile(BALANCE_REPORT_PATH);
        if (fileContent == null) {
            return BridgeApiResponse.success(JsonNull.INSTANCE);
        }

        try {
            return BridgeApiResponse.success(JsonParser.parseString(fileContent));
        } catch (RuntimeException exception) {
            Bukkit.getLogger().warning("[MysterriaReporter] Invalid balance report JSON: " + exception.getMessage());
            return BridgeApiResponse.success(JsonNull.INSTANCE);
        }
    }
}
