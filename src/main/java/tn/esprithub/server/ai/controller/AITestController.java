package tn.esprithub.server.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.ai.CodeReviewService;
import tn.esprithub.server.ai.dto.CodeReviewResult;
import tn.esprithub.server.notification.CodeReviewNotificationService;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.dto.UserDto;
import tn.esprithub.server.user.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/test")
@RequiredArgsConstructor
@Slf4j
public class AITestController {

    private final CodeReviewService codeReviewService;
    private final CodeReviewNotificationService notificationService;
    private final UserService userService;

    /**
     * Test d'analyse de code simple
     */
    @PostMapping("/code-analysis")
    @PreAuthorize("permitAll()")
    public ResponseEntity<CodeReviewResult> testCodeAnalysis() {
        log.info("Testing AI code analysis");
        
        String testCode = """
            public class Calculator {
                public int add(int a, int b) {
                    return a + b;
                }
                
                public int divide(int a, int b) {
                    return a / b; // Potential division by zero
                }
                
                public void processData(String data) {
                    if (data == null) {
                        System.out.println("Data is null");
                    } else {
                        System.out.println(data);
                    }
                }
            }
            """;
        
        CodeReviewResult result = codeReviewService.analyzeCode(
            testCode, 
            "java", 
            "Test calculator class with potential issues"
        );
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test d'analyse de diff
     */
    @PostMapping("/diff-analysis")
    @PreAuthorize("permitAll()")
    public ResponseEntity<CodeReviewResult> testDiffAnalysis() {
        log.info("Testing AI diff analysis");
        
        String testDiff = """
            diff --git a/src/main/java/Calculator.java b/src/main/java/Calculator.java
            index 1234567..abcdefg 100644
            --- a/src/main/java/Calculator.java
            +++ b/src/main/java/Calculator.java
            @@ -5,6 +5,7 @@ public class Calculator {
                 public int divide(int a, int b) {
            +        if (b == 0) {
            +            throw new IllegalArgumentException("Division by zero");
            +        }
                     return a / b;
                 }
            @@ -12,6 +13,9 @@ public class Calculator {
                 public void processData(String data) {
            +        if (data == null) {
            +            throw new IllegalArgumentException("Data cannot be null");
            +        }
                     System.out.println(data);
                 }
            """;
        
        CodeReviewResult result = codeReviewService.analyzeDiff(testDiff, "java");
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test d'analyse de fichier
     */
    @PostMapping("/file-analysis")
    @PreAuthorize("permitAll()")
    public ResponseEntity<CodeReviewResult> testFileAnalysis() {
        log.info("Testing AI file analysis");
        
        String testFile = """
            package com.example;
            
            import java.util.List;
            import java.util.ArrayList;
            
            /**
             * Service class for managing users
             */
            public class UserService {
                private List<User> users;
                
                public UserService() {
                    this.users = new ArrayList<>();
                }
                
                public void addUser(User user) {
                    if (user == null) {
                        throw new IllegalArgumentException("User cannot be null");
                    }
                    users.add(user);
                }
                
                public User findUserById(String id) {
                    for (User user : users) {
                        if (user.getId().equals(id)) {
                            return user;
                        }
                    }
                    return null;
                }
                
                public List<User> getAllUsers() {
                    return new ArrayList<>(users);
                }
            }
            """;
        
        CodeReviewResult result = codeReviewService.analyzeFile(
            "UserService.java", 
            testFile, 
            "java"
        );
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test d'analyse avec notification
     */
    @PostMapping("/analysis-with-notification")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> testAnalysisWithNotification(@RequestParam List<Long> recipientIds) {
        log.info("Testing AI analysis with notification for recipients: {}", recipientIds);
        
        String testCode = """
            public class SecurityVulnerableClass {
                public String processUserInput(String input) {
                    // SQL Injection vulnerability
                    String query = "SELECT * FROM users WHERE name = '" + input + "'";
                    return executeQuery(query);
                }
                
                public void savePassword(String password) {
                    // Password stored in plain text
                    System.out.println("Password: " + password);
                }
                
                public int calculate(int a, int b) {
                    // Potential integer overflow
                    return a * b;
                }
            }
            """;
        
        try {
                    // Récupérer les destinataires
        List<UserDto> userDtos = userService.getUsersByIds(recipientIds.stream()
            .map(id -> UUID.fromString(id.toString()))
            .toList());
        // Convertir DTOs vers entités (temporaire - à améliorer)
        List<User> recipients = userDtos.stream()
            .map(dto -> {
                User user = new User();
                user.setId(dto.getId());
                user.setEmail(dto.getEmail());
                user.setFirstName(dto.getFirstName());
                user.setLastName(dto.getLastName());
                return user;
            })
            .toList();
            
            // Analyser le code
            CodeReviewResult result = codeReviewService.analyzeCode(
                testCode, 
                "java", 
                "Test class with security vulnerabilities"
            );
            
            // Envoyer la notification
            notificationService.analyzeAndNotify(
                testCode,
                "java",
                "Test class with security vulnerabilities",
                recipients,
                "test-repository",
                "SecurityVulnerableClass.java"
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Analysis completed and notification sent");
            response.put("analysisResult", result);
            response.put("recipientsCount", recipients.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during test analysis with notification", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Test de différents langages de programmation
     */
    @PostMapping("/multi-language-test")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> testMultipleLanguages(@RequestBody Map<String, Object> payload) {
        List<String> languages = (List<String>) payload.getOrDefault("languages", List.of());
        Map<String, String> codes = (Map<String, String>) payload.getOrDefault("codes", Map.of());
        List<Map<String, Object>> analysis = new java.util.ArrayList<>();

        for (String lang : languages) {
            String code = codes.getOrDefault(lang, "");
            int issues = (int) (Math.random() * 5); // Simule un nombre d'erreurs
            int score = 100 - issues * 5;           // Simule un score
            analysis.add(Map.of(
                "language", lang,
                "issues", issues,
                "score", score,
                "codeSample", code
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Analyse dynamique multi-langage");
        result.put("analysis", analysis);
        return ResponseEntity.ok(result);
    }

    /**
     * Test de performance de l'IA
     */
    @PostMapping("/performance-test")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> testPerformance(@RequestBody(required = false) Map<String, Object> payload) {
        int size = 100000;
        if (payload != null && payload.containsKey("size")) {
            size = (int) payload.get("size");
        }
        long start = System.currentTimeMillis();
        // Simule une opération coûteuse
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += i % 7;
        }
        long end = System.currentTimeMillis();
        long duration = end - start;
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Test performance dynamique");
        result.put("durationMs", duration);
        result.put("inputSize", size);
        result.put("result", sum);
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    private String generateLargeCode() {
        StringBuilder code = new StringBuilder();
        code.append("public class LargeTestClass {\n");
        
        for (int i = 1; i <= 50; i++) {
            code.append(String.format("""
                public int method%d(int param) {
                    if (param < 0) {
                        return -1;
                    }
                    return param * %d;
                }
                
                """, i, i));
        }
        
        code.append("}\n");
        return code.toString();
    }
} 