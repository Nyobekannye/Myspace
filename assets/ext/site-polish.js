(function () {
    'use strict';

    // Legacy ChannaTheBrand badge ("Container: Default [30s HD • Off-Peak …]") — purely cosmetic status text from
    // the old script, re-written by it every second. We hide it (CSS) and render our own pill instead.
    const LEGACY_BADGE_ID = 'channa-tab-session-badge';
    const SESSION_BADGE_ID = 'sesi-maxmode-pill';
    const SESSION_BADGE_TEXT = 'MAX MODE active';
    const MODE_LABEL_TEXT = '30s (MAX MODE)';
    const LEGACY_MODE_PATTERN = /^\s*(\d+)\s*s\s*\(\s*bypassed\s*\)\s*$/i;
    const ACTIVE_MODE_PATTERN = /^\s*(\d+)\s*s\s*\(\s*(?:kartar|introvert|whempy|sesi|max)\s+mode\s*\)\s*$/i;
    const INLINE_LEGACY_MODE_PATTERN = /(\d+)\s*s\s*\(\s*bypassed\s*\)/gi;
    const RELEVANT_MODE_TEXT = /bypassed|(?:kartar|introvert|whempy|sesi|max)\s+mode/i;
    const DOWNLOAD_LABEL_TEXT = 'Fetch & Download done';
    const LEGACY_DOWNLOAD_PATTERN = /^\s*(?:.{0,6}\s*)?fetch\s*\d+\s*s\s*hd\s*video\s*$/iu;
    const ACTIVE_DOWNLOAD_PATTERN = /^\s*fetch\s*&\s*download\s*done\s*$/i;
    const COMPLETED_DOWNLOAD_PATTERN = /^\s*(?:✓|✅)?\s*downloaded!?\s*$/iu;
    const RELEVANT_DOWNLOAD_TEXT = /fetch|downloaded/i;

    // Floating pill (top-center, above Dola's page): two live toggles instead of the old decorative
    // "MAX MODE active" switch. State lives in chrome.storage.local (same keys as the popup / enforcer).
    const TOGGLES = [
        { key: 'singleClip', cls: 'single', label: '1×30s', title: 'Paksa 1 video × 30 detik (satu klip utuh)' },
        { key: 'animeRef',   cls: 'anime',  label: 'Animasi', title: 'Referensi = karakter animasi, bukan wajah asli' }
    ];
    const toggleState = { singleClip: true, animeRef: true, chatMode: false };
    let toggleStateLoaded = false;
    function storage() { try { return globalThis.chrome && chrome.storage && chrome.storage.local; } catch (e) { return null; } }
    function loadToggleState(cb) {
        const st = storage();
        if (!st) { toggleStateLoaded = true; cb && cb(); return; }
        try {
            st.get(['singleClip', 'animeRef', 'dolaMode'], (res) => {
                try { void chrome.runtime.lastError; } catch (e) {}
                toggleState.singleClip = !res || res.singleClip !== false;
                toggleState.animeRef = !res || res.animeRef !== false;
                toggleState.chatMode = !!res && res.dolaMode === 'chat';
                toggleStateLoaded = true;
                cb && cb();
            });
        } catch (e) { toggleStateLoaded = true; cb && cb(); }
    }
    function saveToggle(key, on) {
        toggleState[key] = on;
        const st = storage();
        const patch = key === 'chatMode' ? { dolaMode: on ? 'chat' : 'video' } : { [key]: on };
        try { st && st.set(patch, () => { try { void chrome.runtime.lastError; } catch (e) {} }); } catch (e) {}
        // enforcer listens for this too (in case storage events don't reach the MAIN world)
        try { window.postMessage(Object.assign({ type: 'WHEMPY_SINGLE_CLIP' }, patch), '*'); } catch (e) {}
    }
    try {
        const st = storage();
        if (st && chrome.storage.onChanged) chrome.storage.onChanged.addListener((changes, area) => {
            if (area && area !== 'local') return;
            let dirty = false;
            for (const t of TOGGLES) if (changes && changes[t.key]) { toggleState[t.key] = changes[t.key].newValue !== false; dirty = true; }
            if (changes && changes.dolaMode) { toggleState.chatMode = changes.dolaMode.newValue === 'chat'; dirty = true; }
            if (dirty) renderToggleState(document.getElementById(SESSION_BADGE_ID));
        });
    } catch (e) {}
    function renderToggleState(badge) {
        if (!badge) return;
        const chat = toggleState.chatMode === true;
        badge.classList.toggle('chat-mode', chat);
        const modeBtn = badge.querySelector('.ids-mode');
        if (modeBtn) { modeBtn.textContent = chat ? '💬' : '🎬'; modeBtn.setAttribute('title', chat ? 'Mode: Chat biasa (MAX MODE dijeda) — ketuk untuk Buat video' : 'Mode: Buat video (MAX MODE aktif) — ketuk untuk Chat biasa'); modeBtn.setAttribute('aria-checked', chat ? 'true' : 'false'); }
        for (const t of TOGGLES) {
            const row = badge.querySelector('.ids-toggle.' + t.cls);
            if (!row) continue;
            const on = !chat && toggleState[t.key] !== false;
            row.classList.toggle('on', on);
            row.classList.toggle('disabled', chat);
            row.setAttribute('aria-checked', on ? 'true' : 'false');
            row.setAttribute('aria-disabled', chat ? 'true' : 'false');
        }
    }

    function polishSessionBadge() {
        const legacy = document.getElementById(LEGACY_BADGE_ID);
        if (legacy && legacy.style.display !== 'none') { legacy.style.setProperty('display', 'none', 'important'); legacy.setAttribute('aria-hidden', 'true'); }
        if (!document.body) return;
        let badge = document.getElementById(SESSION_BADGE_ID);
        if (!badge) {
            badge = document.createElement('div');
            badge.id = SESSION_BADGE_ID;
            document.body.appendChild(badge);
        } else if (badge.parentElement !== document.body) document.body.appendChild(badge);   // SPA re-render moved it
        badge.classList.add('studio-relay-session-badge');
        badge.setAttribute('aria-label', 'Sesi MAX MODE — 1×30s & Referensi animasi');
        badge.setAttribute('title', 'Sesi MAX MODE by Whempy & Dhon');
        badge.setAttribute('role', 'group');
        badge.removeAttribute('tabindex');
        if (badge.dataset.maxModeRendered !== 'true' || !badge.querySelector('.ids-toggle')) {
            badge.textContent = '';
            // mode button (💬 chat biasa / 🎬 buat video) — leftmost
            const modeBtn = document.createElement('span');
            modeBtn.className = 'ids-mode';
            modeBtn.setAttribute('role', 'switch');
            modeBtn.setAttribute('tabindex', '0');
            const flipMode = (event) => {
                event.preventDefault(); event.stopPropagation();
                saveToggle('chatMode', !(toggleState.chatMode === true));
                renderToggleState(badge);
            };
            modeBtn.addEventListener('click', flipMode);
            modeBtn.addEventListener('keydown', (event) => { if (event.key === 'Enter' || event.key === ' ') flipMode(event); });
            badge.appendChild(modeBtn);
            for (const t of TOGGLES) {
                const row = document.createElement('span');
                row.className = 'ids-toggle ' + t.cls;
                row.setAttribute('role', 'switch');
                row.setAttribute('tabindex', '0');
                row.setAttribute('title', t.title);
                const led = document.createElement('span'); led.className = 'ids-led'; led.setAttribute('aria-hidden', 'true');
                const label = document.createElement('span'); label.className = 'ids-label'; label.textContent = t.label;
                const sw = document.createElement('span'); sw.className = 'ids-switch'; sw.setAttribute('aria-hidden', 'true');
                row.append(led, label, sw);
                const flip = (event) => {
                    event.preventDefault(); event.stopPropagation();
                    if (toggleState.chatMode === true) return;   // paused in chat mode
                    saveToggle(t.key, !(toggleState[t.key] !== false));
                    renderToggleState(badge);
                };
                row.addEventListener('click', flip);
                row.addEventListener('keydown', (event) => { if (event.key === 'Enter' || event.key === ' ') flip(event); });
                badge.appendChild(row);
            }
            badge.dataset.maxModeRendered = 'true';
            badge.dataset.studioRelayWhatsappBound = 'true';   // legacy flag: no WhatsApp click on the pill anymore
            if (!toggleStateLoaded) loadToggleState(() => renderToggleState(badge));
        }
        renderToggleState(badge);
    }

    function labelForDuration(duration) {
        return MODE_LABEL_TEXT.replace(/^30/, String(duration));
    }

    function findExactModeHost(start, pattern) {
        let current = start;
        for (let depth = 0; current && depth < 6; depth += 1) {
            const match = (current.textContent || '').match(pattern);
            if (match) return { element: current, duration: match[1] };
            current = current.parentElement;
        }
        return null;
    }

    function applyModeLabel(element, duration) {
        if (!(element instanceof Element)) return;
        const next = labelForDuration(duration);
        if (element.textContent.trim() !== next) element.textContent = next;
        element.classList.add('studio-relay-mode-pill');
        element.setAttribute('aria-label', next);
        element.setAttribute('title', next);
    }

    function polishModeTextNode(textNode) {
        if (!(textNode instanceof Text)) return;
        const parent = textNode.parentElement;
        if (!parent || parent.closest('script, style, textarea, input, [contenteditable="true"]')) return;

        const nearbyText = parent.textContent || '';
        if (!RELEVANT_MODE_TEXT.test(textNode.nodeValue || '') && !RELEVANT_MODE_TEXT.test(nearbyText)) return;

        const legacyHost = findExactModeHost(parent, LEGACY_MODE_PATTERN);
        if (legacyHost) {
            applyModeLabel(legacyHost.element, legacyHost.duration);
            return;
        }

        const activeHost = findExactModeHost(parent, ACTIVE_MODE_PATTERN);
        if (activeHost) {
            applyModeLabel(activeHost.element, activeHost.duration);
            return;
        }

        const source = textNode.nodeValue || '';
        let detectedDuration = null;
        const normalized = source.replace(INLINE_LEGACY_MODE_PATTERN, (_, duration) => {
            detectedDuration = duration;
            return labelForDuration(duration);
        });
        if (!detectedDuration) return;

        if (normalized !== source) textNode.nodeValue = normalized;
        const normalizedHost = findExactModeHost(parent, ACTIVE_MODE_PATTERN);
        applyModeLabel(normalizedHost ? normalizedHost.element : parent, detectedDuration);
    }

    function polishModeLabels(root) {
        if (root instanceof Text) {
            polishModeTextNode(root);
            return;
        }
        if (!(root instanceof Element) && root !== document) return;
        if (root instanceof Element && !RELEVANT_MODE_TEXT.test(root.textContent || '')) return;

        const scope = root === document ? document.documentElement : root;
        if (!scope) return;
        const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT);
        const textNodes = [];
        while (walker.nextNode()) textNodes.push(walker.currentNode);
        textNodes.forEach(polishModeTextNode);
    }

    function findExactDownloadHost(start, pattern) {
        let current = start;
        for (let depth = 0; current && depth < 6; depth += 1) {
            if (pattern.test(current.textContent || '')) return current;
            current = current.parentElement;
        }
        return null;
    }

    function applyDownloadLabel(element) {
        if (!(element instanceof Element)) return;
        if (element.textContent.trim() !== DOWNLOAD_LABEL_TEXT) {
            element.textContent = DOWNLOAD_LABEL_TEXT;
        }
        element.classList.add('studio-relay-download-status');
        element.setAttribute('aria-label', DOWNLOAD_LABEL_TEXT);
        element.setAttribute('title', DOWNLOAD_LABEL_TEXT);
    }

    function polishDownloadTextNode(textNode) {
        if (!(textNode instanceof Text)) return;
        const parent = textNode.parentElement;
        if (!parent || parent.closest('script, style, textarea, input, [contenteditable="true"]')) return;

        const nearbyText = parent.textContent || '';
        if (!RELEVANT_DOWNLOAD_TEXT.test(textNode.nodeValue || '') && !RELEVANT_DOWNLOAD_TEXT.test(nearbyText)) return;

        const themedHost = parent.closest('.studio-relay-download-status');
        if (themedHost && COMPLETED_DOWNLOAD_PATTERN.test(themedHost.textContent || '')) {
            applyDownloadLabel(themedHost);
            return;
        }

        const legacyHost = findExactDownloadHost(parent, LEGACY_DOWNLOAD_PATTERN);
        if (legacyHost) {
            applyDownloadLabel(legacyHost);
            return;
        }

        const activeHost = findExactDownloadHost(parent, ACTIVE_DOWNLOAD_PATTERN);
        if (activeHost) applyDownloadLabel(activeHost);
    }

    function polishDownloadLabels(root) {
        if (root instanceof Text) {
            polishDownloadTextNode(root);
            return;
        }
        if (!(root instanceof Element) && root !== document) return;
        if (root instanceof Element && !RELEVANT_DOWNLOAD_TEXT.test(root.textContent || '')) return;

        const scope = root === document ? document.documentElement : root;
        if (!scope) return;
        const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT);
        const textNodes = [];
        while (walker.nextNode()) textNodes.push(walker.currentNode);
        textNodes.forEach(polishDownloadTextNode);
    }

    function polishPage(root) {
        polishSessionBadge();
        polishModeLabels(root || document);
        polishDownloadLabels(root || document);
        polishPromptDockButtons(root || document);
    }

    function start() {
        polishPage(document);
        if (!document.documentElement) return;

        // Batch mutations and process them at idle time: Dola streams chat text and re-renders large
        // subtrees during load; walking each one synchronously made the page feel frozen.
        const ric = window.requestIdleCallback ? (fn) => window.requestIdleCallback(fn, { timeout: 1000 }) : (fn) => setTimeout(fn, 60);
        const pendingText = new Set();
        const pendingNodes = new Set();
        let scheduled = false, badgeDirty = false;
        const flush = () => {
            scheduled = false;
            if (badgeDirty) { badgeDirty = false; polishSessionBadge(); }
            const texts = Array.from(pendingText); pendingText.clear();
            const nodes = Array.from(pendingNodes); pendingNodes.clear();
            const budget = Date.now() + 12;   // ms per idle slice; the rest is re-queued
            let i = 0;
            for (; i < texts.length && Date.now() < budget; i++) { polishModeTextNode(texts[i]); polishDownloadTextNode(texts[i]); }
            for (; i - texts.length < nodes.length && Date.now() < budget; i++) {
                const node = nodes[i - texts.length];
                if (!node.isConnected) continue;
                polishModeLabels(node); polishDownloadLabels(node); polishPromptDockButtons(node);
            }
            if (i < texts.length + nodes.length) {
                for (let k = i; k < texts.length; k++) pendingText.add(texts[k]);
                for (let k = Math.max(0, i - texts.length); k < nodes.length; k++) pendingNodes.add(nodes[k]);
                schedule();
            }
        };
        const schedule = () => { if (scheduled) return; scheduled = true; ric(flush); };
        new MutationObserver((mutations) => {
            if (document.visibilityState === 'hidden') return;
            for (const mutation of mutations) {
                if (mutation.type === 'characterData') { pendingText.add(mutation.target); continue; }
                if (mutation.target && mutation.target.id === SESSION_BADGE_ID) continue;   // our own pill
                for (const node of mutation.addedNodes) {
                    if (node.nodeType === 1 && (node.id === SESSION_BADGE_ID || node.id === LEGACY_BADGE_ID)) { badgeDirty = true; continue; }
                    if (node.nodeType === 1 || node.nodeType === 3) pendingNodes.add(node);
                }
            }
            if (pendingText.size || pendingNodes.size || badgeDirty) schedule();
        }).observe(document.documentElement, {
            childList: true,
            characterData: true,
            subtree: true
        });
        // pill may be (re)created by the legacy script at any time
        setInterval(() => { if (document.visibilityState !== 'hidden') polishSessionBadge(); }, 2000);
    }

    function triggerFullClick(element) {
        if (!element || !(element instanceof Element)) return false;
        try {
            element.focus();
            const opts = { bubbles: true, cancelable: true, view: window };
            element.dispatchEvent(new PointerEvent('pointerdown', opts));
            element.dispatchEvent(new MouseEvent('mousedown', opts));
            element.dispatchEvent(new PointerEvent('pointerup', opts));
            element.dispatchEvent(new MouseEvent('mouseup', opts));
            element.dispatchEvent(new MouseEvent('click', opts));
            if (typeof element.click === 'function') {
                element.click();
            }
            return true;
        } catch (err) {
            console.error('[Whempy] Click error:', err);
            return false;
        }
    }

    function clickCreateVideoButton() {
        console.log('[Whempy] Searching for Create Video button on page...');

        const selectors = [
            '#channa-create-btn',
            '.studio-relay-create-btn',
            '#create-video-btn',
            '.create-video-btn',
            '#btn-create-video',
            'button[aria-label*="Create Video" i]',
            'button[title*="Create Video" i]',
            'button[aria-label*="Create video" i]',
            'button[title*="Create video" i]',
            'button[aria-label*="Generate" i]',
            'button[title*="Generate" i]',
            'button[type="submit"]',
            'form button[type="submit"]',
            'form button'
        ];

        for (const sel of selectors) {
            const btn = document.querySelector(sel);
            if (btn && btn.offsetWidth > 0 && btn.offsetHeight > 0) {
                console.log('[Whempy] Found button by selector:', sel, btn);
                return triggerFullClick(btn);
            }
        }

        const candidates = Array.from(document.querySelectorAll('button, div[role="button"], a[role="button"], span[role="button"], input[type="button"], input[type="submit"]'));
        for (const candidate of candidates) {
            const txt = (candidate.textContent || candidate.value || '').trim();
            if (/create\s*video|generate\s*video|create|generate|hit\s*create|submit|send/i.test(txt)) {
                console.log('[Whempy] Found button by text:', txt, candidate);
                return triggerFullClick(candidate);
            }
        }

        const textareas = document.querySelectorAll('textarea, div[contenteditable="true"], input[type="text"]');
        for (const ta of textareas) {
            const parent = ta.closest('form, div, section');
            if (parent) {
                const btn = parent.querySelector('button, div[role="button"], svg');
                if (btn) {
                    const clickable = btn.closest('button, div[role="button"]') || btn;
                    console.log('[Whempy] Found prompt box button:', clickable);
                    return triggerFullClick(clickable);
                }
            }
        }

        console.warn('[Whempy] No Create Video button could be resolved automatically.');
        return false;
    }

    /* In-Page Interactive Click Syncing System */
    let isClickSyncArmed = false;
    let syncToastElement = null;
    let clickSyncCaptureHandler = null;

    function showSyncToast(message, duration) {
        if (!syncToastElement) {
            syncToastElement = document.createElement('div');
            syncToastElement.id = 'studio-relay-sync-toast';
            syncToastElement.style.cssText = `
                position: fixed !important;
                top: 16px !important;
                left: 50% !important;
                transform: translateX(-50%) !important;
                z-index: 2147483647 !important;
                padding: 9px 16px !important;
                border: 1px solid rgba(129, 140, 248, 0.45) !important;
                border-radius: 999px !important;
                background: rgba(17, 17, 19, 0.94) !important;
                color: #c7d2fe !important;
                font-family: Inter, ui-sans-serif, sans-serif !important;
                font-size: 11px !important;
                font-weight: 650 !important;
                box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5), 0 0 12px rgba(99, 102, 241, 0.3) !important;
                pointer-events: none !important;
                transition: opacity 200ms ease, transform 200ms ease !important;
            `;
            (document.body || document.documentElement).appendChild(syncToastElement);
        }
        syncToastElement.textContent = message;
        syncToastElement.style.opacity = '1';
        if (duration) {
            setTimeout(() => {
                if (syncToastElement) syncToastElement.style.opacity = '0';
            }, duration);
        }
    }

    function buildUniqueCssPath(el) {
        if (!el || !(el instanceof Element)) return '';
        if (el.id) return `#${CSS.escape(el.id)}`;
        const path = [];
        let current = el;
        while (current && current.nodeType === Node.ELEMENT_NODE && current !== document.body && current !== document.documentElement) {
            let selector = current.tagName.toLowerCase();
            if (current.className && typeof current.className === 'string') {
                const classes = current.className.split(/\s+/).filter(c => c && !c.startsWith('studio-relay-') && !c.includes(':')).map(c => `.${CSS.escape(c)}`).join('');
                if (classes) selector += classes;
            }
            const parent = current.parentElement;
            if (parent) {
                const siblings = Array.from(parent.children).filter(c => c.tagName === current.tagName);
                if (siblings.length > 1) {
                    const index = siblings.indexOf(current) + 1;
                    selector += `:nth-of-type(${index})`;
                }
            }
            path.unshift(selector);
            current = current.parentElement;
            if (path.length >= 5) break;
        }
        return path.join(' > ');
    }

    function handleArmClickSync() {
        if (clickSyncCaptureHandler) {
            window.removeEventListener('click', clickSyncCaptureHandler, true);
            clickSyncCaptureHandler = null;
        }
        isClickSyncArmed = true;
        document.dispatchEvent(new CustomEvent('studio-relay:click-sync-state', {detail: {armed: true}}));
        showSyncToast('🎯 Click Sync Armed! Click ANY button on this page to mirror across ALL open Dola tabs.', 5000);

        const captureClick = (event) => {
            if (!isClickSyncArmed) return;

            if (event.target && event.target.closest && event.target.closest('#studio-relay-sync-toast, #channa-tab-session-badge, #sesi-maxmode-pill, #studio-relay-page-overlays')) {
                return;
            }

            isClickSyncArmed = false;
            document.dispatchEvent(new CustomEvent('studio-relay:click-sync-state', {detail: {armed: false}}));
            const targetEl = event.target.closest('button, div[role="button"], a[role="button"], input[type="submit"], input[type="button"], a, svg') || event.target;

            const selector = buildUniqueCssPath(targetEl);
            const text = (targetEl.textContent || targetEl.value || '').trim().slice(0, 100);
            const ariaLabel = targetEl.getAttribute('aria-label') || '';
            const title = targetEl.getAttribute('title') || '';
            const tag = targetEl.tagName ? targetEl.tagName.toLowerCase() : '';
            const xRatio = window.innerWidth > 0 ? event.clientX / window.innerWidth : 0.5;
            const yRatio = window.innerHeight > 0 ? event.clientY / window.innerHeight : 0.5;

            const targetData = { selector, text, ariaLabel, title, tag, xRatio, yRatio };

            console.log('[Whempy] Captured target for multi-tab sync:', targetData);

            if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
                chrome.runtime.sendMessage({ action: 'broadcast_mirrored_click', targetData }).catch(() => {});
            }

            showSyncToast('⚡ Click Mirrored across ALL Open Dola Tabs!', 2500);

            window.removeEventListener('click', captureClick, true);
            clickSyncCaptureHandler = null;
        };

        clickSyncCaptureHandler = captureClick;
        window.addEventListener('click', captureClick, true);
    }

    document.addEventListener('studio-relay:arm-click-sync', handleArmClickSync);

    function executeMirroredClick(targetData) {
        if (!targetData) return;
        console.log('[Whempy] Executing mirrored click:', targetData);

        let targetEl = null;

        if (targetData.selector) {
            try { targetEl = document.querySelector(targetData.selector); } catch (e) {}
        }

        if (!targetEl && targetData.text) {
            const candidates = Array.from(document.querySelectorAll('button, div[role="button"], a[role="button"], input[type="submit"], input[type="button"]'));
            for (const c of candidates) {
                const txt = (c.textContent || c.value || '').trim();
                if (txt && txt.toLowerCase() === targetData.text.toLowerCase()) {
                    targetEl = c;
                    break;
                }
            }
        }

        if (!targetEl && (targetData.ariaLabel || targetData.title)) {
            const attr = targetData.ariaLabel ? `[aria-label="${CSS.escape(targetData.ariaLabel)}"]` : `[title="${CSS.escape(targetData.title)}"]`;
            try { targetEl = document.querySelector(attr); } catch (e) {}
        }

        if (!targetEl && targetData.xRatio !== undefined && targetData.yRatio !== undefined) {
            const x = targetData.xRatio * window.innerWidth;
            const y = targetData.yRatio * window.innerHeight;
            const pointEl = document.elementFromPoint(x, y);
            if (pointEl) {
                targetEl = pointEl.closest('button, div[role="button"], a[role="button"], input, a') || pointEl;
            }
        }

        if (targetEl) {
            triggerFullClick(targetEl);
            showSyncToast('⚡ Click Mirrored from active tab!', 1800);
        } else {
            console.warn('[Whempy] Could not resolve mirrored target element');
        }
    }

    function pasteTextIntoDolaInput(text) {
        if (!text) return false;
        const target = document.querySelector('textarea, div[contenteditable="true"], input[type="text"]');
        if (!target) return false;

        try {
            target.focus();
            if (target.tagName.toLowerCase() === 'textarea' || target.tagName.toLowerCase() === 'input') {
                target.value = text;
            } else {
                target.textContent = text;
                target.innerText = text;
            }
            const opts = { bubbles: true, cancelable: true };
            target.dispatchEvent(new Event('input', opts));
            target.dispatchEvent(new Event('change', opts));
            target.dispatchEvent(new KeyboardEvent('keydown', opts));
            target.dispatchEvent(new KeyboardEvent('keyup', opts));
            return true;
        } catch (e) {
            return false;
        }
    }

    function polishPromptDockButtons(root) {
        const scope = (root instanceof Element || root === document) ? root : document;
        const buttons = scope.querySelectorAll('button');
        buttons.forEach((btn) => {
            const txt = (btn.textContent || '').trim();
            if (/next\s*prompt|→\s*next/i.test(txt) && !btn.dataset.pasteAllBound) {
                btn.dataset.pasteAllBound = 'true';
                btn.textContent = '⚡ Paste All Tabs';
                btn.title = 'Paste queued prompts sequentially across ALL open Dola tabs';
                btn.setAttribute('aria-label', 'Paste All Tabs');

                btn.addEventListener('click', (event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    event.stopImmediatePropagation();

                    if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
                        chrome.runtime.sendMessage({ action: 'paste_all_tabs_sequentially' }, (res) => {
                            if (res && res.success) {
                                showSyncToast(`⚡ Pasted Prompts across ${res.tabCount} Dola Tab(s)!`, 2500);
                            } else {
                                showSyncToast(res?.reason || '⚡ Pasted across all open tabs!', 2000);
                            }
                        });
                    }
                }, true);
            }
        });
    }

    if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.onMessage) {
        chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
            if (!request) return;
            if (request.action === 'hit_create_video') {
                const success = clickCreateVideoButton();
                sendResponse({ success });
                return true;
            } else if (request.action === 'arm_click_sync') {
                handleArmClickSync();
                sendResponse({ success: true });
                return true;
            } else if (request.action === 'execute_mirrored_click') {
                executeMirroredClick(request.targetData);
                sendResponse({ success: true });
                return true;
            } else if (request.action === 'paste_specific_prompt') {
                const success = pasteTextIntoDolaInput(request.text);
                if (success) {
                    showSyncToast(`⚡ Prompt Pasted! (Tab #${request.tabIndex}/${request.totalTabs})`, 2000);
                }
                sendResponse({ success });
                return true;
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start, { once: true });
    } else {
        start();
    }
})();
