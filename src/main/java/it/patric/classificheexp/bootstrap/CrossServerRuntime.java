package it.patric.classificheexp.bootstrap;

import it.patric.classificheexp.api.CrossServerLeaderboardApi;
import it.patric.classificheexp.crossserver.security.BridgeAuthenticator;
import it.patric.classificheexp.crossserver.security.NonceCache;
import it.patric.classificheexp.crossserver.service.RemoteBridgeService;
import it.patric.classificheexp.crossserver.transport.PendingRequestRegistry;
import it.patric.classificheexp.crossserver.transport.ProxyMessagingTransport;

import java.util.Objects;

public record CrossServerRuntime(
        ProxyMessagingTransport transport,
        RemoteBridgeService remoteBridgeService,
        BridgeAuthenticator bridgeAuthenticator,
        NonceCache nonceCache,
        PendingRequestRegistry pendingRequestRegistry,
        CrossServerLeaderboardApi crossServerLeaderboardApi
) {

    public CrossServerRuntime {
        Objects.requireNonNull(transport, "transport cannot be null");
        Objects.requireNonNull(remoteBridgeService, "remoteBridgeService cannot be null");
        Objects.requireNonNull(bridgeAuthenticator, "bridgeAuthenticator cannot be null");
        Objects.requireNonNull(nonceCache, "nonceCache cannot be null");
        Objects.requireNonNull(pendingRequestRegistry, "pendingRequestRegistry cannot be null");
        Objects.requireNonNull(crossServerLeaderboardApi, "crossServerLeaderboardApi cannot be null");
    }
}
