package com.hotel.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN')")
public class AdminPartnerController {
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/properties")
    public List<Map<String, Object>> properties(@RequestParam(required = false) String source) {
        String sql = """
                SELECT h.id,h.code,h.name_vi,h.property_type,h.address,h.approval_status,h.operation_status,
                       h.is_demo,h.data_source,h.province_id,h.ward_id,
                       (SELECT COUNT(*) FROM room_types rt WHERE rt.hotel_id=h.id) room_type_count,
                       (SELECT COUNT(*) FROM rooms r WHERE r.hotel_id=h.id) room_count
                FROM hotels h WHERE (? IS NULL OR h.data_source=?) ORDER BY h.id DESC
                """;
        return jdbcTemplate.queryForList(sql, source, source);
    }

    @GetMapping("/property-owners")
    public List<Map<String, Object>> owners() {
        return jdbcTemplate.queryForList("""
                SELECT u.id user_id,u.full_name,u.email,u.status account_status,
                       COUNT(DISTINCT up.hotel_id) property_count,COUNT(DISTINCT r.id) room_count,
                       COALESCE(s.plan_code,'NO_PLAN') plan_code,COALESCE(s.subscription_status,'NONE') subscription_status,
                       s.start_at,s.end_at,s.is_lifetime,COALESCE(pay.payment_status,'UNPAID') payment_status,
                       pay.total_paid
                FROM users u
                JOIN user_properties up ON up.user_id=u.id AND up.relationship_type='OWNER'
                LEFT JOIN rooms r ON r.hotel_id=up.hotel_id
                OUTER APPLY (SELECT TOP 1 sp.code plan_code,a.status subscription_status,a.start_at,a.end_at,a.is_lifetime
                             FROM account_subscriptions a JOIN subscription_plans sp ON sp.id=a.plan_id
                             WHERE a.user_id=u.id ORDER BY a.start_at DESC,a.id DESC) s
                OUTER APPLY (SELECT TOP 1 p.payment_status,o.total_amount total_paid
                             FROM subscription_orders o LEFT JOIN subscription_payments p ON p.order_id=o.id
                             WHERE o.user_id=u.id ORDER BY o.id DESC) pay
                GROUP BY u.id,u.full_name,u.email,u.status,s.plan_code,s.subscription_status,s.start_at,s.end_at,s.is_lifetime,pay.payment_status,pay.total_paid
                ORDER BY u.id DESC
                """);
    }

    @GetMapping("/property-registrations")
    public List<Map<String, Object>> registrations() {
        return jdbcTemplate.queryForList("""
                SELECT u.id user_id,u.full_name,u.email,h.id property_id,h.name_vi,h.approval_status,h.operation_status,up.created_at registered_at
                FROM user_properties up JOIN users u ON u.id=up.user_id JOIN hotels h ON h.id=up.hotel_id
                WHERE up.relationship_type='OWNER' ORDER BY up.created_at DESC
                """);
    }

    @GetMapping("/property-owners/unsubscribed")
    public List<Map<String, Object>> unsubscribedOwners() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT u.id user_id,u.full_name,u.email,u.status account_status
                FROM users u JOIN user_properties up ON up.user_id=u.id AND up.relationship_type='OWNER'
                WHERE NOT EXISTS (SELECT 1 FROM account_subscriptions a WHERE a.user_id=u.id AND a.status='ACTIVE')
                ORDER BY u.id DESC
                """);
    }

    @GetMapping("/property-approvals")
    public List<Map<String, Object>> approvals() {
        return jdbcTemplate.queryForList("""
                SELECT h.id,h.code,h.name_vi,h.address,h.property_type,h.approval_status,h.operation_status,
                       u.id owner_id,u.full_name owner_name,u.email owner_email
                FROM hotels h LEFT JOIN user_properties up ON up.hotel_id=h.id AND up.relationship_type='OWNER'
                LEFT JOIN users u ON u.id=up.user_id
                WHERE h.approval_status IN ('DRAFT','PENDING_APPROVAL','IMPORTED_PENDING_REVIEW')
                ORDER BY h.created_at DESC
                """);
    }

    @GetMapping("/property-staff")
    public List<Map<String, Object>> staff() {
        return jdbcTemplate.queryForList("""
                SELECT up.id,u.id user_id,u.full_name,u.email,u.status account_status,h.id property_id,h.name_vi property_name,
                       up.relationship_type,up.status assignment_status,up.start_date,up.end_date
                FROM user_properties up JOIN users u ON u.id=up.user_id JOIN hotels h ON h.id=up.hotel_id
                WHERE up.relationship_type IN ('ADMIN','RECEPTIONIST','STAFF') ORDER BY h.id,u.full_name
                """);
    }

    @GetMapping("/subscription-orders")
    public List<Map<String, Object>> subscriptionOrders() {
        return jdbcTemplate.queryForList("""
                SELECT o.id,o.order_code,u.email,sp.code plan_code,o.billing_type,o.total_amount,o.currency,o.status,o.created_at
                FROM subscription_orders o JOIN users u ON u.id=o.user_id JOIN subscription_plans sp ON sp.id=o.plan_id
                ORDER BY o.id DESC
                """);
    }

    @GetMapping("/subscription-payments")
    public List<Map<String, Object>> subscriptionPayments() {
        return jdbcTemplate.queryForList("""
                SELECT p.id,o.order_code,u.email,p.payment_method,p.amount,p.payment_status,p.transaction_code,p.paid_at
                FROM subscription_payments p JOIN subscription_orders o ON o.id=p.order_id JOIN users u ON u.id=o.user_id
                ORDER BY p.id DESC
                """);
    }

    @GetMapping("/software-contracts")
    public List<Map<String, Object>> softwareContracts() {
        return jdbcTemplate.queryForList("""
                SELECT c.id,c.contract_no,u.email,h.name_vi property_name,sp.code plan_code,c.contract_type,
                       c.start_date,c.end_date,c.is_lifetime,c.contract_value,c.status
                FROM software_contracts c JOIN users u ON u.id=c.user_id
                LEFT JOIN hotels h ON h.id=c.property_id JOIN subscription_plans sp ON sp.id=c.plan_id
                ORDER BY c.id DESC
                """);
    }

    @GetMapping("/property-room-types")
    public List<Map<String, Object>> roomTypes(@RequestParam(required = false) Long propertyId) {
        return jdbcTemplate.queryForList("""
                SELECT rt.id,rt.hotel_id,h.name_vi property_name,rt.code,rt.name_vi,rt.base_price,
                       rt.max_adults,rt.max_children,rt.max_guests,rt.status,COUNT(r.id) room_count
                FROM room_types rt JOIN hotels h ON h.id=rt.hotel_id LEFT JOIN rooms r ON r.room_type_id=rt.id
                WHERE (? IS NULL OR rt.hotel_id=?)
                GROUP BY rt.id,rt.hotel_id,h.name_vi,rt.code,rt.name_vi,rt.base_price,rt.max_adults,rt.max_children,rt.max_guests,rt.status
                ORDER BY rt.hotel_id,rt.id
                """, propertyId, propertyId);
    }

    @GetMapping("/property-rooms")
    public List<Map<String, Object>> rooms(@RequestParam(required = false) Long propertyId) {
        return jdbcTemplate.queryForList("""
                SELECT r.id,r.hotel_id,h.name_vi property_name,r.room_type_id,rt.name_vi room_type_name,
                       r.room_number,r.floor,r.status,r.housekeeping_status,r.maintenance_status,r.is_demo
                FROM rooms r JOIN hotels h ON h.id=r.hotel_id JOIN room_types rt ON rt.id=r.room_type_id
                WHERE (? IS NULL OR r.hotel_id=?) ORDER BY r.hotel_id,r.room_number
                """, propertyId, propertyId);
    }
}
