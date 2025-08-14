package kr.hhplus.be.server.common.util;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 데드락 방지를 위한 락 순서 관리자
 * 
 * 여러 리소스에 대해 동시에 락을 획득할 때 항상 동일한 순서로 락을 획득하도록 보장하여
 * 데드락을 방지합니다.
 * 
 * 예시:
 * - Thread A: Product(1) → Product(3) 순서로 락 획득
 * - Thread B: Product(3) → Product(1) 순서로 락 획득 시도 → 데드락 발생
 * 
 * 해결:
 * - 모든 스레드가 Product(1) → Product(3) 순서로 락 획득하도록 강제
 */
@Component
public class LockOrderManager {

    /**
     * ID 리스트를 정렬하여 일관된 락 획득 순서를 보장합니다.
     * 
     * @param resourceIds 정렬할 리소스 ID 리스트
     * @return 오름차순으로 정렬되고 중복이 제거된 ID 리스트
     */
    public List<Long> getOrderedLockIds(List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        
        return resourceIds.stream()
                .distinct()  // 중복 제거
                .sorted()    // 오름차순 정렬
                .collect(Collectors.toList());
    }

    /**
     * 두 개의 ID를 정렬하여 락 획득 순서를 결정합니다.
     * 
     * @param id1 첫 번째 ID
     * @param id2 두 번째 ID
     * @return 정렬된 ID 리스트 [작은값, 큰값]
     */
    public List<Long> getOrderedLockIds(Long id1, Long id2) {
        if (id1 == null && id2 == null) {
            return List.of();
        }
        if (id1 == null) {
            return List.of(id2);
        }
        if (id2 == null) {
            return List.of(id1);
        }
        if (id1.equals(id2)) {
            return List.of(id1);
        }
        
        return id1 < id2 ? List.of(id1, id2) : List.of(id2, id1);
    }

    /**
     * 가변 인자로 받은 ID들을 정렬합니다.
     * 
     * @param resourceIds 정렬할 리소스 ID들
     * @return 정렬된 ID 리스트
     */
    public List<Long> getOrderedLockIds(Long... resourceIds) {
        if (resourceIds == null || resourceIds.length == 0) {
            return List.of();
        }
        
        return List.of(resourceIds).stream()
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}