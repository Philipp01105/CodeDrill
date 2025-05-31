/**
 * Analytics Tracker
 * 
 * This script tracks user interactions with the website and sends analytics data to the server.
 * It captures events like page views, task views, login/logout, and task attempts.
 */

document.addEventListener('DOMContentLoaded', function() {
    // Track login on page load if user is authenticated
    trackLogin();

    // Add event listener for page unload to track logout
    window.addEventListener('beforeunload', function() {
        trackLogout();
    });

    // Add event listeners for tracking task views on specific interactions
    setupTaskViewTracking();
});

/**
 * Set up event listeners for tracking task views on interactions
 */
function setupTaskViewTracking() {
    // Track when a user clicks on the train button
    document.querySelectorAll('.train-btn').forEach(button => {
        button.addEventListener('click', function() {
            const taskId = this.getAttribute('data-task-id');
            if (taskId) {
                trackSpecificTaskView(taskId);
            }
        });
    });

    // Track when a user opens a task modal
    document.querySelectorAll('.view-task').forEach(element => {
        element.addEventListener('click', function() {
            // Find the parent task card to get the task ID
            const taskCard = this.closest('.task-card');
            if (taskCard) {
                const taskId = taskCard.getAttribute('data-task-id');
                if (taskId) {
                    trackSpecificTaskView(taskId);
                }
            }
        });
    });

    // Track when a user clicks train button in modal
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
    // Only track if user is authenticated (check for a user-specific element)
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
    // Only track if user is authenticated
    if (document.getElementById('user-menu') || document.querySelector('[data-user-authenticated]')) {
        // Use sendBeacon for more reliable tracking during page unload
        if (navigator.sendBeacon) {
            navigator.sendBeacon('/analytics/track/logout');
        } else {
            // Fallback to fetch with keepalive
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
 * Track task view on page load
 * NOTE: This function is now just a utility function and is not called automatically
 */
function trackTaskView() {
    // Check if we're on a task page by looking for task-specific elements
    const taskIdElement = document.querySelector('[data-task-id]');
    if (taskIdElement) {
        const taskId = taskIdElement.getAttribute('data-task-id');
        trackSpecificTaskView(taskId);
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
    // Add task ID to the task container
    const taskContainer = document.querySelector('.task-container');
    if (taskContainer) {
        taskContainer.setAttribute('data-task-id', taskId);
    } else {
        // If no specific container, add to body
        document.body.setAttribute('data-task-id', taskId);
    }
}

/**
 * Track code submission attempt
 * This function should be called when a user submits code for a task
 * 
 * @param {string} taskId - The ID of the task
 * @param {boolean} successful - Whether the attempt was successful
 * @param {string} errorMessage - Error message if the attempt failed
 * @param {string} code - The code submitted by the user
 */
function trackTaskAttempt(taskId, successful, errorMessage = '', code = '') {
    fetch(`/analytics/track/attempt/${taskId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            successful: successful,
            errorMessage: errorMessage,
            code: code
        })
    }).catch(error => {
        console.error('Error tracking task attempt:', error);
    });
}
