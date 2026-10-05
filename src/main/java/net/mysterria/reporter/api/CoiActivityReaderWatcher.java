package net.mysterria.reporter.api;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;

public final class CoiActivityReaderWatcher implements Listener {
    private final CoiActivityReaderSource source;

    public CoiActivityReaderWatcher(CoiActivityReaderSource source) {
        this.source = source;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegister(ServiceRegisterEvent event) {
        refreshIfTracked(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnregister(ServiceUnregisterEvent event) {
        refreshIfTracked(event);
    }

    private void refreshIfTracked(ServiceEvent event) {
        if (source.tracks(event.getProvider().getService())) source.refresh();
    }
}
