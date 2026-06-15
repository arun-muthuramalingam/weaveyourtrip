package com.weaveyourtrip.config;

import com.weaveyourtrip.model.WizardInput;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.WebApplicationContext;

/**
 * Wires the {@link WizardInput} as a session-scoped bean. State accumulates
 * across the 6 wizard steps within one HTTP session.
 *
 * <p>{@link ScopedProxyMode#TARGET_CLASS} lets controllers inject the bean directly
 * (Spring proxies it) without needing {@code ObjectProvider} or {@code @Lookup}.
 */
@Configuration
public class WizardSessionConfig {

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public WizardInput wizardInput() {
        return new WizardInput();
    }
}
