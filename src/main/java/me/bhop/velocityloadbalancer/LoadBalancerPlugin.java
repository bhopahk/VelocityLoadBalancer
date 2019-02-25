package me.bhop.velocityloadbalancer;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Plugin(
        id = "loadbalancer",
        name = "LoadBalancer",
        version = "1.1",
        authors = {"bhop_"},
        description = "A server load balancer for Velocity."
)
public class LoadBalancerPlugin {
    private final ProxyServer proxyServer;
    private final Logger logger;

    private final List<RegisteredServer> lobbies = new ArrayList<>();
    private final List<UUID> connectedPlayers = new ArrayList<>();

    @Inject
    public LoadBalancerPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        for (String serverName : proxyServer.getConfiguration().getAttemptConnectionOrder())
            proxyServer.getServer(serverName).ifPresent(lobbies::add);
        logger.info("Loaded LoadBalancer. The following servers are treated as lobbies: " + lobbies.stream().map(server -> server.getServerInfo().getName()).collect(Collectors.joining(", ")));
    }

    @Subscribe
    public void onLeave(DisconnectEvent event) {
        connectedPlayers.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onKick(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect())
            return;
        Optional<RegisteredServer> opt = proxyServer.getAllServers().stream().filter(server -> server != event.getServer()).filter(lobbies::contains).min(Comparator.comparingInt(s -> s.getPlayersConnected().size()));
        opt.ifPresent(registeredServer -> event.setResult(KickedFromServerEvent.RedirectPlayer.create(registeredServer)));
    }

    @Subscribe
    public void onJoin(ServerPreConnectEvent event) {
        if (!connectedPlayers.contains(event.getPlayer().getUniqueId())) {
            connectedPlayers.add(event.getPlayer().getUniqueId());
            Optional<RegisteredServer> opt = proxyServer.getAllServers().stream().filter(lobbies::contains).min(Comparator.comparingInt(s -> s.getPlayersConnected().size()));
            if (!opt.isPresent()) {
                logger.warn("No valid lobby servers were detected, so joining player '" + event.getPlayer().getUsername() + "' was connected to the default server.");
                return;
            }
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(opt.get()));
        }
    }
}