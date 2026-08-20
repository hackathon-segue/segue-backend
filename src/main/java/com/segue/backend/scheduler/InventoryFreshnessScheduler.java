package com.segue.backend.scheduler;

import com.segue.backend.domain.Inventory;
import com.segue.backend.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 데모 재고의 확인 시각(checked_at)을 주기적으로 갱신한다.
 *
 * DecisionEngine 은 checked_at 이 inventory.freshness-hours(기본 12시간)를 넘긴 재고를 미확인으로
 * 보고 확정 경로에서 제외한다(기능명세서 6번). 그런데 checked_at 은 DataLoader 가 기동할 때만
 * 갱신하므로, 서버를 12시간 넘게 켜두면 모든 재고가 미확인이 되어 네 가지 결과가 전부 추가 상담으로
 * 수렴한다. 로컬에서는 자주 재기동해 드러나지 않지만 배포 서버에서는 반드시 마주치는 문제다.
 *
 * 실제 서비스라면 POS 연동이 재고 상태와 확인 시각을 계속 갱신한다. 이 스케줄러는 MVP 에서 그
 * 주기적 동기화를 대신하며, 보유 여부(confirmed 포함)는 건드리지 않고 시각만 갱신한다.
 * 따라서 CA 가 수동으로 confirmed=false 로 만들어 둔 재고는 계속 미확인으로 남는다.
 *
 * inventory.freshness-refresh.enabled=false 로 끌 수 있다(신선도 게이트 자체를 시연할 때 사용).
 */
@Component
@RequiredArgsConstructor
public class InventoryFreshnessScheduler {

    private static final Logger log = LoggerFactory.getLogger(InventoryFreshnessScheduler.class);

    private final InventoryRepository inventoryRepository;

    @Value("${inventory.freshness-refresh.enabled:true}")
    private boolean enabled;

    /** 신선도 기준(12시간)보다 충분히 짧아야 의미가 있으므로 1시간 주기로 돈다. */
    @Scheduled(fixedDelayString = "${inventory.freshness-refresh.interval-ms:3600000}",
               initialDelayString = "${inventory.freshness-refresh.interval-ms:3600000}")
    @Transactional
    public void refreshCheckedAt() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Inventory> inventories = inventoryRepository.findAll();
        inventories.forEach(inventory -> inventory.setCheckedAt(now));
        log.info("재고 확인 시각 갱신 완료: {}건", inventories.size());
    }
}
