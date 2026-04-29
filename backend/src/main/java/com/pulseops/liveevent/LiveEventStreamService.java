package com.pulseops.liveevent;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.pulseops.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LiveEventStreamService {

	static final long CONNECTION_TIMEOUT_MILLISECONDS = 30 * 60 * 1000L;
	static final int MAX_CONNECTIONS_PER_ORGANIZATION = 100;

	private final ConcurrentMap<UUID, ConcurrentMap<UUID, SseEmitter>> connections =
			new ConcurrentHashMap<>();
	private final Clock clock;

	public LiveEventStreamService(Clock clock) {
		this.clock = clock;
	}

	public SseEmitter subscribe(UUID organizationId, Instant accessTokenExpiresAt) {
		var organizationConnections = connections.computeIfAbsent(
				organizationId, ignored -> new ConcurrentHashMap<>());
		var connectionId = UUID.randomUUID();
		var remainingTokenLifetime = accessTokenExpiresAt == null
				? CONNECTION_TIMEOUT_MILLISECONDS
				: Duration.between(Instant.now(clock), accessTokenExpiresAt).toMillis();
		var timeout = Math.max(
				1000L,
				Math.min(CONNECTION_TIMEOUT_MILLISECONDS, remainingTokenLifetime));
		var emitter = new SseEmitter(timeout);
		synchronized (organizationConnections) {
			if (organizationConnections.size() >= MAX_CONNECTIONS_PER_ORGANIZATION) {
				throw new ApiException(
						HttpStatus.TOO_MANY_REQUESTS,
						"LIVE_CONNECTION_LIMIT_REACHED",
						"The organization has too many active live connections.");
			}
			organizationConnections.put(connectionId, emitter);
		}
		Runnable cleanup = () -> remove(organizationId, connectionId);
		emitter.onCompletion(cleanup);
		emitter.onTimeout(cleanup);
		emitter.onError(ignored -> cleanup.run());

		var ready = new OrganizationLiveEvent(
				UUID.randomUUID(),
				organizationId,
				LiveEventType.CONNECTION_READY,
				"organization",
				organizationId,
				Instant.now(clock));
		try {
			send(emitter, "pulseops-ready", ready, 3000L);
		}
		catch (IOException | IllegalStateException exception) {
			cleanup.run();
			emitter.completeWithError(exception);
			throw new ApiException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"LIVE_STREAM_UNAVAILABLE",
					"The live event stream could not be established.");
		}
		return emitter;
	}

	@TransactionalEventListener(
			phase = TransactionPhase.AFTER_COMMIT,
			fallbackExecution = true)
	public void broadcast(OrganizationLiveEvent event) {
		var organizationConnections = connections.get(event.organizationId());
		if (organizationConnections == null) {
			return;
		}
		organizationConnections.forEach((connectionId, emitter) -> {
			try {
				send(emitter, "pulseops-update", event, null);
			}
			catch (IOException | IllegalStateException exception) {
				remove(event.organizationId(), connectionId);
				emitter.completeWithError(exception);
			}
		});
	}

	@Scheduled(
			fixedDelayString =
					"${pulseops.live-events.heartbeat-milliseconds:15000}")
	public void heartbeat() {
		connections.forEach((organizationId, organizationConnections) ->
				organizationConnections.forEach((connectionId, emitter) -> {
					try {
						emitter.send(SseEmitter.event().comment("heartbeat"));
					}
					catch (IOException | IllegalStateException exception) {
						remove(organizationId, connectionId);
						emitter.completeWithError(exception);
					}
				}));
	}

	int connectionCount(UUID organizationId) {
		var organizationConnections = connections.get(organizationId);
		return organizationConnections == null ? 0 : organizationConnections.size();
	}

	void disconnectAll() {
		connections.values().forEach(organizationConnections ->
				organizationConnections.values().forEach(SseEmitter::complete));
		connections.clear();
	}

	private void send(
			SseEmitter emitter,
			String eventName,
			OrganizationLiveEvent event,
			Long reconnectTime) throws IOException {
		var builder = SseEmitter.event()
				.id(event.id().toString())
				.name(eventName)
				.data(event);
		if (reconnectTime != null) {
			builder.reconnectTime(reconnectTime);
		}
		emitter.send(builder);
	}

	private void remove(UUID organizationId, UUID connectionId) {
		var organizationConnections = connections.get(organizationId);
		if (organizationConnections == null) {
			return;
		}
		organizationConnections.remove(connectionId);
		if (organizationConnections.isEmpty()) {
			connections.remove(organizationId, organizationConnections);
		}
	}
}
