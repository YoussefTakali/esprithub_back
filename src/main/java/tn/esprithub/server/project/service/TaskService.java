package tn.esprithub.server.project.service;

import tn.esprithub.server.project.dto.TaskCreateDto;
import tn.esprithub.server.project.dto.TaskUpdateDto;
import tn.esprithub.server.project.dto.TaskDto;
import java.util.List;
import java.util.UUID;

public interface TaskService {
    List<TaskDto> createTasks(TaskCreateDto dto);
    TaskDto updateTask(UUID id, TaskUpdateDto dto);
    void deleteTask(UUID id);
    TaskDto getTaskById(UUID id);
    List<TaskDto> getAllTasks();
    List<TaskDto> getTasksByClasseId(UUID classeId);
    List<TaskDto> getTasksByProjectId(UUID projectId);
}
