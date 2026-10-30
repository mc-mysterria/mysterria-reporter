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
import net.mysterria.reporter.domain.profile.service.BackendClient;
import net.mysterria.reporter.domain.profile.service.LandLookup;
import net.mysterria.reporter.domain.profile.service.PresenceService;
import net.mysterria.reporter.domain.profile.service.ProfilePushService;
import net.mysterria.reporter.domain.profile.service.ProfileScanService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

@Getter
public class MysterriaReporter extends JavaPlugin {

    private PathwayService pathwayService;
    private ProfilePushService profilePushService;
    private ProfileScanService profileScanService;
    private UUID worldUid;

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
        if (config.isBackendEnabled()) {
            startProfiles(coi, config);
        }
        new ReporterCommand(this);
    }

    private void startProfiles(CircleOfImaginationAPI coi, ReporterConfig config) {
        worldUid = getServer().getWorlds().getFirst().getUID();
        profilePushService = new ProfilePushService(new BackendClient(config, getLogger()), config, getLogger(), worldUid);
        LandLookup lands = getServer().getPluginManager().isPluginEnabled("Lands") ? new LandLookup(this) : null;
        PresenceService presence = new PresenceService(this, profilePushService, lands);
        presence.loadTitlePermissions();
        profileScanService = new ProfileScanService(coi, profilePushService, getLogger());
        getLogger().info("Profile push enabled for world " + worldUid + (config.isBackendDryRun() ? " (dry run)" : ""));
        long heartbeatTicks = config.getHeartbeatSeconds() * 20L;
        getServer().getScheduler().runTaskTimer(this, presence::beat, heartbeatTicks, heartbeatTicks);
        getServer().getScheduler().runTaskTimerAsynchronously(this, profileScanService::scan, 600L, config.getScanIntervalMinutes() * 1200L);
    }

    @Override
    public void onDisable() {
        if (pathwayService != null) {
            pathwayService.shutdown();
        }
    }
}
