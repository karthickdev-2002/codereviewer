package com.codereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the CodeSage AI Code Reviewer application.
 *
 * scanBasePackages is expanded to include com.codesage so the new runtime
 * custom rule service and controller are discovered by Spring Boot.
 */
@SpringBootApplication(scanBasePackages = {"com.codereview", "com.codesage"})
public class CodeSageApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeSageApplication.class, args);
    }

}
