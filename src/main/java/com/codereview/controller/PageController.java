package com.codereview.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the frontend HTML pages.
 * Separated from the REST API controller to maintain clean MVC separation.
 */
@Controller
public class PageController {

    /**
     * GET / — Serves the index.html dashboard page.
     *
     * @return the "index" view name (resolves to templates/index.html)
     */
    @GetMapping("/")
    public String showHomePage() {
        return "index";
    }
}
