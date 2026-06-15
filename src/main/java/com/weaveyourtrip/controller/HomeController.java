package com.weaveyourtrip.controller;

import com.weaveyourtrip.model.Passport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page — the marketing surface. Lists supported passports + visa-first
 * value props. Drives users into the wizard via {@code /plan}.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("passports", Passport.values());
        return "index";
    }
}
