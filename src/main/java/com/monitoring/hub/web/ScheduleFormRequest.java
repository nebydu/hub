package com.monitoring.hub.web;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.monitoring.hub.domain.job.JobType;

/**
 * Thymeleaf 폼(`POST /ui/schedules`) 입력. spec §4.2 데모 UI 단순화 형태.
 *
 * <p>한 폼에 SCRIPT_JOB과 LOG_JOB 양쪽 필드를 모두 두고, {@code jobType}에 따라
 * 해당 그룹만 spec Map에 포함한다 — 데모 UI는 분리 화면을 두지 않는다는 spec
 * §4.2 결정 그대로.
 *
 * <p>모든 옵션 필드는 nullable. 빈 문자열은 spec에서 제외해 Agent 측 디코더가
 * 누락을 그대로 인지하도록 한다.
 */
public record ScheduleFormRequest(
        JobType jobType,
        String cron,
        String targetAgentId,
        String scriptPath,
        /** 콤마(,) 구분 단일 입력. 데모 단순 처리. */
        String args,
        Integer timeoutSeconds,
        Integer outputCapBytes,
        String logPath,
        String pattern,
        String encoding
) {

    /** {@link JobType}에 맞는 spec Map을 생성. spec §5.1.1 / §5.1.2 필드 이름 그대로. */
    public Map<String, Object> toSpec() {
        return switch (jobType) {
            case SCRIPT_JOB -> buildScriptSpec();
            case LOG_JOB -> buildLogSpec();
        };
    }

    private Map<String, Object> buildScriptSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        putIfPresent(spec, "script_path", scriptPath);
        List<String> argList = splitArgs(args);
        if (!argList.isEmpty()) {
            spec.put("args", argList);
        }
        if (timeoutSeconds != null) {
            spec.put("timeout_seconds", timeoutSeconds);
        }
        if (outputCapBytes != null) {
            spec.put("output_cap_bytes", outputCapBytes);
        }
        return spec;
    }

    private Map<String, Object> buildLogSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        putIfPresent(spec, "log_path", logPath);
        putIfPresent(spec, "pattern", pattern);
        putIfPresent(spec, "encoding", encoding);
        return spec;
    }

    private static void putIfPresent(Map<String, Object> spec, String key, String value) {
        if (value != null && !value.isBlank()) {
            spec.put(key, value.trim());
        }
    }

    private static List<String> splitArgs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
