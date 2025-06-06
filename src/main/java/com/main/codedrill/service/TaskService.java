package com.main.codedrill.service;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
import com.main.codedrill.repository.TaskAttemptRepository;
import com.main.codedrill.repository.TaskRepository;
import com.main.codedrill.repository.UserTaskCompletionRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserTaskCompletionRepository userTaskCompletionRepository;
    private final TaskAttemptRepository taskAttemptRepository;

    @Autowired
    public TaskService(TaskRepository taskRepository,
                       UserTaskCompletionRepository userTaskCompletionRepository,
                       TaskAttemptRepository taskAttemptRepository) {
        this.taskRepository = taskRepository;
        this.userTaskCompletionRepository = userTaskCompletionRepository;
        this.taskAttemptRepository = taskAttemptRepository;
    }


    public List<Task> getAllTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Task> getTasksByTag(String tag) {
        if (tag == null || tag.isEmpty() || tag.equalsIgnoreCase("all")) {
            return getAllTasks();
        }
        return taskRepository.findByTagsContainingOrderByCreatedAtDesc(tag);
    }

    public Task getTaskById(Long id) {
        return taskRepository.findById(id).orElse(null);
    }

    public List<Task> getTasksByUser(User user) {
        return taskRepository.findByCreatedByOrderByCreatedAtDesc(user);
    }

    public Task createTask(Task task, User user) {
        task.setId(null);
        task.setCreatedBy(user);
        return taskRepository.save(task);
    }

    public Task updateTask(Task task, User user) {
        Optional<Task> existingTask = taskRepository.findById(task.getId());

        if (existingTask.isPresent()) {
            Task taskToUpdate = existingTask.get();

            if (user.isAdmin() ||
                    (taskToUpdate.getCreatedBy() != null && taskToUpdate.getCreatedBy().equals(user))) {

                taskToUpdate.setTitle(task.getTitle());
                taskToUpdate.setDescription(task.getDescription());
                taskToUpdate.setTags(task.getTags());
                taskToUpdate.setContent(task.getContent());
                taskToUpdate.setSolution(task.getSolution());
                taskToUpdate.setExpectedOutput(task.getExpectedOutput());
                taskToUpdate.setJunitTests(task.getJunitTests());

                return taskRepository.save(taskToUpdate);
            }
        }

        return null;
    }

    @Transactional
    public boolean deleteTask(Long id, User user) {
        Optional<Task> task = taskRepository.findById(id);

        if (task.isPresent()) {
            Task taskToDelete = task.get();

            if (user.isAdmin() || (taskToDelete.getCreatedBy() != null && taskToDelete.getCreatedBy().equals(user))) {

                taskToDelete.getTags().clear();
                taskRepository.save(taskToDelete);
                taskAttemptRepository.deleteByTask(taskToDelete);
                userTaskCompletionRepository.deleteByTask(taskToDelete);
                taskRepository.delete(taskToDelete);
                return true;
            }
        }

        return false;
    }

    @Transactional
    public List<String> getAllTags() {
        return getAllTasks().stream()
                .flatMap(task -> task.getTags().stream())
                .distinct()
                .collect(Collectors.toList());
    }
}

