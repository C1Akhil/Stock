package com.market.analyser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirects root "/" to the static dashboard page.
 * When deployed on Render, visiting the base URL shows the HTML dashboard.
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String dashboard() {
        return "forward:/index.html";
    }
}
