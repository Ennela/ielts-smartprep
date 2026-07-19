package com.smartprep.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RequestLoggingInterceptorTest {

    @Test
    void preHandleDoesNotLogQueryValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingInterceptor.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        String oneTimeToken = "one-time-test-token";
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/auth/verify-email");
        request.setQueryString("token=" + oneTimeToken);

        try {
            new RequestLoggingInterceptor().preHandle(
                    request, new MockHttpServletResponse(), new Object());

            assertFalse(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains(oneTimeToken)));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
