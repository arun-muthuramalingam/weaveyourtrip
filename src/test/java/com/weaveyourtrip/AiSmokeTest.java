package com.weaveyourtrip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test that proves the full Spring AI → Gemini wiring works.
 *
 * Skipped automatically when GEMINI_API_KEY is unset (e.g. on a fresh clone or in CI
 * without secrets), so it never produces a false failure.
 *
 * To run locally:
 *   export GEMINI_API_KEY=AIza...
 *   ./mvnw test -Dtest=AiSmokeTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class AiSmokeTest {

    @Autowired
    ChatClient.Builder chatClientBuilder;

    @Test
    void canCallGemini() {
        String response = chatClientBuilder.build()
                .prompt("Say 'hello from gemini' and nothing else.")
                .call()
                .content();

        assertThat(response)
                .as("Gemini should respond with a greeting")
                .containsIgnoringCase("hello");
    }
}
