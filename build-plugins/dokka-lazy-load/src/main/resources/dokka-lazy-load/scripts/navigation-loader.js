/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Portions (scroll persistence, resetTocState, preventScrollBySpaceKey, initLogoClickListener, current-page
 * matching, and the readyState bootstrap) are adapted from Dokka's navigation-loader.js, Copyright 2014-2025
 * JetBrains s.r.o., licensed under the Apache 2.0 license.
 */

// LAZY-LOAD NAVIGATION LOADER (aws.sdk.kotlin.gradle.dokka.lazyload.DokkaLazyLoadPlugin)
//
// Replaces Dokka's default scripts/navigation-loader.js. Instead of fetching one monolithic navigation.html for
// the whole corpus (roughly 200 MB for the AWS SDK for Kotlin) and innerHTML-ing it into every page's DOM, this
// renders a tiny shell (the module list from <root>/nav.json) and fetches a node's own nav.json — holding only its
// DIRECT children — the first time the node is expanded. So the browser only holds the branches opened so far.
//
// Data model (emitted by the plugin's NavigationSplitter post-action from Dokka's own navigation.html):
//   <root>/nav.json                      -> { navId, children: [ moduleRow, ... ] }
//   <module>/nav.json                    -> that module's packages
//   <module>/<pkg>/nav.json              -> that package's declarations
//   ...one nav.json per expandable node, in that node's own content directory (nav-<navId>.json on collision).
// Each row: { navId, label, pageId, textHtml, href|null, iconHtml|null, hasChildren, navFile|null }, with
// href/navFile stored RELATIVE TO THE nav.json's OWN DIRECTORY. This loader composes them against each node's base
// dir + pathToRoot.
//
// Global sentinel so a page (or a person in devtools) can tell the lazy loader replaced the stock one.
const LAZY_NAV_LOADER = true;

(function () {
    const TOC_CONTAINER_ID = 'sideMenu';
    const TOC_SCROLL_CONTAINER_ID = 'leftColumn';
    const TOC_PART_CLASS = 'toc--part';
    const TOC_PART_HIDDEN_CLASS = 'toc--part_hidden';
    const TOC_ROW_CLASS = 'toc--row';
    const TOC_LINK_CLASS = 'toc--link';
    const TOC_BUTTON_CLASS = 'toc--button';
    const SIDE_MENU_CLASS = 'sideMenu';
    const TOC_STATE_KEY_PREFIX = 'TOC_STATE::';
    const NAV_FILE = 'nav.json';

    // `safeSessionStorage` is a global from scripts/safe-local-storage_blocking.js, which Dokka loads before this
    // script on every page. Reuse it — do NOT redeclare it (a second top-level `const safeSessionStorage` throws
    // "Identifier already declared", aborting the whole script and leaving the sidebar unrendered).
    const storage = (typeof safeSessionStorage !== 'undefined')
        ? safeSessionStorage
        : { getItem: () => null, setItem: () => {}, removeItem: () => {}, getKeys: () => [] };

    // navId -> Promise<childrenRendered>. A node's nav.json is fetched once on success, even under rapid clicks;
    // a failed fetch is retried on the next expand.
    const loadedNodes = new Map();

    // ---- URL helpers ---------------------------------------------------------------------------------------

    // pathToRoot (a Dokka global, e.g. "../../") takes us from the current page back to the docs root. A node's
    // href/navFile is relative to the NODE's dir, so each rendered node records its own root-relative dir and its
    // children resolve against it regardless of how deep they were fetched.

    // Join a root-relative base dir with a value relative to that dir, normalizing "../" and "./" segments. A
    // trailing "/" on `rel` (a directory link) is kept.
    function joinRelative(baseDir, rel) {
        const segments = [];
        const push = (parts) => parts.forEach((p) => {
            if (p === '' || p === '.') return;
            if (p === '..') { if (segments.length) segments.pop(); return; }
            segments.push(p);
        });
        push(baseDir.split('/'));
        push(rel.split('/'));
        const joined = segments.join('/');
        return rel.endsWith('/') && joined !== '' ? `${joined}/` : joined;
    }

    // Browser-usable URL (from the current page) for a root-relative path.
    function urlFromRoot(rootRelativePath) {
        return `${pathToRoot}${rootRelativePath}`;
    }

    // An href that is already a full URL or site-absolute, so it must not be joined onto a node dir.
    function isAbsoluteHref(href) {
        return /^[A-Za-z][A-Za-z0-9+.-]*:/.test(href) || href.startsWith('/');
    }

    // The URL to put on a row's link, or null when the row should not be a link at all.
    function linkUrl(baseDir, href) {
        if (!href) return null;
        if (/^\s*javascript:/i.test(href)) return null;
        return isAbsoluteHref(href) ? href : urlFromRoot(joinRelative(baseDir, href));
    }

    function safeDecode(path) {
        try {
            return path.split('/').map((s) => decodeURIComponent(s)).join('/');
        } catch {
            return path;
        }
    }

    // ---- Rendering -----------------------------------------------------------------------------------------

    // Build one toc--part element for a row. `baseDir` is the row's PARENT dir (root-relative): the dir of the
    // nav.json this row came from. The row's own dir = joinRelative(baseDir, dirOf(href)). `nestingLevel`
    // is the row's depth (top-level modules = 0), written to data-nesting-level so Dokka's stock ui-kit CSS
    // applies the per-level left indent, expand-button width, and link margin of the default sidebar.
    function buildTocPart(row, baseDir, nestingLevel) {
        const part = document.createElement('div');
        part.className = TOC_PART_CLASS;
        part.id = row.navId;
        part.classList.add(TOC_PART_HIDDEN_CLASS);
        part.dataset.nestingLevel = String(nestingLevel);
        if (row.pageId) part.setAttribute('pageId', row.pageId);

        // The node's own content dir (decoded, to compare with the page URL), used to choose which branch to
        // auto-expand. A node without a relative link has no dir of its own; it inherits its parent's and is
        // marked so the descent never picks it by dir.
        const ownDir = row.href && !isAbsoluteHref(row.href);
        part.dataset.navDir = safeDecode(joinRelative(baseDir, ownDir ? dirOf(row.href) : ''));
        if (!ownDir) part.dataset.noDir = 'true';
        if (row.navFile) {
            // Where this node's children live, and the base their relative hrefs resolve against.
            part.dataset.navFile = joinRelative(baseDir, row.navFile);
            part.dataset.childDir = joinRelative(baseDir, dirOf(row.navFile));
        }

        const rowEl = document.createElement('div');
        rowEl.className = TOC_ROW_CLASS;

        const linkHref = linkUrl(baseDir, row.href);
        // Dokka renders a node whose link it could not resolve as plain text; mirror that with a span.
        const link = document.createElement(linkHref ? 'a' : 'span');
        link.className = TOC_LINK_CLASS;
        if (linkHref) {
            link.setAttribute('href', linkHref);
            link.addEventListener('keydown', preventScrollBySpaceKey);
        }
        if (row.iconHtml) {
            const grid = document.createElement('span');
            grid.className = 'toc--link-grid';
            grid.innerHTML = `${row.iconHtml}${row.textHtml}`;
            link.appendChild(grid);
        } else {
            link.innerHTML = row.textHtml;
        }

        if (row.hasChildren) {
            const button = document.createElement('button');
            button.className = TOC_BUTTON_CLASS;
            button.setAttribute('aria-expanded', 'false');
            button.setAttribute('aria-label', row.label || link.textContent.trim() || row.navId);
            button.addEventListener('click', () => toggleNode(part));
            rowEl.appendChild(button);
        }
        rowEl.appendChild(link);

        // Dokka's stock NavigationPage serializer emits a keyboard-only "Skip to content" anchor after the link
        // for every node that has children, and the stock ui-kit CSS keys off it
        // (`.toc--part_hidden > .toc--row > .toc--button ~ .toc--skip-link { display:none }`). Reproduce it for
        // expandable rows so layout and keyboard behavior match the default sidebar.
        if (row.hasChildren) {
            const skipLink = document.createElement('a');
            skipLink.className = 'toc--skip-link';
            skipLink.setAttribute('href', '#main');
            skipLink.textContent = 'Skip to content';
            skipLink.addEventListener('keydown', preventScrollBySpaceKey);
            rowEl.appendChild(skipLink);
        }

        part.appendChild(rowEl);
        return part;
    }

    function dirOf(path) {
        const clean = path.split('#')[0].split('?')[0];
        const slash = clean.lastIndexOf('/');
        return slash < 0 ? '' : clean.substring(0, slash);
    }

    // Fetch a nav.json (root-relative path) and return its parsed object, or null on any failure/404.
    function fetchNav(rootRelativePath) {
        return fetch(urlFromRoot(rootRelativePath))
            .then((r) => (r.ok ? r.json() : null))
            .then((data) => (data && Array.isArray(data.children) ? data : null))
            .catch(() => null);
    }

    // Render `children` into `container`, each row's base dir = `baseDir`. Rows are built off-document and attached
    // in one append. `container` is either the shell wrapper (rows are level 0) or a parent toc--part (parent + 1).
    function renderChildren(container, children, baseDir) {
        const parentLevel = container.dataset && container.dataset.nestingLevel !== undefined
            ? Number(container.dataset.nestingLevel)
            : -1; // shell container has no level; its direct children are level 0
        const childLevel = parentLevel + 1;
        const fragment = document.createDocumentFragment();
        children.forEach((row) => {
            if (document.getElementById(row.navId)) return; // already present (e.g. from a prior expand)
            fragment.appendChild(buildTocPart(row, baseDir, childLevel));
        });
        container.appendChild(fragment);
    }

    // ---- Expand / collapse ---------------------------------------------------------------------------------

    function toggleNode(part) {
        if (part.dataset.loading === 'true') {
            // A second click while the children are still loading means "never mind": don't open on arrival.
            part.dataset.cancelExpand = part.dataset.cancelExpand === 'true' ? 'false' : 'true';
            return;
        }
        if (part.classList.contains(TOC_PART_HIDDEN_CLASS)) {
            expandNode(part);
        } else {
            collapseNode(part);
        }
    }

    // Expand a node: ensure its children are loaded (lazy fetch on first open), then reveal. Resolves true if the
    // node was opened, false if its children could not be loaded or the user cancelled while they loaded.
    function expandNode(part) {
        const button = ownButton(part);
        part.dataset.loading = 'true';
        part.dataset.cancelExpand = 'false';
        if (button) button.setAttribute('aria-busy', 'true');
        return ensureChildrenLoaded(part).then((ok) => {
            const cancelled = part.dataset.cancelExpand === 'true';
            delete part.dataset.loading;
            delete part.dataset.cancelExpand;
            if (button) button.removeAttribute('aria-busy');
            if (!ok || cancelled) return false;
            part.classList.remove(TOC_PART_HIDDEN_CLASS);
            if (button) button.setAttribute('aria-expanded', 'true');
            storage.setItem(`${TOC_STATE_KEY_PREFIX}${part.id}`, 'true');
            return true;
        });
    }

    function collapseNode(part) {
        part.classList.add(TOC_PART_HIDDEN_CLASS);
        const button = ownButton(part);
        if (button) button.setAttribute('aria-expanded', 'false');
        storage.setItem(`${TOC_STATE_KEY_PREFIX}${part.id}`, 'false');
    }

    // Fetch and render a node's direct children once; resolves true on success. A leaf resolves true immediately.
    // A failed fetch or render is not cached, so the next expand retries instead of showing an empty open row.
    function ensureChildrenLoaded(part) {
        if (loadedNodes.has(part.id)) return loadedNodes.get(part.id);
        const navFile = part.dataset.navFile;
        if (!navFile) return Promise.resolve(true);
        const baseDir = part.dataset.childDir || part.dataset.navDir || '';
        const promise = fetchNav(navFile).then((data) => {
            if (!data) {
                loadedNodes.delete(part.id);
                return false;
            }
            try {
                renderChildren(part, data.children, baseDir);
                return true;
            } catch (e) {
                console.error(e);
                loadedNodes.delete(part.id);
                return false;
            }
        });
        loadedNodes.set(part.id, promise);
        return promise;
    }

    function ownButton(part) {
        const row = part.querySelector(`:scope > .${TOC_ROW_CLASS}`);
        return row ? row.querySelector(`.${TOC_BUTTON_CLASS}`) : null;
    }

    // ---- Persistence + auto-expand -------------------------------------------------------------------------

    // Re-open every node the user had expanded last time. Because expansion is lazy, we fetch each stored node's
    // children as we go. We iterate until no new stored-expanded node can be opened (deeper nodes only exist in
    // the DOM after their parent loaded).
    function restoreExpandedState() {
        const expandedIds = storage.getKeys()
            .filter((k) => k.startsWith(TOC_STATE_KEY_PREFIX) && storage.getItem(k) === 'true')
            .map((k) => k.substring(TOC_STATE_KEY_PREFIX.length));
        const wanted = new Set(expandedIds);

        function step() {
            const present = [...wanted].map((id) => document.getElementById(id)).filter(Boolean);
            // Each id is handled once, as soon as it exists: a node already open is done, and a node the user closed
            // while an earlier level was still loading is not reopened.
            present.forEach((el) => wanted.delete(el.id));
            const openable = present.filter((el) => el.classList.contains(TOC_PART_HIDDEN_CLASS)
                && storage.getItem(`${TOC_STATE_KEY_PREFIX}${el.id}`) === 'true');
            if (openable.length === 0) return Promise.resolve();
            return Promise.all(openable.map((el) => expandNode(el))).then(step);
        }
        return step();
    }

    // Auto-expand the path from the modules down to the page currently being viewed, so the tree opens to where
    // the user is, then mark the active node and open its ancestors.
    //
    // Only loaded nodes exist in the DOM, so we first fetch the branch that holds the page: expand the module
    // whose dir is a prefix of the current page's dir, then at each level descend into the child whose dir is a
    // prefix of it, until we run out of expandable nodes.
    function autoExpandToCurrentPage() {
        const pageDir = currentPageDir();
        if (pageDir === null) return Promise.resolve();

        const childParts = (el) => [...el.children].filter((c) => c.classList && c.classList.contains(TOC_PART_CLASS));
        const onPath = (c, parentDir) => c.dataset.navFile && c.dataset.noDir !== 'true'
            && isPrefixDir(c.dataset.navDir, pageDir) && c.dataset.navDir !== parentDir;

        const startModule = childParts(sideMenuRoot()).find((m) => onPath(m, null));
        if (!startModule) return Promise.resolve(markActiveNode());

        function descend(part) {
            return expandNode(part).then((ok) => {
                // Stop if the fetch failed or the user collapsed this branch while it was loading.
                if (!ok || part.parentElement.closest(`.${TOC_PART_HIDDEN_CLASS}`)) return undefined;
                const next = childParts(part).find((c) => onPath(c, part.dataset.navDir));
                return next ? descend(next) : undefined;
            });
        }
        return descend(startModule).then(markActiveNode);
    }

    // Mark the active node the way the stock loader does: the first part whose pageId contains the content's
    // pageIds, trimming the pageIds one '/' segment at a time until a part matches. So a member page (which has
    // no nav node of its own) marks its enclosing declaration. Like stock, the match and its ancestors are opened.
    function markActiveNode() {
        const currentPageId = document.getElementById('content')?.getAttribute('pageIds');
        if (!currentPageId) return undefined;
        const parts = [...sideMenuRoot().querySelectorAll(`.${TOC_PART_CLASS}`)];
        let prefix = currentPageId;
        while (prefix !== '') {
            const match = parts.find((p) => p.getAttribute('pageId')?.includes(prefix));
            if (match) {
                match.dataset.active = 'true';
                return revealAncestors(match).then(() => (
                    match.dataset.navFile && match.classList.contains(TOC_PART_HIDDEN_CLASS) ? expandNode(match) : true
                ));
            }
            prefix = prefix.substring(0, prefix.lastIndexOf('/'));
        }
        return undefined;
    }

    // Open every collapsed toc--part above `part`, outermost first. Ancestors are already loaded (their children
    // are in the DOM), so this never fetches.
    function revealAncestors(part) {
        const ancestors = [];
        for (let p = part.parentElement?.closest(`.${TOC_PART_CLASS}`); p; p = p.parentElement?.closest(`.${TOC_PART_CLASS}`)) {
            ancestors.unshift(p);
        }
        return ancestors.reduce(
            (chain, p) => chain.then(() => (p.classList.contains(TOC_PART_HIDDEN_CLASS) ? expandNode(p) : true)),
            Promise.resolve(true),
        );
    }

    // Is `dir` an ancestor-or-equal directory of `target` (both root-relative, '/'-joined)?
    function isPrefixDir(dir, target) {
        if (dir === '') return true;
        return target === dir || target.startsWith(`${dir}/`);
    }

    // The current page's directory relative to the docs root, decoded. pathToRoot is the current page's climb to
    // root, so the last N directory segments of the URL path (N = depth of pathToRoot) are the page's dir.
    function currentPageDir() {
        try {
            const pathname = new URL(window.location.href).pathname;
            const segments = pathname.split('/').filter(Boolean).map((s) => decodeURIComponent(s));
            // Drop the trailing page file (index.html or foo.html), unless the URL is a directory (".../s3/").
            const fileless = pathname.endsWith('/') ? segments : segments.slice(0, -1);
            const up = (pathToRoot.match(/\.\.\//g) || []).length;
            return up === 0 ? '' : fileless.slice(fileless.length - up).join('/');
        } catch {
            return null;
        }
    }

    function preventScrollBySpaceKey(event) {
        if (event.key === ' ') {
            event.preventDefault();
            event.stopPropagation();
        }
    }

    // ---- Scroll persistence (same behavior as the stock loader) --------------------------------------------

    function saveScrollTop() {
        const container = document.getElementById(TOC_SCROLL_CONTAINER_ID);
        if (container) storage.setItem(`${TOC_STATE_KEY_PREFIX}SCROLL_TOP`, `${container.scrollTop}`);
    }

    function restoreScrollTop() {
        const container = document.getElementById(TOC_SCROLL_CONTAINER_ID);
        if (container) {
            const stored = storage.getItem(`${TOC_STATE_KEY_PREFIX}SCROLL_TOP`);
            if (stored) container.scrollTop = Number(stored);
        }
    }

    function initScrollListener() {
        const container = document.getElementById(TOC_SCROLL_CONTAINER_ID);
        if (container) container.addEventListener('scroll', saveScrollTop);
    }

    function resetTocState() {
        storage.getKeys().forEach((key) => {
            if (key.startsWith(TOC_STATE_KEY_PREFIX)) storage.removeItem(key);
        });
    }

    function initLogoClickListener() {
        const logo = document.querySelector('.library-name--link');
        if (logo) logo.addEventListener('click', resetTocState);
    }

    // ---- Bootstrap -----------------------------------------------------------------------------------------

    // The element holding the module rows. Multi-module Dokka nests them as `#sideMenu > div.sideMenu > .toc--part`,
    // and custom scripts may read `#sideMenu.children[0]`, so this loader always adds the wrapper.
    function sideMenuRoot() {
        const container = document.getElementById(TOC_CONTAINER_ID);
        return container.querySelector(`:scope > .${SIDE_MENU_CLASS}`) || container;
    }

    function displayNav() {
        const container = document.getElementById(TOC_CONTAINER_ID);
        if (!container) return;
        // The shell nav.json lives at the docs root; its rows (modules) are relative to root (baseDir = '').
        fetchNav(NAV_FILE).then((shell) => {
            if (!shell) return undefined;
            const wrapper = document.createElement('div');
            wrapper.className = SIDE_MENU_CLASS;
            renderChildren(wrapper, shell.children, '');
            container.appendChild(wrapper); // attach fully built, so no script sees an empty wrapper
            // Open the path to the current page and restore scroll right away, then re-apply any user-persisted
            // expansions (which may need several fetch rounds) and restore scroll once more.
            return autoExpandToCurrentPage()
                .catch((e) => console.error(e))
                .then(restoreScrollTop)
                .then(restoreExpandedState)
                .catch((e) => console.error(e))
                .finally(restoreScrollTop);
        }).catch((e) => console.error(e));
    }

    if (document.readyState === 'loading') {
        window.addEventListener('DOMContentLoaded', () => {
            displayNav();
            initScrollListener();
            initLogoClickListener();
        });
    } else {
        displayNav();
        initScrollListener();
        initLogoClickListener();
    }
})();
