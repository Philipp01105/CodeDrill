function getThemePreference() {
    const storedTheme = localStorage.getItem('theme');

    if (storedTheme) {
        return storedTheme;
    }

    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

// Apply the current theme to the document
function applyTheme(theme) {
    if (theme === 'dark') {
        document.documentElement.classList.add('dark');
    } else {
        document.documentElement.classList.remove('dark');
    }

    localStorage.setItem('theme', theme);

    updateToggleButton('theme-toggle', theme);

    updateToggleButton('mobile-theme-toggle', theme);
}

// Helper function to update toggle button state
function updateToggleButton(buttonId, theme) {
    const toggleButton = document.getElementById(buttonId);
    if (toggleButton) {
        toggleButton.setAttribute('aria-checked', theme === 'dark');

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
    applyTheme(getThemePreference());

    const toggleButton = document.getElementById('theme-toggle');
    if (toggleButton) {
        toggleButton.addEventListener('click', toggleTheme);
    }

    const mobileToggleButton = document.getElementById('mobile-theme-toggle');
    if (mobileToggleButton) {
        mobileToggleButton.addEventListener('click', toggleTheme);
    }
});
