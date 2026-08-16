package com.segue.backend.controller;

import com.segue.backend.dto.request.*;
import com.segue.backend.dto.response.*;
import com.segue.backend.service.ConsultationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** F3~F8: Last Intent 상담 플로우 (태블릿 진행 + 고객 모바일 결과 조회) */
@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    /** F3: 고객 발화 -> 구조화된 의도 */
    @PostMapping("/intent")
    public IntentStructureResponse structureIntent(@Valid @RequestBody IntentStructureRequest request) {
        return consultationService.structureIntent(request);
    }

    /** F4-1: 보충 질문 생성 (needsFollowUp=true 인 경우에만 호출) */
    @PostMapping("/followup-question")
    public FollowUpQuestionResponse followUpQuestion(@Valid @RequestBody FollowUpQuestionRequest request) {
        return consultationService.generateFollowUpQuestion(request);
    }

    /** F4-2: 보충 답변 반영해 의도 재구조화 (최대 1회) */
    @PostMapping("/followup-answer")
    public IntentStructureResponse followUpAnswer(@Valid @RequestBody FollowUpAnswerRequest request) {
        return consultationService.restructureWithFollowUp(request);
    }

    /** F5 + F6: 규칙 기반 결정 + Last Intent Card 생성 (프론트에서는 로딩 상태로만 표시) */
    @PostMapping("/decide")
    public LastIntentCardResponse decide(@Valid @RequestBody DecideRequest request) {
        return consultationService.decide(request);
    }

    /** F7 + F8: 실행 요청 접수 및 상담 결과 저장 */
    @PostMapping("/execute")
    public ExecuteResponse execute(@Valid @RequestBody ExecuteRequest request) {
        return consultationService.execute(request);
    }

    /** F8: 고객 모바일에서 자신의 상담 결과 목록 조회 */
    @GetMapping("/customers/{customerId}")
    public List<ConsultationResultResponse> getResults(@PathVariable Long customerId) {
        return consultationService.getResultsForCustomer(customerId);
    }

    /** 기능명세서 7번: CA가 실행 버튼 이후의 후속 처리 상태(실행 불가/후속 확인 필요 등)를 갱신 */
    @PatchMapping("/{consultationResultId}/execution-status")
    public ConsultationResultResponse updateExecutionStatus(@PathVariable Long consultationResultId,
                                                              @Valid @RequestBody ExecutionStatusUpdateRequest request) {
        return consultationService.updateExecutionStatus(consultationResultId, request);
    }
}
