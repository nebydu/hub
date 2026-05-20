package com.monitoring.hub.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.job.JobType;
import com.monitoring.hub.domain.job.ScheduleDefinition;
import com.monitoring.hub.scheduler.ScheduleService;
import com.monitoring.hub.store.AgentRegistry;
import com.monitoring.hub.store.AuditRingBuffer;
import com.monitoring.hub.store.CommandRingBuffer;
import com.monitoring.hub.store.HeartbeatLatestMap;
import com.monitoring.hub.store.JobResultRingBuffer;
import com.monitoring.hub.store.ScheduleRegistry;

@WebMvcTest(controllers = UiController.class)
class UiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private ScheduleService scheduleService;
    @MockBean private AgentRegistry agentRegistry;
    @MockBean private ScheduleRegistry scheduleRegistry;
    @MockBean private AuditRingBuffer auditBuffer;
    @MockBean private JobResultRingBuffer jobResultBuffer;
    @MockBean private CommandRingBuffer commandBuffer;
    @MockBean private HeartbeatLatestMap heartbeatMap;
    @MockBean private AppProperties appProperties;

    @BeforeEach
    void stubProps() {
        when(appProperties.agent()).thenReturn(new AppProperties.Agent(30));
        when(agentRegistry.findAll()).thenReturn(List.of());
        when(scheduleRegistry.findAll()).thenReturn(List.of());
        when(auditBuffer.snapshot()).thenReturn(List.of());
        when(jobResultBuffer.snapshot()).thenReturn(List.of());
        when(commandBuffer.snapshot()).thenReturn(List.of());
        when(heartbeatMap.snapshot()).thenReturn(Map.of());
    }

    @Test
    void indexRendersWithAllExpectedModelAttributes() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists(
                        "now", "heartbeatTimeoutSeconds",
                        "agents", "heartbeats", "schedules",
                        "commands", "jobResults", "auditEvents", "jobTypes"));
    }

    @Test
    void postScheduleFormBuildsScriptSpecAndRedirects() throws Exception {
        when(scheduleService.register(any(), any(), any(), any()))
                .thenReturn(new ScheduleDefinition(
                        "schedule-1", "job-1", "agent-001", "0 0/5 * * * ?", true));

        mvc.perform(post("/ui/schedules")
                        .param("jobType", "SCRIPT_JOB")
                        .param("cron", "0 0/5 * * * ?")
                        .param("targetAgentId", "agent-001")
                        .param("scriptPath", "/opt/scripts/check_disk.sh")
                        .param("args", "--threshold,80")
                        .param("timeoutSeconds", "30")
                        .param("outputCapBytes", "65536"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> specCaptor = ArgumentCaptor.forClass(Map.class);
        verify(scheduleService).register(
                eq(JobType.SCRIPT_JOB),
                specCaptor.capture(),
                eq("agent-001"),
                eq("0 0/5 * * * ?"));

        Map<String, Object> spec = specCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(spec)
                .containsEntry("script_path", "/opt/scripts/check_disk.sh")
                .containsEntry("args", List.of("--threshold", "80"))
                .containsEntry("timeout_seconds", 30)
                .containsEntry("output_cap_bytes", 65536);
    }

    @Test
    void postScheduleFormBuildsLogSpec() throws Exception {
        when(scheduleService.register(any(), any(), any(), any()))
                .thenReturn(new ScheduleDefinition(
                        "schedule-2", "job-2", "agent-002", "0 0/10 * * * ?", true));

        mvc.perform(post("/ui/schedules")
                        .param("jobType", "LOG_JOB")
                        .param("cron", "0 0/10 * * * ?")
                        .param("targetAgentId", "agent-002")
                        .param("logPath", "/var/log/app/error.log")
                        .param("pattern", "ERROR|FATAL")
                        .param("encoding", "UTF-8"))
                .andExpect(status().is3xxRedirection());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> specCaptor = ArgumentCaptor.forClass(Map.class);
        verify(scheduleService).register(
                eq(JobType.LOG_JOB),
                specCaptor.capture(),
                eq("agent-002"),
                eq("0 0/10 * * * ?"));

        org.assertj.core.api.Assertions.assertThat(specCaptor.getValue())
                .containsEntry("log_path", "/var/log/app/error.log")
                .containsEntry("pattern", "ERROR|FATAL")
                .containsEntry("encoding", "UTF-8");
    }
}
