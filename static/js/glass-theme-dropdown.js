document.addEventListener('DOMContentLoaded', function() {
    console.log('[Dropdown] Script loaded');

    // Log initial state
    const allDropdowns = document.querySelectorAll('.dropdown');
    console.log('[Dropdown] Found dropdowns:', allDropdowns.length);

    // Add event listeners for Bootstrap dropdown events
    document.addEventListener('show.bs.dropdown', function(e) {
        const dropdown = e.target;
        const toggle = dropdown.matches('.dropdown-toggle') ? dropdown : dropdown.querySelector('.dropdown-toggle');
        const menu = dropdown.matches('.nav-item') ? dropdown.querySelector('.dropdown-menu') : dropdown.nextElementSibling;

        if (!toggle || !menu) {
            console.error('[Dropdown] Missing elements:', { toggle, menu });
            return;
        }

        // Get dimensions
        const toggleRect = toggle.getBoundingClientRect();
        const menuRect = menu.getBoundingClientRect();
        const viewportWidth = window.innerWidth;

        console.log('[Dropdown] Dimensions:', {
            toggle: toggleRect,
            menu: menuRect,
            viewport: viewportWidth
        });

        // Let Bootstrap handle the positioning
        menu.classList.add('position-absolute');
        
        // Just ensure the z-index
        menu.style.zIndex = '1050';

        // Check if menu goes off screen
        if (menu.classList.contains('dropdown-menu-lg-end') || toggleRect.left + menuRect.width > viewportWidth) {
            menu.classList.add('dropdown-menu-end');
            console.log('[Dropdown] Aligned to right');
        }
    });

    // Handle window resize
    let resizeTimeout;
    window.addEventListener('resize', function() {
        clearTimeout(resizeTimeout);
        resizeTimeout = setTimeout(function() {
            const visibleDropdowns = document.querySelectorAll('.dropdown.show');
            visibleDropdowns.forEach(dropdown => {
                const menu = dropdown.querySelector('.dropdown-menu');
                if (menu) {
                    // Reset position to let Bootstrap recalculate
                    menu.style.transform = '';
                }
            });
        }, 100);
    });

    console.log('[Dropdown] Initialization complete');
});