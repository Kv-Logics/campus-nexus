package com.kvlogics.campusnexus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/relay")
    public String relayPage() {
        return "forward:/relay.html";
    }
}
