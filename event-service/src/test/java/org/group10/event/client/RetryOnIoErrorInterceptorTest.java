package org.group10.event.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryOnIoErrorInterceptorTest {

    private static final byte[] NO_BODY = new byte[0];

    private final RetryOnIoErrorInterceptor interceptor = new RetryOnIoErrorInterceptor(2, Duration.ZERO);

    @Mock
    private ClientHttpRequestExecution execution;

    @Mock
    private ClientHttpResponse response;

    @Test
    void get_thatTimesOutOnce_isRetriedAndReturnsTheSecondAnswer() throws IOException {
        MockClientHttpRequest request = request(HttpMethod.GET);
        when(execution.execute(any(), any()))
                .thenThrow(new HttpTimeoutException("request timed out"))
                .thenReturn(response);

        assertThat(interceptor.intercept(request, NO_BODY, execution)).isSameAs(response);
        verify(execution, times(2)).execute(request, NO_BODY);
    }

    @Test
    void get_thatFailsEveryTime_givesUpAfterMaxAttemptsWithTheLastError() throws IOException {
        MockClientHttpRequest request = request(HttpMethod.GET);
        when(execution.execute(any(), any())).thenThrow(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> interceptor.intercept(request, NO_BODY, execution))
                .isInstanceOf(ConnectException.class);
        verify(execution, times(2)).execute(request, NO_BODY);
    }

    @Test
    void post_thatTimesOut_isNotRetried() throws IOException {
        MockClientHttpRequest request = request(HttpMethod.POST);
        when(execution.execute(any(), any())).thenThrow(new HttpTimeoutException("request timed out"));

        assertThatThrownBy(() -> interceptor.intercept(request, NO_BODY, execution))
                .isInstanceOf(HttpTimeoutException.class);
        verify(execution, times(1)).execute(request, NO_BODY);
    }

    @Test
    void get_thatAnswersWithAnError_isNotRetried() throws IOException {
        MockClientHttpRequest request = request(HttpMethod.GET);
        when(execution.execute(any(), any())).thenReturn(response);

        assertThat(interceptor.intercept(request, NO_BODY, execution)).isSameAs(response);
        verify(execution, times(1)).execute(request, NO_BODY);
    }

    @Test
    void constructor_withLessThanOneAttempt_isRejected() {
        assertThatThrownBy(() -> new RetryOnIoErrorInterceptor(0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockClientHttpRequest request(HttpMethod method) {
        return new MockClientHttpRequest(method, URI.create("http://localhost:8081/api/users/2/permissions"));
    }
}
