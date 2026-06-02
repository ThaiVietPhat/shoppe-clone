package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestAccessDeniedHandlerTest {

    @Mock
    private SecurityErrorWriter securityErrorWriter;

    @InjectMocks
    private RestAccessDeniedHandler accessDeniedHandler;

    @Test
    void handleShouldCallSecurityErrorWriterWithForbidden() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AccessDeniedException accessDeniedException = mock(AccessDeniedException.class);

        accessDeniedHandler.handle(request, response, accessDeniedException);

        verify(securityErrorWriter).writeError(response, ErrorCode.FORBIDDEN);
    }
}
