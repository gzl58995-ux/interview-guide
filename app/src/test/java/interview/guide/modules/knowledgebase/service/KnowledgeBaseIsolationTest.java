package interview.guide.modules.knowledgebase.service;

import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.mapper.KnowledgeBaseMapper;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.RagChatMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("知识库数据按用户隔离")
class KnowledgeBaseIsolationTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private RagChatMessageRepository ragChatMessageRepository;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private FileStorageService fileStorageService;

    private KnowledgeBaseListService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new AuthPrincipal(1L, "alice", false));
        service = new KnowledgeBaseListService(knowledgeBaseRepository, ragChatMessageRepository,
            knowledgeBaseMapper, fileStorageService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("列表只查询当前用户")
    void listScopedToCurrentUser() {
        when(knowledgeBaseRepository.findByUserIdOrderByUploadedAtDesc(1L)).thenReturn(List.of());
        when(knowledgeBaseMapper.toListItemDTOList(anyList())).thenReturn(List.of());

        service.listKnowledgeBases(null, null);

        verify(knowledgeBaseRepository).findByUserIdOrderByUploadedAtDesc(1L);
        verify(knowledgeBaseRepository, never()).findAll();
    }

    @Test
    @DisplayName("他人知识库详情不可见")
    void otherUsersKnowledgeBaseInvisible() {
        when(knowledgeBaseRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThat(service.getKnowledgeBase(7L)).isEmpty();
        assertThat(service.getKnowledgeBaseEntity(7L)).isEmpty();
    }

    @Test
    @DisplayName("分类统计只统计当前用户")
    void categoriesScopedToCurrentUser() {
        when(knowledgeBaseRepository.findCategoriesByUserId(1L)).thenReturn(List.of("JVM"));

        assertThat(service.getAllCategories()).containsExactly("JVM");
        verify(knowledgeBaseRepository).findCategoriesByUserId(1L);
    }
}
