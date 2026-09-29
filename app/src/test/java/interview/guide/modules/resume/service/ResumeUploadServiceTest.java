package interview.guide.modules.resume.service;

import interview.guide.common.config.AppConfigProperties;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.transaction.TransactionalExecutor;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.file.FileValidationService;
import interview.guide.modules.resume.listener.AnalyzeStreamProducer;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历上传求职方向")
class ResumeUploadServiceTest {

  @Mock
  private ResumeParseService parseService;
  @Mock
  private FileStorageService storageService;
  @Mock
  private ResumePersistenceService persistenceService;
  @Mock
  private AppConfigProperties appConfig;
  @Mock
  private FileValidationService fileValidationService;
  @Mock
  private AnalyzeStreamProducer analyzeStreamProducer;
  @Mock
  private ResumeRepository resumeRepository;
  @Mock
  private TransactionalExecutor transactionalExecutor;
  @Mock
  private MultipartFile file;

  private ResumeUploadService service;

  @BeforeEach
  void setUp() {
    service = new ResumeUploadService(parseService, storageService, persistenceService,
        appConfig, fileValidationService, analyzeStreamProducer, resumeRepository,
        transactionalExecutor, registry());
  }

  private ResumePromptRegistry registry() {
    ResumeAnalysisProperties properties = new ResumeAnalysisProperties();
    properties.setDefaultDirection("TECH");
    Map<String, ResumeAnalysisProperties.PromptRoute> routes = new LinkedHashMap<>();
    routes.put("TECH", route("classpath:prompts/resume-analysis/tech/system.st",
        "classpath:prompts/resume-analysis/tech/user.st"));
    routes.put("SALES_BD", route("classpath:prompts/resume-analysis/sales-bd/system.st",
        "classpath:prompts/resume-analysis/sales-bd/user.st"));
    properties.setPromptRoutes(routes);
    return new ResumePromptRegistry(properties, new DefaultResourceLoader());
  }

  private ResumeAnalysisProperties.PromptRoute route(String system, String user) {
    ResumeAnalysisProperties.PromptRoute route = new ResumeAnalysisProperties.PromptRoute();
    route.setSystemPromptPath(system);
    route.setUserPromptPath(user);
    return route;
  }

  private void stubHappyPath(String expectedDirection) {
    when(appConfig.getAllowedTypes()).thenReturn(List.of("application/pdf"));
    when(parseService.detectContentType(file)).thenReturn("application/pdf");
    when(persistenceService.findExistingResume(file)).thenReturn(Optional.empty());
    when(parseService.parseResume(file)).thenReturn("解析后的正文");
    when(storageService.uploadResume(file)).thenReturn("keys/1");
    when(storageService.getFileUrl("keys/1")).thenReturn("url");
    ResumeEntity saved = new ResumeEntity();
    saved.setId(5L);
    saved.setOriginalFilename("a.pdf");
    when(persistenceService.saveResume(file, "解析后的正文", "keys/1", "url", expectedDirection))
        .thenReturn(saved);
    when(analyzeStreamProducer.sendAnalyzeTask(5L)).thenReturn(true);
  }

  @Test
  @DisplayName("上传时把选择的方向写入简历")
  void savesSelectedDirection() {
    stubHappyPath("SALES_BD");

    service.uploadAndAnalyze(file, "SALES_BD");

    verify(persistenceService).saveResume(file, "解析后的正文", "keys/1", "url", "SALES_BD");
  }

  @Test
  @DisplayName("未传方向时按默认技术类入库")
  void defaultsToTechWhenBlank() {
    stubHappyPath("TECH");

    service.uploadAndAnalyze(file, " ");

    verify(persistenceService).saveResume(file, "解析后的正文", "keys/1", "url", "TECH");
  }

  @Test
  @DisplayName("未知方向直接拒绝且不产生解析存储入库副作用")
  void rejectsUnknownDirection() {
    assertThatThrownBy(() -> service.uploadAndAnalyze(file, "UNKNOWN"))
        .isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESUME_JOB_DIRECTION_UNSUPPORTED.getCode()));

    verifyNoInteractions(parseService, storageService, persistenceService, fileValidationService);
  }

  @Test
  @DisplayName("重复简历保留已有方向且不重新入库")
  void duplicateKeepsExistingDirection() {
    ResumeEntity existing = new ResumeEntity();
    existing.setId(9L);
    existing.setOriginalFilename("a.pdf");
    existing.setJobDirection("TECH");
    when(persistenceService.findExistingResume(file)).thenReturn(Optional.of(existing));
    when(persistenceService.getLatestAnalysisAsDTO(9L)).thenReturn(Optional.empty());

    service.uploadAndAnalyze(file, "SALES_BD");

    verify(persistenceService, never()).saveResume(any(), anyString(), anyString(), anyString(), anyString());
    assertThat(existing.getJobDirection()).isEqualTo("TECH");
  }
}
