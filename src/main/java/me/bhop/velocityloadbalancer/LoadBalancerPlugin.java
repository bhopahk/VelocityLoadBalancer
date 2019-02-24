package me.bhop.velocityloadbalancer;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Plugin(
        id = "loadbalancer",
        name = "LoadBalancer",
        version = "1.0",
        authors = {"bhop_"},
        description = "A server load balancer for Velocity."
)
public class LoadBalancerPlugin {
    private final ProxyServer proxyServer;
    private final Path dataDirectory;
    private final Logger logger;

    private final List<String> lobbies = new ArrayList<>();

    @Inject
    public LoadBalancerPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        Path confFile = dataDirectory.resolve("config.toml");
        try {
            if (!Files.exists(dataDirectory))
                Files.createDirectory(dataDirectory);
            if (!Files.exists(confFile)) {
                try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("config.toml")) {
                    if (stream != null)
                        Files.copy(stream, confFile);
                    else
                        throw new RuntimeException();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config file.", e);
        }
        Toml toml;
        try (BufferedReader reader = Files.newBufferedReader(confFile)) {
            toml = new Toml().read(reader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file.", e);
        }

        lobbies.addAll(toml.getList("lobby_servers"));
        logger.info("Loaded LoadBalancer. The following servers are treated as lobbies: " + lobbies.toString());
    }

    @Subscribe
    public void onJoin(ServerPreConnectEvent event) {
        Optional<RegisteredServer> opt = proxyServer.getAllServers().stream().filter(server -> lobbies.contains(server.getServerInfo().getName())).sorted(Comparator.comparingInt(s -> s.getPlayersConnected().size())).findFirst();
        if (!opt.isPresent()) {
            logger.warn("No valid lobby servers were detected, so joining player '" + event.getPlayer().getUsername() + "' was connected to the default server.");
            return;
        }
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(opt.get()));
    }
}
