package com.shopee.monolith.modules.auth.security;

import com.shopee.monolith.modules.auth.config.AuthSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final AuthSecurityProperties properties;

    public String resolveIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        List<String> trustedProxies = properties.getTrustedProxies();

        if (trustedProxies != null && !trustedProxies.isEmpty()) {
            boolean isTrusted = false;
            for (String proxyCidr : trustedProxies) {
                try {
                    IpAddressMatcher matcher = new IpAddressMatcher(proxyCidr);
                    if (matcher.matches(remoteAddr)) {
                        isTrusted = true;
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    // Ignored: already validated at startup
                }
            }

            if (isTrusted) {
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    String[] parts = xff.split(",");
                    if (parts.length > 0) {
                        String firstIp = parts[0].trim();
                        String normalized = normalizeIp(firstIp);
                        if (normalized != null) {
                            return normalized;
                        }
                    }
                }
            }
        }

        String normalizedRemote = normalizeIp(remoteAddr);
        return normalizedRemote != null ? normalizedRemote : "unknown";
    }

    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String cleanIp = ip.trim();
        if (cleanIp.startsWith("[") && cleanIp.endsWith("]")) {
            cleanIp = cleanIp.substring(1, cleanIp.length() - 1).trim();
        }
        
        if (isValidIpLiteral(cleanIp)) {
            try {
                InetAddress address = InetAddress.getByName(cleanIp);
                return address.getHostAddress();
            } catch (Exception e) {
                // If parsing fails, fall back to null
            }
        }
        return null;
    }

    private boolean isValidIpLiteral(String ip) {
        // IPv4 pattern
        if (ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            return true;
        }
        // IPv6 pattern: contains colons and valid hex/colon/dots
        if (ip.contains(":") && ip.matches("^[0-9a-fA-F:\\.]+(%\\w+)?$")) {
            return true;
        }
        return false;
    }
}
