package com.rutatur.poi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.security.InternalHeaders;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The internal token is the only authentication on {@code /internal/**}, so a missing, wrong or
 * unconfigured token must fail closed while public paths stay untouched.
 */
class InternalTokenFilterTest {

    private static final String TOKEN = "s3cr3t-internal-token";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void rejectsInternalRequestWithoutTheToken() throws Exception {
        MockHttpServletResponse response = call(new InternalTokenFilter(TOKEN, objectMapper),
                internalRequest());

        assertThat(response.getStatus()).isEqualTo(ErrorCode.INTERNAL_AUTH_REQUIRED.statusValue());
        assertThat(response.getContentAsString()).contains(ErrorCode.INTERNAL_AUTH_REQUIRED.name());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsInternalRequestWithAWrongToken() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalHeaders.INTERNAL_TOKEN, "wrong-token");

        MockHttpServletResponse response = call(new InternalTokenFilter(TOKEN, objectMapper), request);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.INTERNAL_AUTH_REQUIRED.statusValue());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void letsInternalRequestThroughWithTheRightToken() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalHeaders.INTERNAL_TOKEN, TOKEN);

        MockHttpServletResponse response = call(new InternalTokenFilter(TOKEN, objectMapper), request);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void publicPathsAreNotGuardedByTheInternalToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pois/search");

        MockHttpServletResponse response = call(new InternalTokenFilter(TOKEN, objectMapper), request);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void unconfiguredTokenFailsClosedInsteadOfAcceptingAnyRequest() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalHeaders.INTERNAL_TOKEN, "");

        MockHttpServletResponse response = call(new InternalTokenFilter("", objectMapper), request);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.INTERNAL_AUTH_REQUIRED.statusValue());
        verify(chain, never()).doFilter(any(), any());
    }

    private MockHttpServletResponse call(InternalTokenFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest internalRequest() {
        return new MockHttpServletRequest("GET", "/internal/pois/candidates");
    }
}
