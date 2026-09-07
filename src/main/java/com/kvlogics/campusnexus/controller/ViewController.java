package com.kvlogics.campusnexus.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/")
    public String index(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        boolean isLocal = "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "localhost".equalsIgnoreCase(request.getServerName());

        // Isolate external peers to relay.html so they cannot view or access File Distribution or FTP Target configs
        if (!isLocal && !"true".equalsIgnoreCase(request.getParameter("admin"))) {
            return "redirect:/relay.html";
        }
        return "forward:/index.html";
    }

    @GetMapping("/relay")
    public String relayPage() {
        return "forward:/relay.html";
    }
}
