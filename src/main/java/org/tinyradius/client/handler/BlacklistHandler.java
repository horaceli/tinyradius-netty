package org.tinyradius.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tinyradius.client.PendingRequestCtx;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChannelOutboundHandler that adds support for blacklists after multiple failures.
 * <p>
 * Can be placed at any point where PendingRequestCtx is available, as it hooks onto the promise
 * outcomes to catch all failure scenarios (e.g. timeouts). However, the earlier it's hooked, the
 * sooner it can fail fast the request if the endpoint is blacklisted.
 */
public class BlacklistHandler extends ChannelOutboundHandlerAdapter {

    private static final Logger logger = LogManager.getLogger();

    private final long blacklistTtlMs;
    private final int failCountThreshold;

    private final Map<SocketAddress, AtomicInteger> failCounts = new ConcurrentHashMap<>();
    private final Map<SocketAddress, Long> blacklist = new ConcurrentHashMap<>();

    public BlacklistHandler(long blacklistTtlMs, int failCountThreshold) {
        this.blacklistTtlMs = blacklistTtlMs;
        this.failCountThreshold = failCountThreshold;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof PendingRequestCtx) {
            final PendingRequestCtx request = (PendingRequestCtx) msg;
            final InetSocketAddress address = request.getEndpoint().getAddress();

            if (isBlacklisted(address)) {
                request.getResponse().tryFailure(new IOException("Client send failed, endpoint blacklisted: " + address));
                return;
            }

            request.getResponse().addListener(f -> logResult(address, f.isSuccess()));
        }

        ctx.write(msg, promise);
    }

    private boolean isBlacklisted(SocketAddress socketAddress) {
        final Long blacklistExpiry = blacklist.get(socketAddress);

        // not blacklisted
        if (blacklistExpiry == null)
            return false;

        // blacklist active
        if (System.currentTimeMillis() < blacklistExpiry) {
            logger.debug("Endpoint blacklisted while proxying packet to {}", socketAddress);
            return true;
        }

        // blacklist expired
        reset(socketAddress);
        logger.info("Endpoint {} removed from blacklist (expired)", socketAddress);
        return false;
    }

    private void logResult(SocketAddress socketAddress, boolean success) {
        if (success) {
            reset(socketAddress);
        } else {
            final int i = failCounts.computeIfAbsent(socketAddress, d -> new AtomicInteger()).incrementAndGet();

            if (i >= failCountThreshold && blacklist.get(socketAddress) == null) {

                // only set if isn't already blacklisted, to avoid delayed responses extending ttl
                blacklist.put(socketAddress, System.currentTimeMillis() + blacklistTtlMs);
                logger.debug("Endpoint {} added to blacklist", socketAddress);
            }
        }
    }

    private void reset(SocketAddress socketAddress) {
        blacklist.remove(socketAddress);
        failCounts.remove(socketAddress);
    }
}