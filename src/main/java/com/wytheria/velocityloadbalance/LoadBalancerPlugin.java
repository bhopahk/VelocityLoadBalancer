package com.wytheria.velocityloadbalance;

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
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Plugin(
        id = "load_balancer",
        name = "LoadBalancer",
        version = "1.0",
        authors = { "SuperPuppyz" },
        description = "A server load balancer for Velocity based redis sync"
)
public class LoadBalancerPlugin {

    private final ProxyServer proxyServer;
    private final Logger logger;

    private final List<UUID> connectedPlayers = new ArrayList<>();

    @Inject
    public LoadBalancerPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        List<RegisteredServer> lobbies = getAllServers();
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
        Optional<RegisteredServer> opt = getAllServers().stream().filter(server -> server != event.getServer()).min(Comparator.comparingInt(s -> s.getPlayersConnected().size()));
        opt.ifPresent(registeredServer -> event.setResult(KickedFromServerEvent.RedirectPlayer.create(registeredServer)));
    }

    private List<RegisteredServer> getAllServers() {
        List<String> lines = runCommand("kubectl get pods -l app=mk-mp-worker-prod -o jsonpath=\"{.items[?(@.status.phase=='Running')].metadata.name}\"");
        List<RegisteredServer> servers = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size();index++) {
            String line = lines.get(index);
            String name = "server" + index;
            ServerInfo info = new ServerInfo(name, InetSocketAddress.createUnresolved(line, 25575));
            servers.add(proxyServer.registerServer(info));
        }
        return servers;
    }

    private List<String> runCommand(String command) {
        try {
            List<String> lines = new ArrayList<>();
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command.split("\\s+"));
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            process.waitFor();
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            int exitCode = process.waitFor();
            return lines;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Subscribe
    public void onJoin(ServerPreConnectEvent event) {
        if (!connectedPlayers.contains(event.getPlayer().getUniqueId())) {
            connectedPlayers.add(event.getPlayer().getUniqueId());
            Optional<RegisteredServer> opt = getAllServers().stream().min(Comparator.comparingInt(s -> s.getPlayersConnected().size()));
            if (!opt.isPresent()) {
                logger.warn("No valid lobby servers were detected, so joining player '" + event.getPlayer().getUsername() + "' was connected to the default server.");
                return;
            }
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(opt.get()));
        }
    }
}