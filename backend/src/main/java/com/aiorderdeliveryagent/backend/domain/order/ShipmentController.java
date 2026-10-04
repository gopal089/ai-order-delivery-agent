package com.aiorderdeliveryagent.backend.domain.order;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shipments/{shipmentId}")
class ShipmentController {
	private final ShipmentTrackingService shipments;
	ShipmentController(ShipmentTrackingService shipments) { this.shipments = shipments; }
	private void check(long id, HttpServletRequest request) { DomainRequestPolicy.check(request); DomainRequestPolicy.id(id); }
	@GetMapping OrderDomainData.Shipment get(@PathVariable long shipmentId, HttpServletRequest request) {
		check(shipmentId, request); return shipments.getShipment(shipmentId);
	}
	@GetMapping("/tracking-history") OrderDomainData.Tracking history(@PathVariable long shipmentId, HttpServletRequest request) {
		check(shipmentId, request); return shipments.getTrackingHistory(shipmentId);
	}
	@GetMapping("/current-status") OrderDomainData.CurrentStatus status(@PathVariable long shipmentId, HttpServletRequest request) {
		check(shipmentId, request); return shipments.getCurrentShipmentStatus(shipmentId);
	}
	@GetMapping("/current-location") OrderDomainData.CurrentLocation location(@PathVariable long shipmentId, HttpServletRequest request) {
		check(shipmentId, request); return shipments.getCurrentPackageLocation(shipmentId);
	}
}
