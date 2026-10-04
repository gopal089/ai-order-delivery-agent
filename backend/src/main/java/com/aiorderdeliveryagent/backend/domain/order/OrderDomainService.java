package com.aiorderdeliveryagent.backend.domain.order;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import com.aiorderdeliveryagent.backend.auth.*;
import com.aiorderdeliveryagent.backend.integration.order.model.ExternalOrderId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import static com.aiorderdeliveryagent.backend.domain.order.OrderDomainData.*;

/** Read-only persisted application state. No order ingestion, raw cache, or current-state inference. */
@Service
public class OrderDomainService {
	private final AuthenticatedUserContextProvider contexts;
	private final TenantDataAuthorizationService authorization;
	private final JdbcTemplate jdbc;
	public OrderDomainService(AuthenticatedUserContextProvider contexts, TenantDataAuthorizationService authorization, JdbcTemplate jdbc) {
		this.contexts = contexts; this.authorization = authorization; this.jdbc = jdbc;
	}
	public OrderPage listCustomerOrders() { return listCustomerOrders(0, 100); }
	public OrderPage listCustomerOrders(long afterId, int limit) {
		var user = contexts.getCurrentUser();
		if (afterId < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid order pagination");
		var rows = jdbc.query("""
			SELECT id,integration_id,order_status,fetched_at,source_updated_at FROM public.orders
			WHERE tenant_id=? AND user_id=? AND id>? ORDER BY id LIMIT ?
			""", (row, index) -> order(row), user.tenantId(), user.userId(), afterId, limit + 1);
		boolean more = rows.size() > limit;
		var page = rows.subList(0, Math.min(rows.size(), limit));
		return new OrderPage(page, more ? Optional.of(page.getLast().id()) : Optional.empty());
	}
	public Order getOrder(long orderId) {
		var user = contexts.getCurrentUser(); authorization.requireOrderAccess(orderId);
		return jdbc.query("""
			SELECT id,integration_id,order_status,fetched_at,source_updated_at FROM public.orders
			WHERE id=? AND tenant_id=? AND user_id=?
			""", (row, index) -> order(row), orderId, user.tenantId(), user.userId())
			.stream().findFirst().orElseThrow(OrderDomainService::denied);
	}
	/** Backend-only resolution; never expose this method/record as model arguments or client authority. */
	public ProviderOrderReference resolveForProvider(long orderId) {
		var user = contexts.getCurrentUser(); authorization.requireOrderAccess(orderId);
		var reference = jdbc.query("""
			SELECT integration_id,external_order_id FROM public.orders WHERE id=? AND tenant_id=? AND user_id=?
			""", (row, index) -> new ProviderOrderReference(row.getLong(1), new ExternalOrderId(row.getString(2))),
			orderId, user.tenantId(), user.userId()).stream().findFirst().orElseThrow(OrderDomainService::denied);
		authorization.requireIntegrationAccess(reference.integrationId());
		return reference;
	}
	public record ProviderOrderReference(long integrationId, ExternalOrderId externalOrderId) {
		@Override public String toString() { return "ProviderOrderReference[backend-only]"; }
	}
	private Order order(ResultSet row) throws SQLException {
		return new Order(row.getLong("id"), row.getLong("integration_id"), Optional.ofNullable(row.getString("order_status")),
			row.getTimestamp("fetched_at").toInstant(), persisted(optionalInstant(row, "source_updated_at")));
	}
	static Optional<Instant> optionalInstant(ResultSet row, String column) throws SQLException {
		var timestamp = row.getTimestamp(column); return timestamp == null ? Optional.empty() : Optional.of(timestamp.toInstant());
	}
	static Provenance persisted(Optional<Instant> source) { return new Provenance(Authority.PERSISTED_APPLICATION, Optional.empty(), source); }
	static AccessDeniedException denied() { return new AccessDeniedException("Access is denied"); }
}
