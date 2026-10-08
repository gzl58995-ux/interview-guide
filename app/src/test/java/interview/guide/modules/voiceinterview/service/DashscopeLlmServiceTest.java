package interview.guide.modules.voiceinterview.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import interview.guide.common.config.LlmProviderProperties;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptSanitizer;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashscopeLlmService 日志隐私测试")
class DashscopeLlmServiceTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;
  @Mock
  private VoiceInterviewPromptService promptService;
  @Mock
  private ResumeRepository resumeRepository;

  private final VoiceInterviewProperties properties = new VoiceInterviewProperties();
  private final PromptSanitizer promptSanitizer = new PromptSanitizer(mock(LlmProviderProperties.class));

  private DashscopeLlmService service;

  private ListAppender<ILoggingEvent> logAppender;
  private Logger serviceLogger;

  @BeforeEach
  void setUp() {
    service = new DashscopeLlmService(
        llmProviderRegistry, promptService, resumeRepository, properties, promptSanitizer);
    serviceLogger = (Logger) LoggerFactory.getLogger(DashscopeLlmService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    serviceLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    serviceLogger.detachAppender(logAppender);
  }

  private String capturedLogs() {
    StringBuilder sb = new StringBuilder();
    for (ILoggingEvent event : logAppender.list) {
      sb.append(event.getFormattedMessage()).append('\n');
    }
    return sb.toString();
  }

  private ILoggingEvent findEvent(Level level, String keyword) {
    return logAppender.list.stream()
        .filter(event -> event.getLevel() == level && event.getFormattedMessage().contains(keyword))
        .findFirst()
        .orElse(null);
  }

  @Test
  @DisplayName("同步调用日志不包含用户输入与模型回答原文，只记录长度")
  void shouldNotLogUserInputOrModelReply() {
    String userInputMarker = "用户敏感标记MARKER-USER-7f3a";
    String replyMarker = "回答敏感标记MARKER-REPLY-9b2c";
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec callResponse = mock(ChatClient.CallResponseSpec.class);
    ChatResponse chatResponse = mock(ChatResponse.class);
    Generation generation = mock(Generation.class);
    when(promptService.generateSystemPromptWithContext(anyString(), anyString(), isNull()))
        .thenReturn("系统提示");
    when(llmProviderRegistry.getVoiceChatClient("dashscope")).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponse);
    when(callResponse.chatResponse()).thenReturn(chatResponse);
    when(chatResponse.getResult()).thenReturn(generation);
    when(generation.getOutput()).thenReturn(new AssistantMessage(replyMarker));

    VoiceInterviewSessionEntity session = new VoiceInterviewSessionEntity();
    String reply = service.chat(userInputMarker, session, List.of());

    assertThat(reply).contains(replyMarker);
    String logs = capturedLogs();
    assertThat(logs).doesNotContain(userInputMarker);
    assertThat(logs).doesNotContain(replyMarker);
    ILoggingEvent replyEvent = findEvent(Level.INFO, "replyLength");
    assertThat(replyEvent).isNotNull();
    assertThat(replyEvent.getFormattedMessage()).contains("replyLength=" + replyMarker.length());
  }

  @Test
  @DisplayName("每轮对话把会话上的求职方向传给提示词组装")
  void passesSessionJobDirectionToPromptService() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec callResponse = mock(ChatClient.CallResponseSpec.class);
    ChatResponse chatResponse = mock(ChatResponse.class);
    Generation generation = mock(Generation.class);
    when(promptService.generateSystemPromptWithContext(anyString(), anyString(), isNull()))
        .thenReturn("系统提示");
    when(llmProviderRegistry.getVoiceChatClient("dashscope")).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponse);
    when(callResponse.chatResponse()).thenReturn(chatResponse);
    when(chatResponse.getResult()).thenReturn(generation);
    when(generation.getOutput()).thenReturn(new AssistantMessage("回复"));

    VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
        .jobDirection("SALES_BD")
        .build();
    service.chat("回答", session, List.of());

    ArgumentCaptor<String> directionCaptor = ArgumentCaptor.forClass(String.class);
    verify(promptService).generateSystemPromptWithContext(anyString(), directionCaptor.capture(), isNull());
    assertThat(directionCaptor.getValue()).isEqualTo("SALES_BD");
  }
}
