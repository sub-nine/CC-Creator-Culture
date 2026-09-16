package com.sub9.productservice.category.domain.model;

// 유사도 파이프라인의 한 스테이지가 특정 카테고리 후보 하나에 대해 내리는 판정
// (어떤 카테고리에 대한 판정인지는 CategoryCandidateResult가 별도로 짝지어 들고 있음)
public sealed interface CategoryMatchResult {

    // 즉시 병합 가능할 만큼 유사함
    record Merge(CategoryHashtagMatchType matchType, double similarity) implements CategoryMatchResult {
    }

    // 병합 가능성은 있으나 애매함 - 관리자 승인 대기로 연결
    record PendingApproval(CategoryHashtagMatchType matchType, double similarity) implements CategoryMatchResult {
    }

    // 이 스테이지 기준으론 유사한 카테고리가 없음(정상 판단)
    record NotSimilar() implements CategoryMatchResult {
    }

    // 스테이지 실행 자체가 실패함(타임아웃/예외 등, 비정상) - "유사하지 않음"과는 구분해서 다뤄야 함
    record Failed(String reason) implements CategoryMatchResult {
    }

    // 지금까지 누적된 결과(this)에 다음 스테이지 결과(next)를 합쳐 새 누적 결과를 정한다.
    // 우선순위: Merge(찾는 즉시 확정) > PendingApproval(한 번 찾으면 계속 유지) > NotSimilar/Failed(서로
    // 우선순위 없이 더 뒤, 즉 더 정확한 스테이지의 결과를 신뢰해 갱신됨)
    default CategoryMatchResult combineWith(CategoryMatchResult next) {
        if (this instanceof Merge) {
            return this;
        }
        if (next instanceof Merge) {
            return next;
        }
        if (this instanceof PendingApproval) {
            return this;
        }
        return next;
    }
}
