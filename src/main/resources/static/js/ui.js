(() => {
    'use strict';
    const ready = () => {
        const sidebar = document.getElementById('sidebar-wrapper');
        const nav = document.getElementById('sidebarNav');
        const toggle = document.getElementById('sidebarToggle');
        const close = document.getElementById('sidebarClose');
        const backdrop = document.getElementById('sidebarBackdrop');
        const content = document.getElementById('page-content-wrapper');
        const mobile = window.matchMedia('(max-width: 991.98px)');
        const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
        const path = window.location.pathname.replace(/\/$/, '') || '/';
        const setOpen = (open, restoreFocus = false) => {
            open = open && mobile.matches;
            document.body.classList.toggle('sidebar-open', open);
            if (toggle) toggle.setAttribute('aria-expanded', String(open));
            if (backdrop) backdrop.hidden = !open;
            if (content) content.inert = open;
            if (sidebar) sidebar.inert = mobile.matches && !open;
            if (open && close) close.focus();
            else if (restoreFocus && toggle) toggle.focus();
        };
        if (sidebar && nav) {
            setOpen(false);
            toggle?.addEventListener('click', () => setOpen(!document.body.classList.contains('sidebar-open')));
            close?.addEventListener('click', () => setOpen(false, true));
            backdrop?.addEventListener('click', () => setOpen(false, true));
            document.addEventListener('keydown', event => {
                if (!document.body.classList.contains('sidebar-open')) return;
                if (event.key === 'Escape') { setOpen(false, true); return; }
                if (event.key !== 'Tab') return;
                const focusable = [...sidebar.querySelectorAll('a[href],button:not([disabled])')].filter(el => el.getClientRects().length);
                const first = focusable[0], last = focusable[focusable.length - 1];
                if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
                else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
            });
            mobile.addEventListener('change', () => setOpen(false));
            let active = null, longest = -1;
            for (const link of sidebar.querySelectorAll('#sidebarNav a[href], .sidebar-footer a[data-active-paths]')) {
                const routes = (link.dataset.activePaths || new URL(link.href).pathname).split(' ');
                for (const route of routes) {
                    if ((path === route || path.startsWith(route + '/')) && route.length > longest) {
                        active = link; longest = route.length;
                    }
                }
            }
            if (active) {
                active.classList.add('active'); active.setAttribute('aria-current', 'page');
                const heading = document.querySelector('[data-auto-title]');
                if (heading) heading.textContent = active.textContent.trim();
            }
            const storageKey = 'petshop.menu.' + (document.body.dataset.portalRole || 'staff');
            try { nav.scrollTop = Number(sessionStorage.getItem(storageKey)) || 0; } catch (_) { /* Storage may be unavailable. */ }
            if (active && nav.contains(active)) {
                const itemTop = active.getBoundingClientRect().top - nav.getBoundingClientRect().top + nav.scrollTop;
                if (itemTop < nav.scrollTop || itemTop + active.offsetHeight > nav.scrollTop + nav.clientHeight) {
                    nav.scrollTop = Math.max(0, itemTop - nav.clientHeight / 2);
                }
            }
            const controls = document.getElementById('sidebarScrollControls');
            const up = document.getElementById('sidebarScrollUp');
            const down = document.getElementById('sidebarScrollDown');
            const updateScroll = () => {
                const overflow = nav.scrollHeight > nav.clientHeight + 2;
                if (controls) controls.hidden = !overflow;
                if (up) up.disabled = nav.scrollTop < 2;
                if (down) down.disabled = nav.scrollTop + nav.clientHeight >= nav.scrollHeight - 2;
                try { sessionStorage.setItem(storageKey, String(nav.scrollTop)); } catch (_) { /* Optional convenience only. */ }
            };
            const scroll = direction => nav.scrollBy({top: direction * Math.max(140, nav.clientHeight * .7), behavior: reduced.matches ? 'instant' : 'smooth'});
            up?.addEventListener('click', () => scroll(-1)); down?.addEventListener('click', () => scroll(1));
            nav.addEventListener('scroll', updateScroll, {passive:true});
            nav.addEventListener('click', event => { if (event.target.closest('a') && mobile.matches) setOpen(false); });
            window.addEventListener('resize', updateScroll, {passive:true});
            if (typeof ResizeObserver !== 'undefined') new ResizeObserver(updateScroll).observe(nav);
            updateScroll();
        }
        const date = document.getElementById('portalDate');
        for (const picture of document.querySelectorAll('img[data-fallback-src]')) {
            const fallback = () => {
                if (picture.dataset.fallbackApplied) return;
                picture.dataset.fallbackApplied = 'true';
                picture.src = picture.dataset.fallbackSrc;
            };
            picture.addEventListener('error', fallback, {once:true});
            if (picture.complete && !picture.naturalWidth) fallback();
        }
        for (const logo of document.querySelectorAll('img[data-brand-logo]')) {
            const hideBrokenLogo = () => { logo.hidden = true; };
            logo.addEventListener('error', hideBrokenLogo, {once:true});
            if (logo.complete && !logo.naturalWidth) hideBrokenLogo();
        }
        if (date) date.textContent = new Intl.DateTimeFormat('vi-VN', {day:'2-digit',month:'2-digit',year:'numeric',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
        const pet = (new URLSearchParams(window.location.search).get('petType') || '').trim().toUpperCase();
        for (const link of document.querySelectorAll('[data-store-nav]')) {
            if (path === '/products' || path.startsWith('/products/')) {
                const active = link.dataset.storeNav === (pet === 'DOG' || pet === 'CAT' ? pet : 'all');
                link.classList.toggle('active', active);
                if (active) link.setAttribute('aria-current', 'page');
            }
        }
        const top = document.getElementById('backToTop');
        if (top) {
            const update = () => { top.hidden = window.scrollY < 450; };
            window.addEventListener('scroll', update, {passive:true}); update();
            top.addEventListener('click', () => window.scrollTo({top:0,behavior:reduced.matches?'instant':'smooth'}));
        }
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready);
    else ready();
})();
