package net.mysterria.reporter;

import dev.ua.ikeepcalm.catwalk.hub.webserver.services.CatWalkWebserverService;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import lombok.Getter;
import net.mysterria.reporter.command.admin.ReporterCommand;
import net.mysterria.reporter.config.ReporterConfig;
import net.mysterria.reporter.domain.beyonder.endpoint.BeyonderEndpoints;
import net.mysterria.reporter.domain.beyonder.service.BeyonderService;
import net.mysterria.reporter.domain.pathway.endpoint.PathwayEndpoints;
import net.mysterria.reporter.domain.pathway.service.PathwayService;
import net.mysterria.reporter.domain.report.endpoint.ReportEndpoints;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class MysterriaReporter extends JavaPlugin {

    private PathwayService pathwayService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        CircleOfImaginationAPI coi = getServer().getServicesManager().load(CircleOfImaginationAPI.class);
        CatWalkWebserverService webserver = getServer().getServicesManager().load(CatWalkWebserverService.class);
        if (coi == null || webserver == null) {
            getLogger().severe("CircleOfImagination or CatWalk is not available, disabling");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ReporterConfig config = ReporterConfig.load(getConfig());
        pathwayService = new PathwayService(this, coi, config);
        BeyonderService beyonderService = new BeyonderService(this, coi, pathwayService);

        webserver.registerHandlers(new PathwayEndpoints(pathwayService, config));
        webserver.registerHandlers(new BeyonderEndpoints(beyonderService, config));
        webserver.registerHandlers(new ReportEndpoints(config));
        new ReporterCommand(this);
    }

    @Override
    public void onDisable() {
        if (pathwayService != null) {
            pathwayService.shutdown();
        }
    }
}
