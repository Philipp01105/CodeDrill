// Dark mode functionality for CodeDrill
// This script handles toggling between light and dark modes and persists the user's preference

// Check for saved theme preference or use the system preference
function getThemePreference() {
    // Check if theme is stored in localStorage
    const storedTheme = localStorage.getItem('theme');

    if (storedTheme) {
        return storedTheme;
    }

    // If no stored preference, check system preference
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

// Apply the current theme to the document
function applyTheme(theme) {
    if (theme === 'dark') {
        document.documentElement.classList.add('dark');
    } else {
        document.documentElement.classList.remove('dark');
    }

    // Store the preference
    localStorage.setItem('theme', theme);

    // Update main toggle button state if it exists
    updateToggleButton('theme-toggle', theme);

    // Update mobile toggle button state if it exists
    updateToggleButton('mobile-theme-toggle', theme);
}

// Helper function to update toggle button state
function updateToggleButton(buttonId, theme) {
    const toggleButton = document.getElementById(buttonId);
    if (toggleButton) {
        toggleButton.setAttribute('aria-checked', theme === 'dark');

        // Update the icon
        const moonIcon = toggleButton.querySelector('.moon-icon');
        const sunIcon = toggleButton.querySelector('.sun-icon');

        if (moonIcon && sunIcon) {
            if (theme === 'dark') {
                moonIcon.classList.add('hidden');
                sunIcon.classList.remove('hidden');
            } else {
                moonIcon.classList.remove('hidden');
                sunIcon.classList.add('hidden');
            }
        }
    }
}

// Toggle between light and dark mode
function toggleTheme() {
    const currentTheme = document.documentElement.classList.contains('dark') ? 'dark' : 'light';
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    applyTheme(newTheme);
}

// Initialize theme when the DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    // Apply the theme
    applyTheme(getThemePreference());

    // Add event listener to main toggle button if it exists
    const toggleButton = document.getElementById('theme-toggle');
    if (toggleButton) {
        toggleButton.addEventListener('click', toggleTheme);
    }

    // Add event listener to mobile toggle button if it exists
    const mobileToggleButton = document.getElementById('mobile-theme-toggle');
    if (mobileToggleButton) {
        mobileToggleButton.addEventListener('click', toggleTheme);
    }
});
