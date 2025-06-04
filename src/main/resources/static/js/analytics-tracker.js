/**
 * Analytics Tracker
 * 
 * This script tracks user interactions with the website and sends analytics data to the server.
 * It captures events like page views, task views, login/logout, and task attempts.
 */

document.addEventListener('DOMContentLoaded', function() {
    trackLogin();

    window.addEventListener('beforeunload', function() {
        trackLogout();
    });

    setupTaskViewTracking();
});

/**
 * Set up event listeners for tracking task views on interactions
 */
function setupTaskViewTracking() {
    document.querySelectorAll('.train-btn').forEach(button => {
        button.addEventListener('click', function() {
            const taskId = this.getAttribute('data-task-id');
            if (taskId) {
                trackSpecificTaskView(taskId);
            }
        });
    });

    document.querySelectorAll('.view-task').forEach(element => {
        element.addEventListener('click', function() {
            const taskCard = this.closest('.task-card');
            if (taskCard) {
                const taskId = taskCard.getAttribute('data-task-id');
                if (taskId) {
                    trackSpecificTaskView(taskId);
                }
            }
        });
    });

    const modalTrainBtn = document.getElementById('modalTrainBtn');
    if (modalTrainBtn) {
        modalTrainBtn.addEventListener('click', function() {
            const taskId = this.getAttribute('data-task-id');
            if (taskId) {
                trackSpecificTaskView(taskId);
            }
        });
    }
}

/**
 * Track user login
 */
function trackLogin() {
    if (document.getElementById('user-menu') || document.querySelector('[data-user-authenticated]')) {
        fetch('/analytics/track/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        }).catch(error => {
            console.error('Error tracking login:', error);
        });
    }
}

/**
 * Track user logout
 */
function trackLogout() {
    if (document.getElementById('user-menu') || document.querySelector('[data-user-authenticated]')) {
        if (navigator.sendBeacon) {
            navigator.sendBeacon('/analytics/track/logout');
        } else {
            fetch('/analytics/track/logout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                keepalive: true
            }).catch(error => {
                console.error('Error tracking logout:', error);
            });
        }
    }
}

/**
 * Track a specific task view by ID
 * This is called when a user interacts with a task (opens modal or clicks train)
 *
 * @param {string} taskId - The ID of the task to track
 */
function trackSpecificTaskView(taskId) {
    if (!taskId) return;

    fetch(`/analytics/track/view/${taskId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    }).catch(error => {
        console.error('Error tracking task view:', error);
    });
}

/**
 * Add data-task-id attribute to task elements
 * This function should be called on task pages to enable tracking
 * 
 * @param {string} taskId - The ID of the current task
 */
function initTaskTracking(taskId) {
    const taskContainer = document.querySelector('.task-container');
    if (taskContainer) {
        taskContainer.setAttribute('data-task-id', taskId);
    } else {
        document.body.setAttribute('data-task-id', taskId);
    }
}

