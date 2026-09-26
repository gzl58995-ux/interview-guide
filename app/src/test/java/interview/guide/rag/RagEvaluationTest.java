package interview.guide.rag;

import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseQueryService;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import interview.guide.modules.knowledgebase.service.RagQueryExecution;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.fail;

/**
 * RAG 测评（P1-01）。
 *
 * <p>运行方式见 app/src/test/resources/rag-eval/README.md：
 * <pre>RUN_RAG_EVAL=true REDIS_DATABASE=1 ./gradlew :app:ragEvaluation --no-daemon</pre>
 *
 * <p>双保险：rag-eval 标签（普通 :app:test 排除）+ RUN_RAG_EVAL 环境变量。
 * 环境使用独立可销毁的 PostgreSQL 逻辑库 interview_guide_rag_eval，不触碰开发库。
 */
@Tag("rag-eval")
@EnabledIfEnvironmentVariable(named = "RUN_RAG_EVAL", matches = "true")
@ActiveProfiles("rag-eval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RagEvaluationTest {

  private static final String EVAL_DB = "interview_guide_rag_eval";
  private static final String DATASET = "rag-eval/dataset-v1.jsonl";
  private static final String REPORT_DIR =
      System.getProperty("ragEval.reportDir", "build/reports/rag-eval");
  private static final String RUN_ID =
      "rag-eval-" + System.getenv().getOrDefault("RAG_EVAL_RUN_TAG", "baseline") + "-" + System.currentTimeMillis();

  private static String evalDbUrl;
  private static String sourceDbUrl;
  private static boolean dbCreated;

  @Autowired
  private KnowledgeBaseRepository knowledgeBaseRepository;
  @Autowired
  private KnowledgeBaseVectorService vectorService;
  @Autowired
  private KnowledgeBaseQueryService queryService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, Long> fixtureKbIds = new HashMap<>();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    if (evalDbUrl != null) {
      registry.add("spring.datasource.url", () -> evalDbUrl);
    }
  }

  @BeforeAll
  static void recreateDatabase() {
    // rag-eval Profile 已把 Redis database 固定为 1（可用 REDIS_DATABASE 覆盖），
    // 这里兜底拒绝显式指定 database 0 的运行，防止消费开发环境 Stream 消息
    String redisDatabase = System.getenv("REDIS_DATABASE");
    if ("0".equals(redisDatabase)) {
      fail("REDIS_DATABASE=0 会连接开发环境 Redis，测评运行拒绝启动");
    }
    String host = env("POSTGRES_HOST", "localhost");
    String port = env("POSTGRES_PORT", "5432");
    String user = env("POSTGRES_USER", "postgres");
    String password = env("POSTGRES_PASSWORD", "123456");
    evalDbUrl = "jdbc:postgresql://" + host + ":" + port + "/" + EVAL_DB;
    sourceDbUrl = "jdbc:postgresql://" + host + ":" + port + "/" + env("POSTGRES_DB", "interview_guide");
    try (Connection conn = DriverManager.getConnection(
        "jdbc:postgresql://" + host + ":" + port + "/postgres", user, password);
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP DATABASE IF EXISTS " + EVAL_DB);
      stmt.execute("CREATE DATABASE " + EVAL_DB);
      dbCreated = true;
      // 测评链路复用生产查询服务，需要登录用户上下文按 user_id 归属数据
      UserContext.set(new AuthPrincipal(1L, "rag-eval", false));
    } catch (Exception e) {
      fail("RAG 测评前置失败：无法重建独立测评数据库 " + EVAL_DB + "：" + e.getMessage());
    }
  }

  @AfterAll
  static void dropDatabase() {
    UserContext.clear();
    if (!dbCreated) {
      return;
    }
    try (Connection conn = DriverManager.getConnection(
        "jdbc:postgresql://" + env("POSTGRES_HOST", "localhost") + ":" + env("POSTGRES_PORT", "5432") + "/postgres",
        env("POSTGRES_USER", "postgres"), env("POSTGRES_PASSWORD", "123456"));
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP DATABASE IF EXISTS " + EVAL_DB + " WITH (FORCE)");
    } catch (Exception ignored) {
      // 清理失败不影响已生成的报告；下次运行会先 DROP 再 CREATE
    }
  }

  @Test
  void runEvaluation() {
    Map<String, Object> report = new LinkedHashMap<>();
    try {
      report = executeWithStages();
      RagEvalReportWriter.write(Path.of(REPORT_DIR), RUN_ID, report);
    } catch (Exception e) {
      String stage = e instanceof HarnessStageException h ? h.stage : "UNKNOWN";
      RagEvalReportWriter.writeHarnessError(Path.of(REPORT_DIR), RUN_ID, stage,
          e.getClass().getSimpleName() + ": " + e.getMessage());
      fail("RAG 测评在阶段 " + stage + " 失败：" + e.getMessage()
          + "，错误报告见 " + REPORT_DIR + "/" + RUN_ID + ".json");
    }
    long harnessErrors = ((List<?>) report.getOrDefault("samples", List.of())).stream()
        .filter(r -> "HARNESS_ERROR".equals(((Map<?, ?>) r).get("outcome"))).count();
    if (harnessErrors > 0) {
      fail(harnessErrors + " 个样本发生环境级错误，报告见 " + REPORT_DIR + "/" + RUN_ID + ".md");
    }
  }

  private Map<String, Object> executeWithStages() throws Exception {
    List<RagEvalSample> samples = stage("DATASET_LOAD", this::loadSamplesChecked);
    Map<String, String> fixtureContents = stage("FIXTURE_LOAD", () -> loadFixtures(samples));
    stageRun("PROVIDER_SETUP", this::seedProvidersFromSourceDatabase);
    Map<String, Integer> chunkCounts = stage("VECTORIZATION", () -> vectorizeFixtures(fixtureContents));

    List<Map<String, Object>> sampleResults = new ArrayList<>();
    List<Map<String, Object>> badCases = new ArrayList<>();
    List<Map<String, Object>> faithfulnessReview = new ArrayList<>();
    for (RagEvalSample sample : samples) {
      sampleResults.add(evaluateSample(sample, faithfulnessReview, badCases));
    }
    final List<Map<String, Object>> results = sampleResults;
    final List<Map<String, Object>> bad = badCases;
    final List<Map<String, Object>> faith = faithfulnessReview;
    return stage("REPORT", () -> buildReport(samples, results, bad, faith, chunkCounts));
  }

  private interface StageSupplier<T> {
    T get() throws Exception;
  }

  private interface StageRunnable {
    void run() throws Exception;
  }

  private <T> T stage(String name, StageSupplier<T> supplier) throws Exception {
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new HarnessStageException(name, e);
    }
  }

  private void stageRun(String name, StageRunnable runnable) throws Exception {
    try {
      runnable.run();
    } catch (Exception e) {
      throw new HarnessStageException(name, e);
    }
  }

  private static final class HarnessStageException extends RuntimeException {
    final String stage;

    HarnessStageException(String stage, Exception cause) {
      super(stage + ": " + cause.getMessage(), cause);
      this.stage = stage;
    }
  }

  private List<RagEvalSample> loadSamplesChecked() throws IOException {
    return loadSamples();
  }

  // ========== 样本执行 ==========

  private Map<String, Object> evaluateSample(RagEvalSample sample,
                                             List<Map<String, Object>> faithfulnessReview,
                                             List<Map<String, Object>> badCases) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", sample.id());
    result.put("split", sample.split());
    result.put("tags", sample.tags());
    result.put("question", sample.question());
    result.put("shouldReject", sample.shouldReject());
    result.put("evaluateGeneration", sample.evaluateGeneration());
    long totalStart = System.nanoTime();
    try {
      Long kbId = fixtureKbIds.get(sample.fixture());
      if (kbId == null) {
        throw new IllegalStateException("fixture 未向量化: " + sample.fixture());
      }
      List<Message> history = toMessages(sample.history());

      RagQueryExecution execution;
      if (sample.evaluateGeneration()) {
        List<String> chunks = new ArrayList<>();
        List<RagQueryExecution> trace = new ArrayList<>();
        queryService
            .answerQuestionStream(List.of(kbId), sample.question(), history, trace::add)
            .doOnNext(chunks::add)
            .blockLast();
        execution = trace.isEmpty()
            ? new RagQueryExecution(sample.question(), sample.question(), List.of(), 0, 0,
                List.of(), 0, 0, 0, String.join("", chunks), "NO_RESULT")
            : trace.getFirst();
      } else {
        execution = queryService.retrieveOnly(List.of(kbId), sample.question(), history);
      }

      List<String> hitEvidenceIds = new ArrayList<>();
      Integer firstHitRank = null;
      List<String> docTexts = execution.retrievedDocs().stream()
          .map(RagQueryExecution.RetrievedDoc::text).toList();
      for (RagEvalSample.Evidence evidence : sample.expectedEvidence()) {
        String normalizedEvidence = RagEvalSample.normalize(evidence.text());
        for (int i = 0; i < docTexts.size(); i++) {
          if (docTexts.get(i) != null
              && RagEvalSample.normalize(docTexts.get(i)).contains(normalizedEvidence)) {
            hitEvidenceIds.add(evidence.id());
            if (firstHitRank == null) {
              firstHitRank = i + 1;
            }
            break;
          }
        }
      }
      boolean hit = !hitEvidenceIds.isEmpty() && !sample.expectedEvidence().isEmpty();
      double evidenceRecall = sample.expectedEvidence().isEmpty() ? 1.0
          : (double) hitEvidenceIds.size() / sample.expectedEvidence().size();
      boolean predictedReject = "NO_RESULT".equals(execution.outcome());

      if (sample.evaluateGeneration() && !sample.shouldReject()) {
        faithfulnessReview.add(faithfulnessEntry(sample, execution));
      }

      result.put("outcome", execution.outcome());
      result.put("rewrittenQuestion", execution.rewrittenQuestion());
      result.put("attemptedQueries", execution.attemptedQueries());
      result.put("resolvedTopK", execution.resolvedTopK());
      result.put("resolvedMinScore", execution.resolvedMinScore());
      result.put("answer", execution.answer());
      result.put("retrievedDocs", execution.retrievedDocs().stream()
          .map(d -> Map.of(
              "rank", d.rank(),
              "score", d.score() != null ? d.score() : -1.0,
              "excerpt", d.text() != null && d.text().length() > 200
                  ? d.text().substring(0, 200) : d.text(),
              "contentHash", sha256Quiet(d.text())))
          .toList());
      result.put("hit", hit);
      result.put("hitEvidenceIds", hitEvidenceIds);
      result.put("firstHitRank", firstHitRank);
      result.put("evidenceRecall", evidenceRecall);
      result.put("predictedReject", predictedReject);
      result.put("rewriteMs", execution.rewriteDurationMs());
      result.put("retrievalMs", execution.retrievalDurationMs());
      result.put("generationMs", execution.generationDurationMs());
      result.put("totalMs", (System.nanoTime() - totalStart) / 1_000_000);

      collectBadCase(sample, result, firstHitRank, evidenceRecall, predictedReject, badCases);
      return result;
    } catch (Exception e) {
      result.put("outcome", "HARNESS_ERROR");
      result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
      result.put("totalMs", (System.nanoTime() - totalStart) / 1_000_000);
      badCases.add(badCase(sample, "HARNESS_ERROR",
          e.getClass().getSimpleName() + ": " + e.getMessage(), result));
      return result;
    }
  }

  private void collectBadCase(RagEvalSample sample, Map<String, Object> result, Integer firstHitRank,
                              double evidenceRecall, boolean predictedReject, List<Map<String, Object>> badCases) {
    String reason = null;
    String detail = null;
    if (sample.shouldReject() && !predictedReject) {
      reason = "OOS_NOT_REJECTED";
      detail = "知识库外问题未被拒答";
    } else if (!sample.shouldReject() && predictedReject) {
      reason = "FALSE_REJECT";
      detail = "站内问题被误拒答";
    } else if (!sample.shouldReject() && Boolean.FALSE.equals(result.get("hit"))) {
      reason = "NO_EVIDENCE_HIT";
      detail = "Top-K 内未命中任何证据锚点";
    } else if (!sample.shouldReject() && evidenceRecall < 1.0) {
      reason = "EVIDENCE_INCOMPLETE";
      detail = "证据锚点未全部召回，Recall=" + evidenceRecall;
    } else if (!sample.shouldReject() && firstHitRank != null && firstHitRank > 3) {
      reason = "LOW_FIRST_RANK";
      detail = "首个命中排名=" + firstHitRank;
    }
    if (reason != null) {
      badCases.add(badCase(sample, reason, detail, result));
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> badCase(RagEvalSample sample, String reason, String detail,
                                      Map<String, Object> result) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("id", sample.id());
    c.put("question", sample.question());
    c.put("tags", sample.tags());
    c.put("reason", reason);
    c.put("detail", detail);
    c.put("answerExcerpt", excerpt(result.get("answer"), 300));
    c.put("topDocs", ((List<Map<String, Object>>) result.getOrDefault("retrievedDocs", List.of()))
        .stream().limit(3).toList());
    c.put("expectedEvidence", sample.expectedEvidence().stream()
        .map(RagEvalSample.Evidence::text).toList());
    return c;
  }

  private String excerpt(Object text, int limit) {
    if (!(text instanceof String s) || s.isEmpty()) {
      return "(空)";
    }
    return s.length() > limit ? s.substring(0, limit) + "…" : s;
  }

  private Map<String, Object> faithfulnessEntry(RagEvalSample sample, RagQueryExecution execution) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("id", sample.id());
    f.put("question", sample.question());
    f.put("expectedEvidence", sample.expectedEvidence().stream().map(RagEvalSample.Evidence::text).toList());
    f.put("answer", execution.answer());
    f.put("evidenceIds", sample.expectedEvidence().stream().map(RagEvalSample.Evidence::id).toList());
    f.put("faithfulnessStatus", "PENDING");
    f.put("unsupportedClaims", null);
    f.put("reason", null);
    f.put("reviewer", null);
    f.put("reviewedAt", null);
    return f;
  }

  // ========== 报告汇总 ==========

  private Map<String, Object> buildReport(List<RagEvalSample> samples,
                                          List<Map<String, Object>> sampleResults,
                                          List<Map<String, Object>> badCases,
                                          List<Map<String, Object>> faithfulnessReview,
                                          Map<String, Integer> chunkCounts) throws Exception {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("runId", RUN_ID);
    report.put("timestamp", java.time.Instant.now().toString());
    report.put("environment", buildEnvironment(chunkCounts));
    report.put("metrics", buildMetrics(sampleResults));
    report.put("rejection", RagEvalMetrics.rejectionMetrics(sampleResults));
    report.put("badCases", badCases);
    report.put("faithfulnessReview", faithfulnessReview);
    report.put("samples", sampleResults);
    report.put("datasetVersion", DATASET);
    report.put("datasetSize", samples.size());
    return report;
  }

  private Map<String, Object> buildEnvironment(Map<String, Integer> chunkCounts) throws Exception {
    Map<String, Object> env = new LinkedHashMap<>();
    env.put("gitSha", gitSha());
    env.put("datasetSha256", sha256(new ClassPathResource(DATASET).getInputStream().readAllBytes()));
    for (String fixture : List.of("java-guide.md", "project-guide.md")) {
      env.put("fixtureSha256:" + fixture,
          sha256(new ClassPathResource("rag-eval/fixtures/" + fixture).getInputStream().readAllBytes()));
    }
    for (String prompt : List.of("knowledgebase-query-system.st", "knowledgebase-query-user.st",
        "knowledgebase-query-rewrite.st")) {
      env.put("promptSha256:" + prompt,
          sha256(new ClassPathResource("prompts/" + prompt).getInputStream().readAllBytes()));
    }
    env.put("rewriteEnabled", System.getenv("APP_AI_RAG_REWRITE_ENABLED") == null
        ? "false(rag-eval Profile 默认)" : System.getenv("APP_AI_RAG_REWRITE_ENABLED"));
    env.put("mergeOriginalQuery", System.getenv("APP_AI_RAG_MERGE_ORIGINAL_QUERY") == null
        ? "false(默认)" : System.getenv("APP_AI_RAG_MERGE_ORIGINAL_QUERY"));
    env.put("chunkSize", System.getenv("APP_AI_RAG_VECTORIZATION_CHUNK_SIZE") == null
        ? "800(默认)" : System.getenv("APP_AI_RAG_VECTORIZATION_CHUNK_SIZE"));
    env.put("redisDatabase", System.getenv().getOrDefault("REDIS_DATABASE", "1(rag-eval Profile 默认)"));
    env.put("evalDatabase", EVAL_DB + "（每 run 重建）");
    env.put("tokenUsage", "null（当前链路 .content() 无法取得 usage，补齐属 P1-04）");
    env.put("springAiVersion", springAiVersion());
    env.putAll(providerSnapshot());
    chunkCounts.forEach((fixture, count) -> env.put("chunkCount:" + fixture, count));
    return env;
  }

  /**
   * 从测评库读取 Provider 快照（模型、Embedding 模型、温度），不含密钥。
   */
  private Map<String, Object> providerSnapshot() throws Exception {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    try (Connection conn = DriverManager.getConnection(evalDbUrl,
        env("POSTGRES_USER", "postgres"), env("POSTGRES_PASSWORD", "123456"));
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery(
            "SELECT id, model, embedding_model, temperature FROM llm_provider_config"
                + " WHERE id IN (SELECT default_chat_provider_id FROM llm_global_setting"
                + "   UNION SELECT default_embedding_provider_id FROM llm_global_setting)")) {
      while (rs.next()) {
        String id = rs.getString("id");
        snapshot.put("provider:" + id + ".model", rs.getString("model"));
        snapshot.put("provider:" + id + ".embeddingModel", rs.getString("embedding_model"));
        snapshot.put("provider:" + id + ".temperature", rs.getObject("temperature"));
      }
    }
    try (Connection conn = DriverManager.getConnection(evalDbUrl,
        env("POSTGRES_USER", "postgres"), env("POSTGRES_PASSWORD", "123456"));
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT default_chat_provider_id, default_embedding_provider_id"
            + " FROM llm_global_setting")) {
      while (rs.next()) {
        snapshot.put("defaultChatProvider", rs.getString(1));
        snapshot.put("defaultEmbeddingProvider", rs.getString(2));
      }
    }
    return snapshot;
  }

  private String springAiVersion() {
    Package pkg = org.springframework.ai.chat.client.ChatClient.class.getPackage();
    String version = pkg != null ? pkg.getImplementationVersion() : null;
    return version != null ? version : "unknown";
  }

  private String sha256Quiet(String text) {
    try {
      return sha256(text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    } catch (Exception e) {
      return "error";
    }
  }

  private Map<String, Object> buildMetrics(List<Map<String, Object>> results) {
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("overall", RagEvalMetrics.metricsOf(results));
    metrics.put("dev", RagEvalMetrics.metricsOf(results.stream()
        .filter(r -> "dev".equals(r.get("split"))).toList()));
    metrics.put("holdout", RagEvalMetrics.metricsOf(results.stream()
        .filter(r -> "holdout".equals(r.get("split"))).toList()));
    results.stream().map(r -> (List<String>) r.get("tags"))
        .flatMap(List::stream).distinct().sorted()
        .forEach(tag -> metrics.put("tag:" + tag, RagEvalMetrics.metricsOf(results.stream()
            .filter(r -> ((List<String>) r.get("tags")).contains(tag)).toList())));
    return metrics;
  }

  // ========== 数据准备 ==========

  private List<RagEvalSample> loadSamples() throws IOException {
    List<String> lines = new String(
            new ClassPathResource(DATASET).getInputStream().readAllBytes(), StandardCharsets.UTF_8)
        .lines().filter(line -> !line.isBlank()).toList();
    List<RagEvalSample> samples = new ArrayList<>();
    for (String line : lines) {
      Map<String, Object> raw = objectMapper.readValue(line, Map.class);
      samples.add(RagEvalSample.fromMap(raw));
    }
    return samples;
  }

  private Map<String, String> loadFixtures(List<RagEvalSample> samples) throws IOException {
    Map<String, String> contents = new HashMap<>();
    for (RagEvalSample sample : samples) {
      if (!contents.containsKey(sample.fixture())) {
        contents.put(sample.fixture(), new String(
            new ClassPathResource("rag-eval/fixtures/" + sample.fixture())
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return contents;
  }

  private Map<String, Integer> vectorizeFixtures(Map<String, String> fixtureContents) {
    Map<String, Integer> chunkCounts = new LinkedHashMap<>();
    fixtureContents.forEach((fixture, content) -> {
      KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
      kb.setUserId(1L);
      kb.setName("rag-eval-" + fixture);
      kb.setOriginalFilename(fixture);
      kb.setFileHash("rag-eval-" + RUN_ID + "-" + fixture);
      kb.setContentType("text/markdown");
      kb.setFileSize((long) content.length());
      kb.setVectorStatus(VectorStatus.PENDING);
      KnowledgeBaseEntity saved = knowledgeBaseRepository.save(kb);
      vectorService.vectorizeAndStore(saved.getId(), content);
      fixtureKbIds.put(fixture, saved.getId());
      // 使用生产链路真实写入的 chunkCount（由向量化快照更新），保证与 chunk-size 配置一致
      int chunks = knowledgeBaseRepository.findById(saved.getId())
          .map(KnowledgeBaseEntity::getChunkCount).orElse(0);
      chunkCounts.put(fixture, chunks);
    });
    return chunkCounts;
  }

  /**
   * 测评库启动时会按 application.yml seed 空 key 的 Provider，
   * 这里用开发库的真实配置整体替换（密文使用相同加密钥，可直接复制），
   * 使 Embedding / Chat 走与开发环境一致的 Provider。
   */
  private void seedProvidersFromSourceDatabase() throws Exception {
    try (Connection source = DriverManager.getConnection(sourceDbUrl,
        env("POSTGRES_USER", "postgres"), env("POSTGRES_PASSWORD", "123456"));
        Connection target = DriverManager.getConnection(evalDbUrl,
            env("POSTGRES_USER", "postgres"), env("POSTGRES_PASSWORD", "123456"))) {
      replaceTable(source, target, "llm_provider_config");
      replaceTable(source, target, "llm_global_setting");
    }
  }

  private void replaceTable(Connection source, Connection target, String table) throws Exception {
    try (Statement tgt = target.createStatement()) {
      tgt.execute("DELETE FROM " + table);
    }
    copyTable(source, target, table);
  }

  private void copyTable(Connection source, Connection target, String table) throws Exception {
    try (Statement src = source.createStatement();
        var rs = src.executeQuery("SELECT * FROM " + table);
        Statement tgt = target.createStatement()) {
      var meta = rs.getMetaData();
      int columnCount = meta.getColumnCount();
      List<String> columns = new ArrayList<>();
      for (int i = 1; i <= columnCount; i++) {
        columns.add(meta.getColumnName(i));
      }
      String columnList = String.join(", ", columns);
      while (rs.next()) {
        List<String> values = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
          Object value = rs.getObject(i);
          values.add(value == null ? "NULL" : "'" + value.toString().replace("'", "''") + "'");
        }
        tgt.executeUpdate("INSERT INTO " + table + " (" + columnList + ") VALUES ("
            + String.join(", ", values) + ")");
      }
    }
  }

  private List<Message> toMessages(List<RagEvalSample.HistoryMessage> history) {
    List<Message> messages = new ArrayList<>();
    for (RagEvalSample.HistoryMessage m : history) {
      messages.add("assistant".equals(m.role())
          ? new AssistantMessage(m.content()) : new UserMessage(m.content()));
    }
    return messages;
  }

  // ========== 工具 ==========

  private static long percentile(List<Long> sorted, double p) {
    int index = (int) Math.min(sorted.size() - 1, Math.round(p * (sorted.size() - 1)));
    return sorted.get(index);
  }

  private static double round(double value) {
    return Math.round(value * 10000) / 10000.0;
  }

  private static String gitSha() {
    try {
      Process p = new ProcessBuilder("git", "rev-parse", "HEAD")
          .redirectErrorStream(true).start();
      p.waitFor();
      return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
      return "unknown";
    }
  }

  private static String sha256(byte[] data) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(data)).substring(0, 16);
  }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
