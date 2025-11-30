package tn.esprithub.server.project.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.project.dto.TaskCreateDto;
import tn.esprithub.server.project.dto.TaskDto;
import tn.esprithub.server.project.dto.TaskUpdateDto;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.service.TaskService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<List<TaskDto>> createTasks(@RequestBody TaskCreateDto dto) {
        return ResponseEntity.ok(taskService.createTasks(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDto> updateTask(@PathVariable UUID id, @RequestBody TaskUpdateDto dto) {
        return ResponseEntity.ok(taskService.updateTask(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> getTaskById(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @GetMapping
    public ResponseEntity<List<TaskDto>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @GetMapping("/by-class/{classeId}")
    public ResponseEntity<List<TaskDto>> getTasksByClasseId(@PathVariable UUID classeId) {
        return ResponseEntity.ok(taskService.getTasksByClasseId(classeId));
    }

    @GetMapping("/by-project/{projectId}")
    public ResponseEntity<List<TaskDto>> getTasksByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(taskService.getTasksByProjectId(projectId));
    }
}
