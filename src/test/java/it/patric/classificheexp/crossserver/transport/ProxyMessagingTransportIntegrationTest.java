package it.patric.classificheexp.crossserver.transport;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.crossserver.protocol.BridgeCodec;
import it.patric.classificheexp.crossserver.protocol.BridgeEnvelope;
import it.patric.classificheexp.crossserver.protocol.BridgeOp;
import it.patric.classificheexp.crossserver.protocol.BridgePayloads;
import it.patric.classificheexp.crossserver.security.BridgeAuthenticator;
import it.patric.classificheexp.crossserver.security.NonceCache;
import it.patric.classificheexp.crossserver.service.RemoteBridgeService;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxyMessagingTransportIntegrationTest {

    private static final String KEY = "12345678901234567890123456789012";

    private it.patric.classificheexp.Main plugin;
    private Server server;
    private Messenger messenger;
    private Player player;

    @BeforeEach
    void setUp() {
        plugin = mock(it.patric.classificheexp.Main.class);
        server = mock(Server.class);
        messenger = mock(Messenger.class);
        player = mock(Player.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getMessenger()).thenReturn(messenger);
        Collection<? extends Player> players = Set.of(player);
        doReturn(players).when(server).getOnlinePlayers();
    }

    @Test
    void shouldProcessIncomingRequestAndSendSignedResponse() {
        LeaderboardService leaderboardService = mock(LeaderboardService.class);
        when(leaderboardService.getScore("alpha")).thenReturn(13);

        BridgeCodec codec = new BridgeCodec();
        NonceCache nonceCache = new NonceCache(120_000);
        BridgeAuthenticator authenticator = new BridgeAuthenticator(KEY, 30_000, true, nonceCache);
        PendingRequestRegistry pendingRegistry = new PendingRequestRegistry(Runnable::run);
        RemoteBridgeService bridgeService = new RemoteBridgeService("survival-1", leaderboardService, codec, Logger.getLogger("test"));

        ProxyMessagingTransport transport = new ProxyMessagingTransport(
                plugin,
                crossConfig(),
                codec,
                authenticator,
                pendingRegistry,
                bridgeService,
                Logger.getLogger("test")
        );
        transport.start();

        BridgeEnvelope request = authenticator.sign(new BridgeEnvelope(
                1,
                "req-1",
                "req-1",
                "hub-1",
                "survival-1",
                BridgeOp.GET_SCORE,
                System.currentTimeMillis(),
                "nonce-1",
                codec.encodePayload(new BridgePayloads.ScoreRequest("alpha")),
                ""
        ));

        byte[] inbound = transport.wrapForwardPayload("survival-1", codec.encodeEnvelope(request));
        transport.handleProxyMessage(inbound);

        verify(player).sendPluginMessage(eq(plugin), eq("BungeeCord"), any());

        transport.close();
        pendingRegistry.close();
        nonceCache.close();
    }

    @Test
    void shouldRejectInvalidAuthRequest() {
        LeaderboardService leaderboardService = mock(LeaderboardService.class);

        BridgeCodec codec = new BridgeCodec();
        NonceCache nonceCache = new NonceCache(120_000);
        BridgeAuthenticator authenticator = new BridgeAuthenticator(KEY, 30_000, true, nonceCache);
        PendingRequestRegistry pendingRegistry = new PendingRequestRegistry(Runnable::run);
        RemoteBridgeService bridgeService = new RemoteBridgeService("survival-1", leaderboardService, codec, Logger.getLogger("test"));

        ProxyMessagingTransport transport = new ProxyMessagingTransport(
                plugin,
                crossConfig(),
                codec,
                authenticator,
                pendingRegistry,
                bridgeService,
                Logger.getLogger("test")
        );
        transport.start();

        BridgeEnvelope request = new BridgeEnvelope(
                1,
                "req-1",
                "req-1",
                "hub-1",
                "survival-1",
                BridgeOp.GET_SCORE,
                System.currentTimeMillis(),
                "nonce-invalid",
                codec.encodePayload(new BridgePayloads.ScoreRequest("alpha")),
                "deadbeef"
        );

        byte[] inbound = transport.wrapForwardPayload("survival-1", codec.encodeEnvelope(request));
        transport.handleProxyMessage(inbound);

        verify(player, never()).sendPluginMessage(eq(plugin), eq("BungeeCord"), any());

        transport.close();
        pendingRegistry.close();
        nonceCache.close();
    }

    @Test
    void shouldCorrelateResponseToPendingRequest() throws Exception {
        LeaderboardService leaderboardService = mock(LeaderboardService.class);

        BridgeCodec codec = new BridgeCodec();
        NonceCache nonceCache = new NonceCache(120_000);
        BridgeAuthenticator authenticator = new BridgeAuthenticator(KEY, 30_000, true, nonceCache);
        PendingRequestRegistry pendingRegistry = new PendingRequestRegistry(Runnable::run);
        RemoteBridgeService bridgeService = new RemoteBridgeService("survival-1", leaderboardService, codec, Logger.getLogger("test"));

        ProxyMessagingTransport transport = new ProxyMessagingTransport(
                plugin,
                crossConfig(),
                codec,
                authenticator,
                pendingRegistry,
                bridgeService,
                Logger.getLogger("test")
        );
        transport.start();

        CompletableFuture<BridgeEnvelope> pending = transport
                .sendRequest("hub-1", BridgeOp.GET_SCORE, new BridgePayloads.ScoreRequest("alpha"))
                .toCompletableFuture();

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq("BungeeCord"), bytesCaptor.capture());

        String outboundJson = extractEnvelopeJson(bytesCaptor.getValue());
        BridgeEnvelope outbound = codec.decodeEnvelope(outboundJson);

        BridgeEnvelope response = authenticator.sign(new BridgeEnvelope(
                1,
                "resp-1",
                outbound.requestId(),
                "hub-1",
                "survival-1",
                BridgeOp.RESULT,
                System.currentTimeMillis(),
                "nonce-r",
                "{\"score\":77}",
                ""
        ));

        byte[] inbound = transport.wrapForwardPayload("survival-1", codec.encodeEnvelope(response));
        transport.handleProxyMessage(inbound);

        BridgeEnvelope completed = pending.get(5, TimeUnit.SECONDS);
        assertEquals(BridgeOp.RESULT, completed.operation());
        transport.close();
        pendingRegistry.close();
        nonceCache.close();
    }

    private static PluginConfig.CrossServerConfig crossConfig() {
        return new PluginConfig.CrossServerConfig(
                true,
                "proxy-messaging",
                "survival-1",
                "classificheexp:bridge",
                3_000,
                new PluginConfig.AuthConfig(KEY, 30_000, 120_000, true)
        );
    }

    private static String extractEnvelopeJson(byte[] wrappedForwardBytes) throws Exception {
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(wrappedForwardBytes));
        in.readUTF();
        in.readUTF();
        in.readUTF();
        int len = in.readUnsignedShort();
        byte[] payload = in.readNBytes(len);
        return new String(payload, StandardCharsets.UTF_8);
    }
}
