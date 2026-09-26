package interview.guide.modules.voiceinterview.controller;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.common.result.Result;
import interview.guide.modules.voiceinterview.dto.VoiceEvaluationStatusDTO;
import interview.guide.modules.voiceinterview.listener.VoiceEvaluateStreamProducer;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.service.VoiceInterviewEvaluationService;
import interview.guide.modules.voiceinterview.service.VoiceInterviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("语音评估接口")
class VoiceInterviewControllerEvaluationTest {

    @Mock
    private VoiceInterviewService voiceInterviewService;

    @Mock
    private VoiceInterviewEvaluationService evaluationService;

    @Mock
    private VoiceEvaluateStreamProducer voiceEvaluateStreamProducer;

    @InjectMocks
    private VoiceInterviewController controller;

    @Test
    @DisplayName("查询评估状态时应返回状态更新时间")
    void shouldExposeEvaluationStatusUpdatedAt() throws ReflectiveOperationException {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 27, 12, 30);
        VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
            .id(1L)
            .evaluateStatus(AsyncTaskStatus.PENDING)
            .updatedAt(updatedAt)
            .build();
        when(voiceInterviewService.requireOwnedSession(1L)).thenReturn(session);

        Result<VoiceEvaluationStatusDTO> result = controller.getEvaluation(1L);

        Field field = VoiceEvaluationStatusDTO.class.getDeclaredField("evaluateStatusUpdatedAt");
        field.setAccessible(true);
        assertThat(field.get(result.getData())).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("用户对 PENDING 评估重新生成时应真实重新投递")
    void shouldRequeuePendingEvaluationOnExplicitRetry() {
        VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
            .id(2L)
            .evaluateStatus(AsyncTaskStatus.PENDING)
            .build();
        when(voiceInterviewService.requireOwnedSession(2L)).thenReturn(session);

        Result<VoiceEvaluationStatusDTO> result = controller.generateEvaluation(2L);

        assertThat(result.getData().getEvaluateStatus()).isEqualTo(AsyncTaskStatus.PENDING.name());
        verify(voiceInterviewService).triggerEvaluation(2L);
    }
}
