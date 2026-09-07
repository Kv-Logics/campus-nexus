package com.kvlogics.campusnexus.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

@RestController
@RequestMapping("/api/system")
public class NetworkInfoController {

    @Value("${server.port:8080}")
    private int serverPort;

    @GetMapping("/network-info")
    public ResponseEntity<Map<String, Object>> getNetworkInfo() {
        List<String> lanIps = new ArrayList<>();
        String primaryIp = "localhost";

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        lanIps.add(ip);
                        if (primaryIp.equals("localhost")) {
                            primaryIp = ip;
                        } else if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
                            primaryIp = ip;
                        }
                    }
                }
            }

            if (primaryIp.equals("localhost")) {
                primaryIp = InetAddress.getLocalHost().getHostAddress();
            }
        } catch (Exception ignored) {
        }

        String relayUrl = "http://" + primaryIp + ":" + serverPort + "/relay.html";
        String shareMessage = "Hey! Open this link on your phone while connected to campus Wi-Fi to act as a Campus Relay: " + relayUrl;

        return ResponseEntity.ok(Map.of(
                "primaryLanIp", primaryIp,
                "port", serverPort,
                "availableIps", lanIps,
                "directRelayUrl", relayUrl,
                "shareMessage", shareMessage
        ));
    }
}
