package tn.esprithub.server.test.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@CrossOrigin(origins = "http://localhost:4200")
public class TestController {

    @PostMapping("/simple")
    public ResponseEntity<Map<String, Object>> simplePost(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "POST request working without security");
        response.put("receivedBody", body);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/simple")
    public ResponseEntity<Map<String, Object>> simpleGet() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "GET request working");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}
