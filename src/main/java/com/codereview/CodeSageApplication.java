package com.codereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the CodeSage AI Code Reviewer application.
 *
 * @SpringBootApplication enables:
 *  - Auto-configuration
 *  - Component scanning (picks up @Controller, @Service in com.codereview.*)
 *  - Configuration properties
 */
@SpringBootApplication
public class CodeSageApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeSageApplication.class, args);
    }

}
