package com.main.codedrill.service;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
import com.main.codedrill.model.UserTaskCompletion;
import com.main.codedrill.repository.UserTaskCompletionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskCompletionService {

    private final UserTaskCompletionRepository completionRepository;

    @Autowired
    public TaskCompletionService(UserTaskCompletionRepository completionRepository) {
        this.completionRepository = completionRepository;
    }

    /**
     * Mark a task as completed by a user
     */
    public UserTaskCompletion markTaskAsCompleted(User user, Task task) {
        if (user == null || task == null) {
            return null;
        }

        if (completionRepository.existsByUserAndTask(user, task)) {
            return completionRepository.findByUserAndTask(user, task).orElse(null);
        }

        UserTaskCompletion completion = new UserTaskCompletion(user, task);
        return completionRepository.save(completion);
    }

    /**
     * Get all completed task IDs for a user
     */
    public List<Long> getCompletedTaskIds(User user) {
        if (user == null) {
            return List.of();
        }
        return completionRepository.findTaskIdsByUser(user);
    }

    /**
     * Check if a task is completed by a user
     */
    public boolean isTaskCompletedByUser(User user, Task task) {
        if (user == null || task == null) {
            return false;
        }
        return completionRepository.existsByUserAndTask(user, task);
    }

    /**
     * Get all completed tasks for a user
     */
    public List<UserTaskCompletion> getCompletedTasks(User user) {
        if (user == null) {
            return List.of();
        }
        return completionRepository.findByUser(user);
    }
} 