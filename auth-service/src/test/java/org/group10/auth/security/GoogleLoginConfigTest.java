package org.group10.auth.security;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleLoginConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GoogleLoginConfig.class);

    @Test
    void context_withoutClientId_startsAndTheLoginAnswers503() {
        runner.withPropertyValues("ticketya.google.client-id=").run(context -> {
            assertThat(context).hasNotFailed();
            GoogleIdTokenVerifier verifier = context.getBean(GoogleIdTokenVerifier.class);

            assertThatThrownBy(() -> verifier.verify("any-token"))
                    .isInstanceOf(TicketYaException.class)
                    .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        });
    }

    @Test
    void context_withClientId_usesGooglePublicKeysByDefault() {
        runner.withPropertyValues("ticketya.google.client-id=ticketya.apps.googleusercontent.com").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GoogleProperties.class).jwkSetUri())
                    .isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
        });
    }
}
