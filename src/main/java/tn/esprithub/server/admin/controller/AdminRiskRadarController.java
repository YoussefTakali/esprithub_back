package tn.esprithub.server.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprithub.server.admin.service.DeadlineRiskRadarService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/risk-radar")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${app.cors.allowed-origins}"})
public class AdminRiskRadarController {

    private final DeadlineRiskRadarService deadlineRiskRadarService;

    @GetMapping("/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStudentRiskRadar(
            @RequestParam(defaultValue = "7") int daysAhead,
            @RequestParam(defaultValue = "3") int staleDays) {

        int safeDaysAhead = Math.max(1, Math.min(daysAhead, 30));
        int safeStaleDays = Math.max(1, Math.min(staleDays, 14));

        log.info("📊 Generating student deadline risk radar (daysAhead={}, staleDays={})", safeDaysAhead, safeStaleDays);

        Map<String, Object> payload = deadlineRiskRadarService.buildStudentRiskRadar(safeDaysAhead, safeStaleDays);
        return ResponseEntity.ok(payload);
    }
}
