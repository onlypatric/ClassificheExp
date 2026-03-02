package it.patric.classificheexp.crossserver.transport;

import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.crossserver.protocol.BridgeCodec;
import it.patric.classificheexp.crossserver.protocol.BridgeEnvelope;
import it.patric.classificheexp.crossserver.protocol.BridgeOp;
import it.patric.classificheexp.crossserver.security.BridgeAuthenticator;
import it.patric.classificheexp.crossserver.service.RemoteBridgeService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class ProxyMessagingTransport implements PluginMessageListener, AutoCloseable {

    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;
    private final PluginConfig.CrossServerConfig config;
    private final BridgeCodec codec;
    private final BridgeAuthenticator authenticator;
    private final PendingRequestRegistry pendingRegistry;
    private final RemoteBridgeService remoteBridgeService;
    private final Logger logger;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public ProxyMessagingTransport(
            JavaPlugin plugin,
            PluginConfig.CrossServerConfig config,
            BridgeCodec codec,
            BridgeAuthenticator authenticator,
            PendingRequestRegistry pendingRegistry,
            RemoteBridgeService remoteBridgeService,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.codec = Objects.requireNonNull(codec, "codec cannot be null");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator cannot be null");
        this.pendingRegistry = Objects.requireNonNull(pendingRegistry, "pendingRegistry cannot be null");
        this.remoteBridgeService = Objects.requireNonNull(remoteBridgeService, "remoteBridgeService cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
    }

    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }

        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
    }

    public CompletionStage<BridgeEnvelope> sendRequest(String targetServerId, BridgeOp operation, Object payload) {
        Objects.requireNonNull(operation, "operation cannot be null");
        if (!operation.isRequest()) {
            throw new IllegalArgumentException("Only request operations can be sent via sendRequest");
        }

        String payloadJson = codec.encodePayload(payload);
        BridgeEnvelope request = remoteBridgeService.createOutboundRequest(targetServerId, operation, payloadJson);
        BridgeEnvelope signed = authenticator.sign(request);

        CompletableFuture<BridgeEnvelope> responseFuture = pendingRegistry.register(
                signed.requestId(),
                config.requestTimeoutMs()
        );

        try {
            sendEnvelopeToProxy(signed, targetServerId);
        } catch (RuntimeException ex) {
            responseFuture.completeExceptionally(ex);
        }

        return responseFuture;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BUNGEE_CHANNEL.equals(channel)) {
            return;
        }

        handleProxyMessage(message);
    }

    void handleProxyMessage(byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = in.readUTF();
            if (!"Forward".equals(subChannel)) {
                return;
            }

            String sourceServer = in.readUTF();
            String forwardedChannel = in.readUTF();
            int payloadLength = in.readUnsignedShort();
            byte[] payloadBytes = in.readNBytes(payloadLength);

            if (!config.channel().equals(forwardedChannel)) {
                return;
            }

            String envelopeJson = new String(payloadBytes, StandardCharsets.UTF_8);
            BridgeEnvelope envelope = codec.decodeEnvelope(envelopeJson);

            if (!isTargetingLocalServer(envelope.targetServerId())) {
                return;
            }

            BridgeAuthenticator.VerificationResult verification = authenticator.verifyIncoming(envelope);
            if (!verification.valid()) {
                logger.warning("event=bridge_auth_failed reason=" + verification.reason()
                        + " origin=" + sourceServer + " requestId=" + envelope.requestId());
                return;
            }

            if (envelope.operation().isResponse()) {
                boolean completed = pendingRegistry.complete(envelope.correlationId(), envelope);
                if (!completed) {
                    logger.warning("event=bridge_response_unmatched correlationId=" + envelope.correlationId());
                }
                return;
            }

            if (config.serverId().equals(envelope.originServerId())) {
                return;
            }

            logger.info("event=bridge_request_received op=" + envelope.operation()
                    + " origin=" + envelope.originServerId() + " requestId=" + envelope.requestId()
                    + " auth=true");

            remoteBridgeService.handleRequest(envelope).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    logger.warning("event=bridge_request_failed op=" + envelope.operation()
                            + " requestId=" + envelope.requestId() + " cause="
                            + cause.getClass().getSimpleName() + ':' + safeMessage(cause));
                    return;
                }

                BridgeEnvelope signedResponse = authenticator.sign(response);
                try {
                    sendEnvelopeToProxy(signedResponse, envelope.originServerId());
                    logger.info("event=bridge_response_sent op=" + envelope.operation()
                            + " requestId=" + envelope.requestId() + " success=true");
                } catch (RuntimeException ex) {
                    logger.warning("event=bridge_response_sent op=" + envelope.operation()
                            + " requestId=" + envelope.requestId() + " success=false cause="
                            + ex.getClass().getSimpleName() + ':' + safeMessage(ex));
                }
            });
        } catch (RuntimeException | IOException ex) {
            logger.warning("event=bridge_message_decode_failed cause="
                    + ex.getClass().getSimpleName() + ':' + safeMessage(ex));
        }
    }

    private boolean isTargetingLocalServer(String targetServerId) {
        return "*".equals(targetServerId) || config.serverId().equals(targetServerId);
    }

    private void sendEnvelopeToProxy(BridgeEnvelope envelope, String targetServerId) {
        ensureStarted();

        byte[] wrappedMessage = wrapForwardPayload(targetServerId, codec.encodeEnvelope(envelope));
        Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("Cannot send proxy bridge message without online players");
        }

        Player carrier = players.iterator().next();
        carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, wrappedMessage);
    }

    byte[] wrapForwardPayload(String targetServerId, String envelopeJson) {
        Objects.requireNonNull(targetServerId, "targetServerId cannot be null");
        Objects.requireNonNull(envelopeJson, "envelopeJson cannot be null");

        byte[] envelopeBytes = envelopeJson.getBytes(StandardCharsets.UTF_8);

        try (ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteOutput)) {
            out.writeUTF("Forward");
            out.writeUTF(targetServerId);
            out.writeUTF(config.channel());
            out.writeShort(envelopeBytes.length);
            out.write(envelopeBytes);
            return byteOutput.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize bridge forward payload", ex);
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void ensureStarted() {
        if (!started.get()) {
            throw new IllegalStateException("ProxyMessagingTransport is not started");
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "no-message" : message;
    }
}
