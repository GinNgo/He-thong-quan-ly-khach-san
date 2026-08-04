package com.hotel.platformbilling.subscription;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name="subscription_plan_admin_operations")
public class SubscriptionPlanAdminOperation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="key_hash",nullable=false,unique=true,length=64,updatable=false) private String keyHash;
    @Column(nullable=false,length=20,updatable=false) private String action;
    @Column(name="plan_id",nullable=false,updatable=false) private Long planId;
    @Column(name="result_status",nullable=false,length=20,updatable=false) private String resultStatus;
    @Column(name="request_hash",nullable=false,length=64,updatable=false) private String requestHash;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    protected SubscriptionPlanAdminOperation() {}
    public static SubscriptionPlanAdminOperation record(String keyHash,String action,Long planId,String resultStatus,String requestHash,LocalDateTime now){ SubscriptionPlanAdminOperation op=new SubscriptionPlanAdminOperation(); op.keyHash=keyHash; op.action=action; op.planId=planId; op.resultStatus=resultStatus; op.requestHash=requestHash; op.createdAt=now; return op; }
}
