package com.main.codedrill.service;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
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

    @Autowired
    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Autowired
    private UserTaskCompletionRepository userTaskCompletionRepository;

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
        task.setId(null); // Ensure it's a new task
        task.setCreatedBy(user);
        return taskRepository.save(task);
    }
    
    public Task updateTask(Task task, User user) {
        Optional<Task> existingTask = taskRepository.findById(task.getId());
        
        if (existingTask.isPresent()) {
            Task taskToUpdate = existingTask.get();
            
            // Admin can update any task, moderators only their own
            if (user.isAdmin() || 
                (taskToUpdate.getCreatedBy() != null && taskToUpdate.getCreatedBy().equals(user))) {
                
                taskToUpdate.setTitle(task.getTitle());
                taskToUpdate.setDescription(task.getDescription());
                taskToUpdate.setTags(task.getTags());
                taskToUpdate.setContent(task.getContent());
                taskToUpdate.setSolution(task.getSolution());
                taskToUpdate.setExpectedOutput(task.getExpectedOutput());
                
                return taskRepository.save(taskToUpdate);
            }
        }
        
        return null; // Not found or not authorized
    }

    @Transactional
    public boolean deleteTask(Long id, User user) {
        Optional<Task> task = taskRepository.findById(id);
        
        if (task.isPresent()) {
            Task taskToDelete = task.get();
            
            // Admin can delete any task, moderators only their own
            if (user.isAdmin() || 
                (taskToDelete.getCreatedBy() != null && taskToDelete.getCreatedBy().equals(user))) {

                //userTaskCompletionRepository.deleteByTask(taskToDelete);

                taskRepository.delete(taskToDelete);
                return true;
            }
        }
        
        return false;
    }
    
    public List<String> getAllTags() {
        return getAllTasks().stream()
                .flatMap(task -> task.getTags().stream())
                .distinct()
                .collect(Collectors.toList());
    }
} 