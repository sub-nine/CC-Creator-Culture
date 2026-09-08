package com.sub9.productservice.category.application.command.service;

import com.sub9.productservice.category.application.command.port.in.AddHashtagsToProductUseCase;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryCommandService implements AddHashtagsToProductUseCase {
    private final HashtagCommandRepository hashtagCommandRepository;

    @Override
    public void addHashtagsToProduct(UUID productId, List<String> hashtagStrings) {
        // TODO: 1. hashtagStrings 정규화(영어는 대문자 등) + 중복 제거

        // TODO: 2. 정규화된 이름마다 Hashtag Upsert(INSERT ... ON CONFLICT DO NOTHING + 재조회)

        // TODO: 3. Hashtag마다 ProductHashtag Upsert(INSERT ... ON CONFLICT DO NOTHING RETURNING id)
        //          - 실제로 새로 삽입된 경우에만 usage_count 원자 증가(UPDATE ... SET usage_count = usage_count + 1)
        //          - 실제로 새로 삽입된 Hashtag만 "새로 추가된 목록"에 수집(이미 연결된 건 자동 스킵)

        // TODO: 4. "새로 추가된 목록"에 대해 3번과 같은 트랜잭션 안에서 Outbox 테이블에 이벤트 기록
        //          (HashtagAddedEvent 등 - productId, hashtagId, status=PENDING, attempt_count=0으로 저장)
        //          - OutboxStatus : PENDING(대기) → PROCESSING(릴레이가 claim) → PUBLISHED(발행 성공)
        //                                                              └→ FAILED(재시도 한도 초과, 데드레터)
        //          - 별도 Outbox Relay(스케줄러/폴러)가
        //              UPDATE ... SET status='PROCESSING' WHERE id IN
        //                (SELECT id FROM outbox WHERE status='PENDING' ORDER BY created_at LIMIT :n
        //                 FOR UPDATE SKIP LOCKED)
        //            로 안전하게 claim한 뒤 Kafka 발행
        //          - 발행 성공 시 status=PUBLISHED, 실패 시 attempt_count 증가 후 status=PENDING으로 되돌려 재시도,
        //            attempt_count가 임계치 초과하면 status=FAILED(데드레터, 알림)
        //          - PROCESSING 상태로 오래 멈춰있는 행(릴레이 크래시 등)은 claimed_at 기준 타임아웃 시 PENDING으로 복구
        //          - 소비 측(category 패키지의 @KafkaListener)에서 유사도 비교 후 병합/신규 분기 처리
    }
}
