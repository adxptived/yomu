/* Yomu EPUB reader runtime: pagination, text offsets, selection, highlights, footnotes, TTS marks.
 * Long strip: the chapter being read is one document; the chapters around it are fetched and inserted into it as
 * <section data-yomu-chapter="N"> blocks, so the whole book scrolls as one feed. Text offsets stay global inside the
 * DOM; everything the app sees (progress, selection, words, blocks) is translated to the chapter it belongs to. */
(function () {
  'use strict';
  var B = window.YomuBridge || { onReady: function () {}, onProgress: function () {}, onTap: function () {}, onSelection: function () {},
    onSelectionCleared: function () {}, onWord: function () {}, onLink: function () {}, onImage: function () {}, onFootnote: function () {},
    onEdge: function () {}, onChapterEdge: function () {}, onStripRange: function () {}, onLayout: function () {}, onHighlightTap: function () {},
    footnote: function () { return ''; }, log: function () {} };
  var cfg = window.__yomuConfig || {};
  var SKIP = 'data-yomu-skip';
  var BLOCK = { P: 1, H1: 1, H2: 1, H3: 1, H4: 1, H5: 1, H6: 1, LI: 1, BLOCKQUOTE: 1, DD: 1, DT: 1, TD: 1, FIGCAPTION: 1, DIV: 1, PRE: 1 };
  var se = null;
  var pageW = 1, pageH = 1;
  var fixedScale = 1, fixedLeft = 0, fixedTop = 0;
  var highlights = [];
  var drawnHighlights = [];
  var marks = {};
  var savedWords = [];
  var savedWordRe = null;
  var cache = null;
  // Page of each chapter's first character in a paged whole-book document; rebuilt after every reflow.
  var pageStarts = null;
  // Direction the book itself declares, read once before the reader sets the page direction.
  var docRtl = null;
  // Right-to-left page layout needs the columns in their own scroller: Chromium paints nothing when multicol is put
  // directly on the body in RTL (the columns fall outside the scrolled area).
  var pageScroller = null;
  var lastReport = '';
  var selectionShown = false;
  var anchorOffset = null;
  var ready = false;

  function json(o) { return JSON.stringify(o); }
  function debounce(fn, ms) { var t = null; return function () { var a = arguments; clearTimeout(t); t = setTimeout(function () { fn.apply(null, a); }, ms); }; }

  // ---------------------------------------------------------------- text model
  function skipped(n) {
    var p = n.parentNode;
    while (p && p !== document.body && p.nodeType === 1) {
      var t = p.tagName;
      if (t === 'SCRIPT' || t === 'STYLE' || t === 'NOSCRIPT' || p.hasAttribute(SKIP)) return true;
      p = p.parentNode;
    }
    return false;
  }
  function isStrip() { return !!cfg.strip && !cfg.paged && !cfg.fixed && !cfg.vertical; }
  // A document with several chapters as sections: the whole book (paged or scrolling) or the long strip.
  function isSectioned() { return !!cfg.book || isStrip(); }
  function sectionOfNode(n) {
    if (!n) return null;
    if (n.nodeType === 1 && n.hasAttribute && n.hasAttribute('data-yomu-chapter')) return n;
    var p = n.parentNode;
    while (p && p !== document.body) {
      if (p.nodeType === 1 && p.hasAttribute('data-yomu-chapter')) return p;
      p = p.parentNode;
    }
    return null;
  }
  function nodes() {
    if (cache) return cache;
    var list = [], map = new Map(), off = 0, n;
    var sections = [], byChapter = new Map(), secEl = null, sec = null;
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    while ((n = w.nextNode())) {
      if (skipped(n)) continue;
      var owner = sectionOfNode(n);
      if (owner !== secEl) {
        secEl = owner;
        sec = owner ? { el: owner, chapter: Number(owner.getAttribute('data-yomu-chapter')), start: off, len: 0 } : null;
        if (sec) { sections.push(sec); byChapter.set(sec.chapter, sec); }
      }
      var e = { node: n, start: off, len: n.nodeValue.length };
      if (sec) { e.local = off - sec.start; e.chapter = sec.chapter; }
      list.push(e); map.set(n, e); off += e.len;
      if (sec) sec.len = off - sec.start;
    }
    // A chapter may have no text at all — a full-page illustration. It still owns a chapter and its page: give it an
    // empty span at its place in the document, or the page map and "chapter N of M" silently skip it.
    var all = document.body.querySelectorAll('section[data-yomu-chapter]');
    if (all.length && sections.length !== all.length) {
      var byEl = new Map();
      for (var k = 0; k < sections.length; k++) byEl.set(sections[k].el, sections[k]);
      var ordered = [], pos = 0;
      for (var j = 0; j < all.length; j++) {
        var el = all[j], s = byEl.get(el);
        if (!s) {
          s = { el: el, chapter: Number(el.getAttribute('data-yomu-chapter')), start: pos, len: 0 };
          byChapter.set(s.chapter, s);
        }
        ordered.push(s);
        pos = s.start + s.len;
      }
      sections = ordered;
    }
    cache = { list: list, map: map, total: off, sections: sections, byChapter: byChapter };
    return cache;
  }
  function invalidate() { cache = null; pageStarts = null; }
  // A layout change (font, margins, columns, screen, a late picture) does not touch the text: the node map stays valid.
  function invalidatePages() { pageStarts = null; }
  function posToOffset(c, o) {
    var m = nodes();
    if (c.nodeType === 3) {
      var e = m.map.get(c);
      if (e) return e.start + Math.min(o, e.len);
    }
    var ref = c.nodeType === 3 ? c : (c.childNodes[o] || null);
    for (var i = 0; i < m.list.length; i++) {
      var n = m.list[i].node;
      if (ref) {
        var p = ref.compareDocumentPosition(n);
        if (n === ref || (p & Node.DOCUMENT_POSITION_FOLLOWING) || (p & Node.DOCUMENT_POSITION_CONTAINED_BY)) return m.list[i].start;
      } else {
        var q = c.compareDocumentPosition(n);
        if ((q & Node.DOCUMENT_POSITION_FOLLOWING) && !(q & Node.DOCUMENT_POSITION_CONTAINED_BY)) return m.list[i].start;
      }
    }
    return m.total;
  }
  function offsetToPos(off) {
    var l = nodes().list;
    if (!l.length) return { node: document.body, offset: 0 };
    var lo = 0, hi = l.length - 1;
    while (lo < hi) { var mid = (lo + hi + 1) >> 1; if (l[mid].start <= off) lo = mid; else hi = mid - 1; }
    var e = l[lo];
    return { node: e.node, offset: Math.max(0, Math.min(e.len, off - e.start)) };
  }
  function rangeFor(start, end) {
    var a = offsetToPos(start), b = offsetToPos(Math.max(start, end));
    var r = document.createRange();
    try { r.setStart(a.node, a.offset); r.setEnd(b.node, b.offset); } catch (e) { r.selectNodeContents(document.body); r.collapse(true); }
    return r;
  }
  function textBetween(start, end) { return rangeFor(start, end).toString(); }
  function fullText() { var l = nodes().list, s = ''; for (var i = 0; i < l.length; i++) s += l[i].node.nodeValue; return s; }

  // ---------------------------------------------------------------- chapter sections (long strip)
  function sectionAt(off) {
    if (!isSectioned()) return null;
    var secs = nodes().sections, hit = null;
    for (var i = 0; i < secs.length; i++) { if (off >= secs[i].start) hit = secs[i]; else break; }
    return hit || (secs.length ? secs[0] : null);
  }
  function currentSection() { return isSectioned() ? sectionAt(firstVisibleOffset()) : null; }
  function currentChapter() { var s = currentSection(); return s ? s.chapter : (cfg.chapter != null ? cfg.chapter : 0); }
  function baseOf(chapter) { var s = nodes().byChapter.get(chapter); return s ? s.start : null; }
  function toGlobal(chapter, off) {
    if (!isSectioned()) return off;
    var b = baseOf(chapter);
    return b == null ? null : b + off;
  }
  function toLocal(off) {
    var s = sectionAt(off);
    if (!s) return { chapter: cfg.chapter != null ? cfg.chapter : 0, offset: off };
    return { chapter: s.chapter, offset: off - s.start };
  }
  function sectionText(sec) {
    if (!sec) return fullText();
    return textBetween(sec.start, sec.start + sec.len);
  }
  /** Pages of a scrolling chapter, only for the page number and its estimate: 1500 characters is roughly one screen. */
  function estimatePages(chars) { return Math.max(1, Math.round(chars / 1500)); }

  // ---------------------------------------------------------------- layout
  function isRtlDoc() { try { return getComputedStyle(document.body).direction === 'rtl'; } catch (e) { return false; } }
  function fixedDimensions() {
    var meta = document.querySelector('meta[name="yomu-fixed-viewport"]');
    var value = meta ? meta.getAttribute('content') || '' : '';
    var w = /(?:^|[,;\s])width\s*=\s*([0-9]+(?:\.[0-9]+)?)/i.exec(value);
    var h = /(?:^|[,;\s])height\s*=\s*([0-9]+(?:\.[0-9]+)?)/i.exec(value);
    var width = w ? Number(w[1]) : 0, height = h ? Number(h[1]) : 0;
    if (!width || !height) {
      var svg = document.querySelector('body > svg[viewBox], body > svg[viewbox]');
      var box = svg && (svg.getAttribute('viewBox') || svg.getAttribute('viewbox'));
      var v = box ? box.trim().split(/[\s,]+/).map(Number) : [];
      if (v.length === 4 && v[2] > 0 && v[3] > 0) { width = v[2]; height = v[3]; }
    }
    if (!(width >= 10 && width <= 20000 && height >= 10 && height <= 20000)) return [1024, 768];
    return [width, height];
  }
  /**
   * Paged RTL keeps its columns in their own scroll container. Chromium lays RTL multicol columns out to the left of
   * the container; on the body itself that area is outside the document's scroller and the page paints as nothing.
   */
  function ensurePageStructure(wanted) {
    var existing = document.getElementById('yomu-scroller');
    if (wanted && !existing) {
      var host = document.createElement('div');
      host.id = 'yomu-columns';
      var scroller = document.createElement('div');
      scroller.id = 'yomu-scroller';
      scroller.appendChild(host);
      var moved = [];
      for (var node = document.body.firstChild; node; node = node.nextSibling) {
        if (node.nodeType === 1 && (node.id === 'yomu-prev' || node.id === 'yomu-next')) continue;
        moved.push(node);
      }
      moved.forEach(function (n) { host.appendChild(n); });
      document.body.appendChild(scroller);
      // The decoration layers belong to the scrolled content, not to the document.
      ['yomu-hl', 'yomu-marks', 'yomu-words'].forEach(function (id) {
        var l = document.getElementById(id);
        if (l) scroller.appendChild(l);
      });
    } else if (!wanted && existing) {
      var host = document.getElementById('yomu-columns');
      if (host) {
        while (host.firstChild) document.body.insertBefore(host.firstChild, existing);
      }
      ['yomu-hl', 'yomu-marks', 'yomu-words'].forEach(function (id) {
        var l = document.getElementById(id);
        if (l) document.documentElement.appendChild(l);
      });
      existing.remove();
    }
    pageScroller = wanted ? document.getElementById('yomu-scroller') : null;
  }

  function applyLayout() {
    // The book may not say it, and the user can force it: the page direction is part of the layout, not the markup.
    if (docRtl === null) {
      try {
        docRtl = document.documentElement.dir === 'rtl' || getComputedStyle(document.body).direction === 'rtl';
      } catch (e) { docRtl = false; }
    }
    var rtl = !!(cfg.rtl || docRtl);
    try { document.documentElement.dir = rtl ? 'rtl' : 'ltr'; } catch (e) { /* ignore */ }
    ensurePageStructure(cfg.paged && !cfg.fixed && !cfg.vertical && rtl);
    se = pageScroller || document.scrollingElement || document.documentElement;
    pageW = window.innerWidth; pageH = window.innerHeight;
    var mh = cfg.marginH || 20, mv = cfg.marginV || 28;
    var st = document.getElementById('yomu-layout');
    if (!st) { st = document.createElement('style'); st.id = 'yomu-layout'; document.head.appendChild(st); }
    var css = '';
    if (cfg.fixed) {
      var dim = fixedDimensions();
      var availableH = Math.max(1, pageH - (cfg.footer ? 30 : 0));
      fixedScale = Math.min(pageW / dim[0], availableH / dim[1]);
      fixedLeft = (pageW - dim[0] * fixedScale) / 2;
      fixedTop = (availableH - dim[1] * fixedScale) / 2;
      css = 'html{position:absolute!important;left:' + fixedLeft + 'px!important;top:' + fixedTop + 'px!important;' +
        'width:' + dim[0] + 'px!important;height:' + dim[1] + 'px!important;overflow:hidden!important;' +
        'transform-origin:0 0!important;transform:scale(' + fixedScale + ')!important;}';
    } else if (cfg.vertical) {
      css = 'html{writing-mode:vertical-rl;-webkit-writing-mode:vertical-rl;height:100vh;overflow-x:auto;overflow-y:hidden;}' +
        'body{margin:0!important;padding:' + mv + 'px ' + mh + 'px!important;height:100vh;box-sizing:border-box;}';
    } else if (cfg.paged) {
      var cols = (cfg.columns || 1);
      var colW = Math.floor((pageW - 2 * mh * cols) / cols);
      // The status line (page, clock) lives in the bottom margin: keep it free of text.
      var mb = cfg.footer ? Math.max(mv, 30) : mv;
      var columnCss = 'column-width:' + colW + 'px!important;column-gap:' + (2 * mh) + 'px!important;column-fill:auto!important;overflow:visible!important;' +
        'max-width:none!important;width:auto!important;';
      var pageCss = 'img,svg,video{max-height:' + (pageH - mv - mb - 4) + 'px!important;max-width:100%!important;object-fit:contain;break-inside:avoid;}' +
        'p,li,h1,h2,h3,h4,h5,h6{orphans:2;widows:2;}' +
        // Each chapter starts on a page of its own: chapter page counts never depend on the neighbour before it.
        'section[data-yomu-chapter]{display:block;break-before:column;-webkit-column-break-before:always;}' +
        'body > section[data-yomu-chapter]:first-child,#yomu-columns > section[data-yomu-chapter]:first-child{break-before:auto;-webkit-column-break-before:auto;}';
      if (pageScroller) {
        // RTL: the scroller and the columns are elements; see ensurePageStructure.
        css = 'html,body{height:' + pageH + 'px!important;overflow:hidden!important;margin:0!important;padding:0!important;}' +
          '#yomu-scroller{position:relative;width:' + pageW + 'px;height:' + pageH + 'px;overflow-x:auto;overflow-y:hidden;direction:rtl;touch-action:none;}' +
          '#yomu-columns{position:relative;height:' + pageH + 'px!important;box-sizing:border-box!important;padding:' + mv + 'px ' + mh + 'px ' + mb + 'px ' + mh + 'px!important;' +
          columnCss + 'direction:rtl;}' + pageCss;
      } else {
        css = 'html{height:' + pageH + 'px!important;overflow:hidden!important;margin:0!important;padding:0!important;}' +
          'body{margin:0!important;padding:' + mv + 'px ' + mh + 'px ' + mb + 'px ' + mh + 'px!important;height:' + pageH + 'px!important;box-sizing:border-box!important;' +
          columnCss + '}' + pageCss;
      }
    } else {
      css = 'html{overflow-x:hidden;}body{margin:0!important;padding:' + mv + 'px ' + mh + 'px ' + (cfg.footer ? Math.max(mv, 30) : mv) + 'px ' + mh + 'px!important;box-sizing:border-box;}' +
        'img,svg,video{max-width:100%!important;height:auto;}' +
        'section[data-yomu-chapter]{display:block;}' +
        'section[data-yomu-chapter]+section[data-yomu-chapter]{margin-top:1.4em;}';
    }
    st.textContent = css;
    navButtons();
  }
  function navButtons() {
    if (cfg.paged || cfg.vertical || cfg.chapterButtons === false) { ['yomu-prev', 'yomu-next'].forEach(function (id) { var e = document.getElementById(id); if (e) e.remove(); }); return; }
    if (cfg.hasPrev && !document.getElementById('yomu-prev')) {
      var p = document.createElement('div'); p.id = 'yomu-prev'; p.className = 'yomu-nav'; p.setAttribute(SKIP, '1'); p.textContent = cfg.prevLabel || '‹';
      // In a whole-book document the arrows move a chapter (the app's edge means the end of the book there).
      p.addEventListener('click', function (e) { e.stopPropagation(); if (cfg.book) chapterStep(-1); else B.onEdge('prev'); });
      document.body.insertBefore(p, document.body.firstChild);
    }
    if (cfg.hasNext && !document.getElementById('yomu-next')) {
      var n = document.createElement('div'); n.id = 'yomu-next'; n.className = 'yomu-nav'; n.setAttribute(SKIP, '1'); n.textContent = cfg.nextLabel || '›';
      n.addEventListener('click', function (e) { e.stopPropagation(); if (cfg.book) chapterStep(1); else B.onEdge('next'); });
      document.body.appendChild(n);
    }
  }
  // A page turn always advances by the window width, whatever the column count: the body's margins live inside that
  // width, so the next column (or spread) starts exactly one window further. (The old 2-column step subtracted both
  // margins and drifted by 40–80 px per turn.)
  function pageStep() { return pageW; }
  function pageCount() {
    if (cfg.fixed || !cfg.paged || cfg.vertical) return 1;
    // The laid-out width is a multiple of the window width: one page, or one spread of two columns.
    return Math.max(1, Math.round((se.scrollWidth - 1) / pageW));
  }
  function currentPage() { return cfg.fixed ? 0 : Math.round(Math.abs(se.scrollLeft) / pageStep()); }
  function setPage(i, smooth) {
    var n = pageCount();
    var step = pageStep();
    i = Math.max(0, Math.min(n - 1, i));
    var x = i * step * (isRtlDoc() ? -1 : 1);
    var anim = cfg.reduceMotion ? 'NONE' : (cfg.pageAnimation || 'SLIDE');
    if (smooth && anim === 'FADE') {
      var el = document.documentElement;
      el.style.transition = 'opacity 110ms ease-out';
      el.style.opacity = '0';
      setTimeout(function () {
        se.scrollLeft = x;
        el.style.transition = 'opacity 150ms ease-in';
        el.style.opacity = '1';
        report();
      }, 120);
      setTimeout(report, 300);
      return;
    }
    if (smooth && anim === 'SLIDE' && Math.abs(Math.abs(se.scrollLeft) - i * step) <= step * 1.5) se.scrollTo({ left: x, behavior: 'smooth' });
    else se.scrollLeft = x;
    setTimeout(report, smooth && anim === 'SLIDE' ? 350 : 30);
  }
  function progression() {
    if (cfg.vertical) { var mx = se.scrollWidth - pageW; return mx <= 0 ? 1 : Math.min(1, Math.abs(se.scrollLeft) / mx); }
    if (cfg.paged) { var n = pageCount(); return n <= 1 ? 1 : currentPage() / (n - 1); }
    var m = se.scrollHeight - pageH; return m <= 0 ? 1 : Math.min(1, se.scrollTop / m);
  }
  /** Pages of a scrolling whole-book document: one screen is one page. */
  function scrollPageCount() { return Math.max(1, Math.round((se.scrollHeight - pageH) / pageH) + 1); }
  function currentScrollPage() { return Math.max(0, Math.min(scrollPageCount() - 1, Math.round(se.scrollTop / pageH))); }
  function indexOfSection(sec) {
    var secs = nodes().sections;
    for (var i = 0; i < secs.length; i++) if (secs[i] === sec) return i;
    return -1;
  }
  /** The first column rect of a section (a section is fragmented into one rect per column). */
  function sectionRect(sec) {
    if (sec.len > 0) return rangeFor(sec.start, sec.start + 1).getBoundingClientRect();
    var rects = sec.el.getClientRects();
    return rects.length ? rects[0] : sec.el.getBoundingClientRect();
  }
  /** Page where a chapter starts. Chapters always begin at a column boundary (`break-before: column`). */
  function pageStartsMap() {
    if (pageStarts) return pageStarts;
    var secs = nodes().sections, out = [];
    var mh = cfg.marginH || 20, rtl = isRtlDoc();
    for (var i = 0; i < secs.length; i++) {
      var page = 0;
      try {
        var rc = sectionRect(secs[i]);
        // Distance from the leading edge of the content, in window widths: a page of a spread is one width.
        // The rect is in viewport coordinates, so the scroll position turns it into a content position.
        var x = rtl ? pageW - (rc.right + se.scrollLeft) : rc.left + se.scrollLeft;
        // A chapter can start in either column of a spread: both belong to the same page.
        page = Math.max(0, Math.floor((x - mh) / pageW + 0.001));
      } catch (e) { page = 0; }
      // A chapter can never start before the one above it: rounding must not steal its pages.
      if (i > 0 && page < out[i - 1]) page = out[i - 1];
      out.push(page);
    }
    pageStarts = out;
    return out;
  }
  function sectionStartPage(sec) {
    var i = indexOfSection(sec);
    return i < 0 ? 0 : pageStartsMap()[i];
  }
  function sectionPageCount(sec) {
    var i = indexOfSection(sec);
    if (i < 0) return 1;
    var map = pageStartsMap();
    var end = i + 1 < map.length ? map[i + 1] : pageCount();
    return Math.max(1, end - map[i]);
  }
  function sectionAtPage(page) {
    var secs = nodes().sections;
    if (!secs.length) return null;
    var map = pageStartsMap(), hit = secs[0];
    for (var i = 0; i < secs.length; i++) if (map[i] <= page) hit = secs[i]; else break;
    return hit;
  }
  /**
   * The section under the eye in a scrolling document. A chapter that is only an illustration has no text for
   * [sectionAt] to find, so ask the layout itself; the first points avoid the chapter arrows in the top margin.
   */
  function sectionAtViewport() {
    if (!isSectioned()) return null;
    var top = Math.min(pageH / 3, (cfg.marginV || 28) + 8);
    var pts = [[pageW / 4, top], [pageW * 3 / 4, top], [pageW / 2, pageH / 3], [pageW / 2, pageH / 2]];
    for (var i = 0; i < pts.length; i++) {
      var el = document.elementFromPoint(pts[i][0], pts[i][1]);
      var s = el ? sectionOfNode(el) : null;
      if (s) return nodes().byChapter.get(Number(s.getAttribute('data-yomu-chapter'))) || null;
    }
    return null;
  }
  /** Page count and chapter page starts, sent to the app after every layout. */
  function layoutInfo() {
    var starts = [], pages = 0;
    if (cfg.paged && isSectioned()) {
      starts = pageStartsMap().slice();
      pages = pageCount();
    } else if (cfg.book) {
      var secs = nodes().sections;
      for (var i = 0; i < secs.length; i++) {
        starts.push(Math.max(0, Math.round((secs[i].el.getBoundingClientRect().top + se.scrollTop) / pageH)));
      }
      pages = scrollPageCount();
    } else if (cfg.paged) {
      pages = pageCount();
    }
    return { pages: pages, starts: starts };
  }
  function caretAt(x, y) {
    var r = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
    if (r && r.startContainer && (r.startContainer.nodeType === 3 || r.startContainer.childNodes.length)) return r;
    return null;
  }
  /**
   * Offset at a viewport point, but only when the caret it answers with is really there. A point in the top margin can
   * return the first line of the document (another page, far off screen); adding that to the visible range made the
   * "first visible offset" 1 on page 40.
   */
  function caretOffsetAt(x, y) {
    var r = caretAt(x, y);
    if (!r) return null;
    var rc = r.getBoundingClientRect();
    if (rc.width === 0 && rc.height === 0) return null;
    if (rc.right < -2 || rc.left > pageW + 2 || rc.bottom < -2 || rc.top > pageH + 2) return null;
    return posToOffset(r.startContainer, r.startOffset);
  }
  function firstVisibleOffset() {
    if (cfg.fixed) return 0;
    var mh = cfg.marginH || 20, mv = cfg.marginV || 28;
    // In a right-to-left page the first line starts at the top-right, not the top-left.
    // The first points are in the top margin, the next ones just below it: a chapter heading starts lower than a
    // paragraph, so the margin points alone can resolve above the first line.
    var pts = isRtlDoc()
      ? [[pageW - mh - 2, mv + 2], [pageW - mh - 20, mv + 12], [pageW - mh - 2, mv + 40], [pageW - mh - 20, mv + 60],
        [pageW / 2, mv + 20], [pageW / 2, pageH / 3], [pageW / 2, pageH / 2]]
      : [[mh + 2, mv + 2], [mh + 20, mv + 12], [mh + 2, mv + 40], [mh + 20, mv + 60],
        [pageW / 2, mv + 20], [pageW / 3, pageH / 3], [pageW / 2, pageH / 2]];
    // The top margin has no text: take the topmost real hit, not the first point that answers.
    var best = null;
    for (var i = 0; i < pts.length; i++) {
      var off = caretOffsetAt(pts[i][0], pts[i][1]);
      if (off === null) continue;
      if (best === null || off < best) best = off;
    }
    return best === null ? Math.round(progression() * nodes().total) : best;
  }
  function lastVisibleOffset() {
    if (cfg.fixed) return nodes().total;
    var mh = cfg.marginH || 20, mv = cfg.marginV || 28;
    if (cfg.footer) mv = Math.max(mv, 30);
    // The last line of a right-to-left page ends at the bottom-left.
    var pts = isRtlDoc()
      ? [[mh + 2, pageH - mv - 4], [mh + 20, pageH - mv - 16], [pageW / 2, pageH - mv - 24], [pageW / 2, pageH * 0.75]]
      : [[pageW - mh - 2, pageH - mv - 4], [pageW - mh - 20, pageH - mv - 16], [pageW / 2, pageH - mv - 24], [pageW / 2, pageH * 0.75]];
    // The bottom margin has no text either: a caret there may answer with the end of the whole book.
    var best = null;
    for (var i = 0; i < pts.length; i++) {
      var off = caretOffsetAt(pts[i][0], pts[i][1]);
      if (off === null) continue;
      if (best === null || off > best) best = off;
    }
    return best === null ? nodes().total : best;
  }
  function report() {
    if (!ready) return;
    var g = firstVisibleOffset();
    var page = currentPage();
    var o = { chapter: cfg.chapter != null ? cfg.chapter : 0, page: page, pages: pageCount(), progression: progression(), offset: g, total: nodes().total,
      bookPage: -1, bookPages: 0, bookOffset: g, bookTotal: nodes().total };
    if (isSectioned()) {
      // Paged: the measured page decides the chapter (an illustration has no text for the caret to land on). Scrolling:
      // the section under the eye, then the text under the first visible point.
      var s = cfg.paged ? sectionAtPage(page) : (sectionAtViewport() || sectionAt(g));
      if (s) {
        // Never report a position outside the chapter the page belongs to (the caret may answer from a margin).
        if (g < s.start) g = s.start;
        if (g > s.start + s.len) g = s.start + s.len;
        var local = Math.max(0, Math.min(s.len, g - s.start));
        if (cfg.paged) {
          // Exact pages of the whole document, and the page inside this chapter.
          var start = sectionStartPage(s), count = sectionPageCount(s);
          var localPage = Math.max(0, Math.min(count - 1, page - start));
          o = {
            chapter: s.chapter, page: localPage, pages: count,
            progression: count <= 1 ? 1 : localPage / (count - 1),
            offset: local, total: s.len,
            bookPage: page, bookPages: pageCount(), bookOffset: g, bookTotal: nodes().total,
          };
        } else {
          // Scrolling: page-like numbers come from the real scroll height; the chapter itself from its section.
          var pages = estimatePages(s.len);
          o = {
            chapter: s.chapter,
            page: Math.min(pages - 1, Math.floor((local / Math.max(1, s.len)) * pages)),
            pages: pages,
            progression: s.len <= 1 ? 1 : local / s.len,
            offset: local,
            total: s.len,
            bookPage: cfg.book ? currentScrollPage() : -1,
            bookPages: cfg.book ? scrollPageCount() : 0,
            bookOffset: g,
            bookTotal: nodes().total,
          };
        }
      }
    }
    var str = json(o);
    if (str !== lastReport) { lastReport = str; B.onProgress(str); }
    drawAll(g);
  }
  var reportLater = debounce(report, 160);

  function rectOfOffset(off) {
    var r = rangeFor(off, Math.min(off + 1, nodes().total));
    var rects = r.getClientRects();
    var rc = rects.length ? rects[0] : r.getBoundingClientRect();
    return rc;
  }
  function goToOffset(off, smooth, chapter) {
    if (cfg.fixed) { setTimeout(report, 30); return; }
    if (isSectioned()) {
      var g = toGlobal(chapter == null ? currentChapter() : chapter, off);
      if (g == null) { setTimeout(report, 30); return; }
      off = g;
    }
    off = Math.max(0, Math.min(off, nodes().total));
    var rc = rectOfOffset(off);
    if (cfg.vertical) { se.scrollLeft = se.scrollLeft + rc.right - pageW + (cfg.marginH || 20); }
    else if (cfg.paged) {
      var abs = Math.abs(se.scrollLeft) + (isRtlDoc() ? (pageW - rc.right) : rc.left);
      setPage(Math.floor((abs + 1) / pageStep()), smooth);
      return;
    } else { se.scrollTop = se.scrollTop + rc.top - (cfg.marginV || 28); }
    setTimeout(report, 30);
  }
  /** Go to an offset of the whole document (the translation of chapter-local offsets happens here). */
  function goToGlobal(g, smooth) {
    if (isSectioned()) {
      var loc = toLocal(g);
      goToOffset(loc.offset, smooth, loc.chapter);
    } else {
      goToOffset(g, smooth);
    }
  }
  function goToProgression(p) {
    if (cfg.fixed) { setTimeout(report, 30); return; }
    p = Math.max(0, Math.min(1, p || 0));
    if (isSectioned()) {
      // A whole-book document scrolls by the whole book; the long strip stays inside the chapter it shows.
      if (!cfg.paged && cfg.book) { se.scrollTop = p * (se.scrollHeight - pageH); setTimeout(report, 30); return; }
      var s = currentSection();
      if (!s) { setTimeout(report, 30); return; }
      goToOffset(Math.round(p * Math.max(0, s.len - 1)), false, s.chapter);
      return;
    }
    if (cfg.vertical) se.scrollLeft = -p * (se.scrollWidth - pageW);
    else if (cfg.paged) { setPage(Math.round(p * (pageCount() - 1)), false); return; }
    else se.scrollTop = p * (se.scrollHeight - pageH);
    setTimeout(report, 30);
  }
  function anchorIn(root, id) {
    var all = root.querySelectorAll ? root.querySelectorAll('[id]') : [];
    for (var i = 0; i < all.length; i++) if (all[i].id === id) return all[i];
    if (root.id === id) return root;
    return null;
  }
  function goToAnchor(id) {
    var s = currentSection();
    var el = (s ? anchorIn(s.el, id) : null) || document.getElementById(id) || document.getElementsByName(id)[0];
    if (!el) return false;
    var off = posToOffset(el, 0);
    if (isStrip()) {
      se.scrollTop = se.scrollTop + el.getBoundingClientRect().top - (cfg.marginV || 28);
      setTimeout(report, 30);
      return true;
    }
    goToGlobal(off, false);
    return true;
  }
  /** Move to a chapter of the loaded document, at a local offset, progression or anchor. Never loads a document. */
  function goToChapter(index, spec) {
    if (!isSectioned()) return false;
    var s = nodes().byChapter.get(index);
    if (!s) return false;
    spec = spec || {};
    var local = 0, hasTarget = false;
    if (spec.anchor) {
      var el = anchorIn(s.el, spec.anchor) || document.getElementById(spec.anchor);
      if (el) {
        if (cfg.paged) {
          goToGlobal(posToOffset(el, 0), false);
        } else {
          se.scrollTop = se.scrollTop + el.getBoundingClientRect().top - (cfg.marginV || 28);
          setTimeout(report, 30);
        }
        return true;
      }
    } else if (spec.offset != null) {
      local = Math.max(0, Math.min(s.len, spec.offset));
      hasTarget = true;
    } else if (spec.progression != null) {
      local = Math.round(Math.max(0, Math.min(1, spec.progression)) * Math.max(0, s.len - 1));
      hasTarget = true;
    }
    if (cfg.paged) {
      // Every chapter starts at a column boundary: its first page is measured, not guessed.
      if (hasTarget) goToGlobal(s.start + local, false); else setPage(sectionStartPage(s), true);
      return true;
    }
    // A chapter may have no text at all (a full-page illustration): its element is its position, a text offset is not.
    var rc = hasTarget && s.len > 0 ? rectOfOffset(s.start + local) : s.el.getBoundingClientRect();
    se.scrollTop = se.scrollTop + rc.top - (cfg.marginV || 28);
    setTimeout(report, 30);
    return true;
  }
  function scrollToSection(s) {
    if (!s) return;
    var rc = s.el.getBoundingClientRect();
    se.scrollTop = se.scrollTop + rc.top - (cfg.marginV || 28);
    setTimeout(report, 30);
  }
  function scrollBehavior() { return cfg.reduceMotion || cfg.pageAnimation === 'NONE' || cfg.pageAnimation === 'FADE' ? 'auto' : 'smooth'; }
  function next() {
    if (cfg.paged && !cfg.vertical) {
      var p = currentPage();
      if (p >= pageCount() - 1) B.onEdge('next'); else setPage(p + 1, true);
    } else {
      var before = cfg.vertical ? Math.abs(se.scrollLeft) : se.scrollTop;
      if (cfg.vertical) se.scrollBy({ left: -(pageW - 40), behavior: scrollBehavior() });
      else se.scrollBy({ top: pageH - 60, behavior: scrollBehavior() });
      setTimeout(function () { var after = cfg.vertical ? Math.abs(se.scrollLeft) : se.scrollTop; if (Math.abs(after - before) < 2) B.onEdge('next'); else report(); }, 400);
    }
  }
  function prev() {
    if (cfg.paged && !cfg.vertical) {
      var p = currentPage();
      if (p <= 0) B.onEdge('prev'); else setPage(p - 1, true);
    } else {
      var before = cfg.vertical ? Math.abs(se.scrollLeft) : se.scrollTop;
      if (cfg.vertical) se.scrollBy({ left: pageW - 40, behavior: scrollBehavior() });
      else se.scrollBy({ top: -(pageH - 60), behavior: scrollBehavior() });
      setTimeout(function () { var after = cfg.vertical ? Math.abs(se.scrollLeft) : se.scrollTop; if (Math.abs(after - before) < 2) B.onEdge('prev'); else report(); }, 400);
    }
  }
  /**
   * Bottom bar chapter buttons: in the strip move to the neighbouring chapter (scrolling, or fetching it into the feed).
   * A neighbour that cannot be inlined (fixed layout, image-only) is opened as its own page by the app.
   */
  function chapterStep(dir) {
    if (!isSectioned()) { B.onEdge(dir > 0 ? 'next' : 'prev'); return; }
    // The chapter the reader is looking at: the measured page when paged, the section under the eye when scrolling.
    // A text caret may sit in the neighbouring chapter when the page shows only an illustration.
    var s = cfg.paged ? sectionAtPage(currentPage()) : (sectionAtViewport() || currentSection());
    if (!s) return;
    var target = s.chapter + dir;
    if (target < 0) return;
    if (target > (cfg.chapterCount || 1) - 1) { if (dir > 0) B.onEdge('next'); return; }
    var m = nodes().byChapter;
    if (m.has(target)) {
      // Paged: the chapter's first page, measured. Scroll/strip: the chapter's first line.
      if (cfg.paged) setPage(sectionStartPage(m.get(target)), true); else scrollToSection(m.get(target));
      return;
    }
    if (!canInline(target)) { B.onChapterEdge(dir > 0); return; }
    var go = function () {
      var mm = nodes().byChapter;
      if (mm.has(target)) scrollToSection(mm.get(target));
    };
    if (dir > 0) loadNext().then(go); else loadPrev().then(go);
  }

  // ---------------------------------------------------------------- long strip loader
  var stripBusy = false;
  var stripSent = false;
  function chapterMeta(i) { return cfg.chapters ? cfg.chapters[i] : null; }
  function canInline(i) {
    var c = chapterMeta(i);
    return !!c && !c.fixed && !c.image;
  }
  function chapterUrl(i) {
    var c = chapterMeta(i);
    if (!c || !c.path) return null;
    return '/' + c.path.split('/').map(encodeURIComponent).join('/');
  }
  function isAbsoluteUrl(v) { return !v || /^(data:|blob:|[a-z][a-z0-9+.-]*:|\/)/i.test(v); }
  function absoluteUrl(v, baseUrl) { return isAbsoluteUrl(v) ? v : new URL(v, baseUrl).href; }
  function absolutize(root, baseUrl) {
    ['src', 'poster'].forEach(function (attr) {
      var els = root.querySelectorAll('[' + attr + ']');
      for (var i = 0; i < els.length; i++) els[i].setAttribute(attr, absoluteUrl(els[i].getAttribute(attr), baseUrl));
    });
    // SVG illustrations reference their files through xlink:href rather than src.
    var images = root.querySelectorAll('image,use');
    for (var k = 0; k < images.length; k++) {
      var href = images[k].getAttribute('xlink:href') || images[k].getAttribute('href');
      if (!href || href.charAt(0) === '#') continue;
      var abs = absoluteUrl(href, baseUrl);
      images[k].setAttribute('xlink:href', abs);
      images[k].setAttribute('href', abs);
    }
    var links = root.querySelectorAll('a[href]');
    for (var j = 0; j < links.length; j++) {
      var target = links[j].getAttribute('href');
      if (!target || target.charAt(0) === '#' || /^(mailto:|tel:|[a-z][a-z0-9+.-]*:)/i.test(target)) continue;
      links[j].setAttribute('href', new URL(target, baseUrl).href);
    }
    // Relative url(...) in an inline style would otherwise point at the opened chapter's folder.
    var styled = root.querySelectorAll('[style]');
    for (var s = 0; s < styled.length; s++) {
      var css = styled[s].getAttribute('style');
      if (css && css.indexOf('url(') >= 0) {
        styled[s].setAttribute('style', css.replace(/url\((['"]?)([^'")]+)\1\)/g, function (all, quote, value) {
          return 'url("' + absoluteUrl(value.trim(), baseUrl) + '")';
        }));
      }
    }
  }
  function fetchSection(i) {
    var path = chapterUrl(i);
    if (!path) return Promise.resolve(null);
    var url = new URL(path, location.href).href;
    return fetch(url + (url.indexOf('?') >= 0 ? '&' : '?') + 'v=strip-' + i)
      .then(function (r) { return r.ok ? r.text() : ''; })
      .then(function (html) {
        if (!html) return null;
        var parsed = new DOMParser().parseFromString(html, 'text/html');
        if (!parsed || !parsed.body) return null;
        absolutize(parsed.body, url);
        var sec = document.createElement('section');
        sec.setAttribute('data-yomu-chapter', String(i));
        sec.className = 'yomu-strip-chapter';
        while (parsed.body.firstChild) sec.appendChild(parsed.body.firstChild);
        // The chapter's own styles, once per sheet, so the inserted text looks like it does when opened alone.
        var heads = parsed.head.querySelectorAll('link[rel=stylesheet],style');
        for (var k = 0; k < heads.length; k++) {
          var node = heads[k];
          if (node.tagName === 'STYLE') {
            // The reader's own user styles are already in this document.
            if (node.id === 'yomu-user' || node.id === 'yomu-layout') continue;
            var st = document.createElement('style');
            st.setAttribute(SKIP, '1');
            st.textContent = node.textContent;
            document.head.appendChild(st);
          } else {
            var href = node.getAttribute('href') || '';
            if (!href || href.indexOf('__yomu/') >= 0 || href.indexOf('reader.css') >= 0) continue;
            var abs = new URL(href, url).href;
            if (!document.querySelector('link[data-yomu-strip-css="' + abs.replace(/"/g, '') + '"]')) {
              var l = document.createElement('link');
              l.rel = 'stylesheet';
              l.href = abs;
              l.setAttribute('data-yomu-strip-css', abs);
              document.head.appendChild(l);
            }
          }
        }
        return sec;
      })
      .catch(function () { return null; });
  }
  function notifyRange() {
    var secs = nodes().sections;
    if (!secs.length || !B.onStripRange) return;
    B.onStripRange(secs[0].chapter, secs[secs.length - 1].chapter);
  }
  function loadedChapters() {
    var els = document.querySelectorAll('section[data-yomu-chapter]'), out = [];
    for (var i = 0; i < els.length; i++) out.push(Number(els[i].getAttribute('data-yomu-chapter')));
    return out;
  }
  function appendSection(sec) {
    var index = sec.getAttribute('data-yomu-chapter');
    if (document.querySelector('section[data-yomu-chapter="' + index + '"]')) return;
    var all = document.querySelectorAll('section[data-yomu-chapter]');
    var last = all.length ? all[all.length - 1] : null;
    if (last) document.body.insertBefore(sec, last.nextSibling); else document.body.appendChild(sec);
    invalidate();
    drawAll();
    notifyRange();
  }
  function prependSection(sec) {
    var index = sec.getAttribute('data-yomu-chapter');
    if (document.querySelector('section[data-yomu-chapter="' + index + '"]')) return;
    var before = se.scrollHeight, top = se.scrollTop;
    var first = document.querySelector('section[data-yomu-chapter]');
    document.body.insertBefore(sec, first || document.body.firstChild);
    invalidate();
    // The content above pushes everything down: keep the reader looking at the same line.
    se.scrollTop = top + (se.scrollHeight - before);
    drawAll();
    notifyRange();
  }
  function loadNext() {
    if (!isStrip() || stripBusy) return Promise.resolve(false);
    var loaded = loadedChapters();
    if (!loaded.length) return Promise.resolve(false);
    var next = loaded[loaded.length - 1] + 1;
    if (next >= (cfg.chapterCount || 0) || !canInline(next)) return Promise.resolve(false);
    stripBusy = true;
    return fetchSection(next).then(function (sec) {
      stripBusy = false;
      if (!sec) return false;
      appendSection(sec);
      return true;
    }).catch(function () { stripBusy = false; return false; });
  }
  function loadPrev() {
    if (!isStrip() || stripBusy) return Promise.resolve(false);
    var loaded = loadedChapters();
    if (!loaded.length) return Promise.resolve(false);
    var prev = loaded[0] - 1;
    if (prev < 0 || !canInline(prev)) return Promise.resolve(false);
    stripBusy = true;
    return fetchSection(prev).then(function (sec) {
      stripBusy = false;
      if (!sec) return false;
      prependSection(sec);
      return true;
    }).catch(function () { stripBusy = false; return false; });
  }
  function underfilled() {
    var all = document.querySelectorAll('section[data-yomu-chapter]');
    if (!all.length) return false;
    return all[all.length - 1].getBoundingClientRect().bottom < pageH * 2;
  }
  function stripFill(rounds) {
    if (!isStrip()) return;
    rounds = rounds == null ? 8 : rounds;
    if (rounds <= 0) return;
    loadNext().then(function (ok) {
      if (!ok) { loadPrev(); return; }
      if (underfilled()) stripFill(rounds - 1);
    });
  }
  // A last resort for chapters whose inline copy fails: the app opens the neighbour as its own page.
  function stripManage() {
    // The whole book is already here; nothing to fetch.
    if (cfg.book || !isStrip() || stripBusy) return;
    var max = se.scrollHeight - pageH;
    if (max <= 0) { loadNext(); return; }
    var y = se.scrollTop;
    if (y > max - pageH * 1.5) loadNext();
    if (y < pageH * 1.2) loadPrev();
    if (y >= max - 2) {
      var secs = nodes().sections;
      var lastChapter = secs.length ? secs[secs.length - 1].chapter : -1;
      if (lastChapter >= (cfg.chapterCount || 1) - 1) {
        if (!stripSent) { stripSent = true; B.onEdge('next'); }
      } else if (lastChapter >= 0 && !canInline(lastChapter + 1)) {
        // The next chapter is a fixed-layout page: it cannot join the feed, so the app opens it.
        if (!stripSent) { stripSent = true; B.onChapterEdge(true); }
      }
    } else {
      stripSent = false;
    }
  }

  // ---------------------------------------------------------------- decorations
  function layer(id, z) {
    var host = pageScroller || document.documentElement;
    var l = document.getElementById(id);
    if (!l) {
      l = document.createElement('div'); l.id = id; l.setAttribute(SKIP, '1');
      l.style.cssText = 'position:absolute;left:0;top:0;width:0;height:0;pointer-events:none;z-index:' + z + ';';
    }
    if (l.parentNode !== host) host.appendChild(l);
    return l;
  }
  function drawRanges(l, items, cls) {
    // Content coordinates of the real scroller (the element one in RTL paged mode, the document otherwise).
    var sx = se ? se.scrollLeft : 0, sy = se ? se.scrollTop : 0, frag = document.createDocumentFragment();
    items.forEach(function (h) {
      var rects = rangeFor(h.start, h.end).getClientRects();
      h.rects = [];
      for (var i = 0; i < rects.length; i++) {
        var r = rects[i];
        if (r.width < 1 || r.height < 1) continue;
        h.rects.push({ l: r.left, t: r.top, r: r.right, b: r.bottom });
        var d = document.createElement('div');
        d.className = cls + ' ' + (h.style || '');
        var left = cfg.fixed ? (r.left - fixedLeft) / fixedScale : r.left + sx;
        var top = cfg.fixed ? (r.top - fixedTop) / fixedScale : r.top + sy;
        var width = cfg.fixed ? r.width / fixedScale : r.width;
        var height = cfg.fixed ? r.height / fixedScale : r.height;
        var s = 'position:absolute;left:' + left + 'px;top:' + top + 'px;width:' + width + 'px;height:' + height + 'px;';
        if (h.color) s += '--c:' + h.color + ';';
        d.style.cssText = s;
        frag.appendChild(d);
      }
      if (h.note && h.rects.length) {
        var last = h.rects[h.rects.length - 1];
        var n = document.createElement('div'); n.className = 'yomu-note-mark'; n.textContent = '✎';
        var noteLeft = cfg.fixed ? (last.r - fixedLeft) / fixedScale : last.r + sx;
        var noteTop = cfg.fixed ? (last.t - fixedTop) / fixedScale - 6 : last.t + sy - 6;
        n.style.cssText = 'position:absolute;left:' + noteLeft + 'px;top:' + noteTop + 'px;';
        frag.appendChild(n);
      }
    });
    l.innerHTML = '';
    l.appendChild(frag);
  }
  function drawAll(from, to) {
    if (!ready) return;
    var wants = highlights.length || savedWords.length || Object.keys(marks).length;
    if (wants && from == null) from = firstVisibleOffset();
    if ((highlights.length || savedWords.length) && to == null) to = lastVisibleOffset();
    // A caret in the bottom margin can answer with the end of the book. A page holds far fewer characters, so a
    // runaway "last visible" only makes the decoration filter useless.
    if (from != null && to != null && to > from + 8000) to = from + 8000;
    var items = [];
    for (var i = 0; i < highlights.length; i++) {
      var h = highlights[i];
      if (isSectioned()) {
        var g = toGlobal(h.chapter, h.start);
        if (g == null) continue;
        var ge = toGlobal(h.chapter, h.end);
        if (ge == null) ge = g + Math.max(1, (h.end || h.start) - h.start);
        items.push({ id: h.id, start: g, end: ge, color: h.color, style: h.style, note: h.note });
      } else {
        items.push(h);
      }
    }
    // Decorate only the text around the page: a whole book can hold many highlights and every page turn used to
    // recreate every one of them. A tap can only hit a decoration that is on the screen anyway.
    if (from != null && to != null) {
      var pad = 2000;
      items = items.filter(function (h) { return h.end >= from - pad && h.start <= to + pad; });
    }
    drawnHighlights = items;
    drawRanges(layer('yomu-hl', 1), items, 'yomu-hl');
    var m = [];
    Object.keys(marks).forEach(function (k) {
      var mk = marks[k];
      if (!mk) return;
      var gs = isSectioned() ? toGlobal(mk.chapter, mk.start) : mk.start;
      var ge = isSectioned() ? toGlobal(mk.chapter, mk.end) : mk.end;
      if (gs == null || ge == null) return;
      m.push({ start: gs, end: ge, style: 'yomu-mark-' + k });
    });
    drawRanges(layer('yomu-marks', 2), m, 'yomu-mark');
    drawWords(from, to);
  }
  /** The pattern of the saved words, built once: rebuilding it on every page turn was the most expensive part. */
  function wordRegex() {
    if (!savedWords.length) return null;
    if (savedWordRe) return savedWordRe;
    var esc = savedWords.map(function (w) { return w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); });
    try { savedWordRe = new RegExp('(^|[^\\p{L}\\p{M}])(' + esc.join('|') + ')(?=$|[^\\p{L}\\p{M}])', 'giu'); } catch (e) { savedWordRe = null; }
    return savedWordRe;
  }
  function drawWords(from, to) {
    var l = layer('yomu-words', 1);
    if (!savedWords.length) { l.innerHTML = ''; return; }
    var re = wordRegex();
    if (!re) return;
    var items = [], m, count = 0;
    if (from == null) from = firstVisibleOffset();
    if (to == null) to = lastVisibleOffset();
    // Only the text around the page: a whole book would be rebuilt on every page turn otherwise.
    var lo = Math.max(0, from - 4000), hi = Math.min(nodes().total, to + 4000);
    var part = textBetween(lo, hi);
    re.lastIndex = 0;
    while ((m = re.exec(part)) && count < 400) {
      var s = lo + m.index + m[1].length;
      items.push({ start: s, end: s + m[2].length, style: 'yomu-word' });
      count++;
    }
    drawRanges(l, items, 'yomu-wordmark');
  }

  // ---------------------------------------------------------------- selection
  function selectionInfo() {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || !sel.rangeCount) return null;
    var r = sel.getRangeAt(0);
    var start = posToOffset(r.startContainer, r.startOffset), end = posToOffset(r.endContainer, r.endOffset);
    var rc = r.getBoundingClientRect();
    var a = toLocal(start), b = toLocal(end);
    return {
      text: sel.toString(), chapter: a.chapter, start: a.offset,
      end: b.chapter === a.chapter ? b.offset : a.offset + (end - start),
      left: rc.left, top: rc.top, right: rc.right, bottom: rc.bottom, sentence: sentenceAround(start, end),
    };
  }
  var onSelectionChange = debounce(function () {
    var info = selectionInfo();
    if (info && info.text.trim().length) { selectionShown = true; B.onSelection(json(info)); }
    else if (selectionShown) { selectionShown = false; B.onSelectionCleared(); }
  }, 220);
  function clearSelection() { var s = window.getSelection(); if (s) s.removeAllRanges(); selectionShown = false; }
  function select(start, end) {
    var base = 0;
    var s = currentSection();
    if (isSectioned() && s) base = s.start;
    var sel = window.getSelection(); sel.removeAllRanges(); sel.addRange(rangeFor(base + start, base + end));
  }
  function blockOf(node) {
    var n = node.nodeType === 3 ? node.parentNode : node;
    while (n && n !== document.body) { if (BLOCK[n.tagName]) return n; n = n.parentNode; }
    return document.body;
  }
  function blockBounds(off) {
    var p = offsetToPos(off);
    var b = blockOf(p.node);
    var r = document.createRange(); r.selectNodeContents(b);
    return { start: posToOffset(r.startContainer, r.startOffset), end: posToOffset(r.endContainer, r.endOffset), el: b };
  }
  var SENT_END = /[.!?…。！？]+["»”’)\]]*\s*/g;
  function sentenceBounds(start, end) {
    var bb = blockBounds(start), text = textBetween(bb.start, bb.end), rel = start - bb.start, relEnd = end - bb.start;
    var s = 0, e = text.length, m;
    SENT_END.lastIndex = 0;
    while ((m = SENT_END.exec(text))) {
      var stop = m.index + m[0].length;
      if (stop <= rel) s = stop;
      else if (m.index >= relEnd) { e = m.index + m[0].replace(/\s+$/, '').length; break; }
    }
    return { start: bb.start + s, end: bb.start + e };
  }
  function sentenceAround(start, end) { var b = sentenceBounds(start, end); return textBetween(b.start, b.end).trim().slice(0, 600); }
  function expand(kind) {
    var info = selectionInfo(); if (!info) return;
    var sec = currentSection();
    var base = isSectioned() && sec ? sec.start : 0;
    var gs = base + info.start, ge = base + info.end;
    var b = kind === 'paragraph' ? blockBounds(gs) : sentenceBounds(gs, ge);
    select(b.start - base, b.end - base);
  }

  // ---------------------------------------------------------------- words
  function wordAt(x, y) {
    var r = caretAt(x, y);
    if (!r || r.startContainer.nodeType !== 3) return null;
    var node = r.startContainer, text = node.nodeValue, i = Math.min(r.startOffset, text.length - 1);
    if (i < 0) return null;
    var probe = document.createRange(); probe.setStart(node, i); probe.setEnd(node, Math.min(text.length, i + 1));
    var pr = probe.getBoundingClientRect();
    if (x < pr.left - 12 || x > pr.right + 12 || y < pr.top - 8 || y > pr.bottom + 8) return null;
    var s = i, e = i + 1, found = false;
    if (window.Intl && Intl.Segmenter) {
      try {
        var seg = new Intl.Segmenter(cfg.lang || undefined, { granularity: 'word' });
        var it = seg.segment(text)[Symbol.iterator](), step;
        while (!(step = it.next()).done) {
          var g = step.value;
          if (g.index <= i && i < g.index + g.segment.length) { if (!g.isWordLike) return null; s = g.index; e = g.index + g.segment.length; found = true; break; }
        }
      } catch (err) { found = false; }
    }
    if (!found) {
      var W = /[\p{L}\p{M}\p{N}'’\-]/u;
      if (!W.test(text[i])) return null;
      while (s > 0 && W.test(text[s - 1])) s--;
      while (e < text.length && W.test(text[e])) e++;
    }
    var base = nodes().map.get(node); if (!base) return null;
    var start = base.start + s, end = base.start + e;
    var loc = toLocal(start);
    var wr = document.createRange(); wr.setStart(node, s); wr.setEnd(node, e);
    var rc = wr.getBoundingClientRect();
    return {
      word: text.substring(s, e), chapter: loc.chapter, start: loc.offset, end: loc.offset + (e - s),
      tail: textBetween(start, Math.min(nodes().total, start + 24)),
      sentence: sentenceAround(start, end), left: rc.left, top: rc.top, right: rc.right, bottom: rc.bottom,
    };
  }

  // ---------------------------------------------------------------- touch, taps, links
  var touch = null, tapTimer = null, lastTapAt = 0;
  // Forgets the first tap of a possible double tap: a page turn or another action happened instead.
  function dropPendingTap() { if (tapTimer) { clearTimeout(tapTimer); tapTimer = null; } lastTapAt = 0; }
  function onTouchStart(e) { if (e.touches.length !== 1) { touch = null; return; } var t = e.touches[0]; touch = { x: t.clientX, y: t.clientY, at: Date.now() }; }
  function onTouchEnd(e) {
    if (!touch) return;
    var t = e.changedTouches[0], dx = t.clientX - touch.x, dy = t.clientY - touch.y, dt = Date.now() - touch.at;
    touch = null;
    var sel = window.getSelection();
    if (sel && !sel.isCollapsed) return;
    if (cfg.paged && !cfg.vertical && Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy) * 1.3 && dt < 800) {
      dropPendingTap();
      var forward = dx < 0;
      if (cfg.rtl) forward = !forward;
      if (forward) next(); else prev();
    }
  }
  function hitHighlight(x, y) {
    for (var i = 0; i < drawnHighlights.length; i++) {
      var rs = drawnHighlights[i].rects || [];
      for (var j = 0; j < rs.length; j++) { var r = rs[j]; if (x >= r.l && x <= r.r && y >= r.t && y <= r.b) return drawnHighlights[i]; }
    }
    return null;
  }
  function footnoteFor(a) {
    var href = a.getAttribute('href') || '';
    var type = (a.getAttribute('epub:type') || '') + ' ' + (a.getAttribute('role') || '');
    var isNote = /noteref/.test(type);
    var txt = (a.textContent || '').trim();
    if (!isNote && href.indexOf('#') >= 0 && /^[\[(]?(\d{1,3}|[*†‡§a-z])[\])]?$/i.test(txt)) isNote = true;
    if (!isNote && a.closest('sup')) isNote = href.indexOf('#') >= 0;
    if (!isNote) return null;
    var id = href.substring(href.indexOf('#') + 1);
    var path = href.substring(0, href.indexOf('#'));
    var html = '';
    if (!path) {
      // The same id may exist in every chapter: look in the chapter on screen first.
      var sec = currentSection();
      var el = (sec ? anchorIn(sec.el, id) : null) || document.getElementById(id);
      if (el) {
        var holder = el;
        if (el.tagName === 'A' && el.parentNode && el.parentNode !== document.body) holder = el.closest('li,p,aside,div,dd') || el.parentNode;
        html = holder.innerHTML;
      }
    } else {
      html = B.footnote(href) || '';
    }
    return html ? html : null;
  }
  function onClick(e) {
    var sel = window.getSelection();
    if (sel && !sel.isCollapsed) { dropPendingTap(); clearSelection(); B.onSelectionCleared(); e.preventDefault(); return; }
    var a = e.target.closest ? e.target.closest('a[href]') : null;
    if (a) {
      dropPendingTap();
      e.preventDefault();
      var note = footnoteFor(a);
      if (note) { B.onFootnote(note); return; }
      var rawHref = a.getAttribute('href') || '';
      // A link inside this document: the whole book is one page now, so B.onLink would not find a chapter for it.
      if (rawHref.charAt(0) === '#') {
        var id = rawHref.substring(1);
        try { id = decodeURIComponent(id); } catch (err) { /* keep the raw id */ }
        goToAnchor(id);
        return;
      }
      B.onLink(a.href);
      return;
    }
    var img = e.target.closest ? e.target.closest('img,image') : null;
    if (img && !cfg.paged && !cfg.fixed) { dropPendingTap(); B.onImage(img.currentSrc || img.src || img.getAttribute('xlink:href') || img.getAttribute('href') || ''); return; }
    var x = e.clientX, y = e.clientY;
    var h = hitHighlight(x, y);
    if (h) { dropPendingTap(); B.onHighlightTap(json({ id: h.id, left: x, top: y, right: x, bottom: y })); return; }
    if (img && !cfg.fixed) { dropPendingTap(); B.onImage(img.currentSrc || img.src || img.getAttribute('xlink:href') || img.getAttribute('href') || ''); return; }
    var now = Date.now();
    if (cfg.wordTap === 'DOUBLE_TAP') {
      // Only a real double tap on a word in the middle of the page opens the dictionary card. A single tap on the sides
      // still turns the page at once; a tap in the middle (where the menu lives) waits a moment for the second one.
      var zoneX = x / pageW;
      var word = (zoneX > 0.2 && zoneX < 0.8) ? wordAt(x, y) : null;
      if (!word) { dropPendingTap(); B.onTap(x / pageW, y / pageH); return; }
      if (now - lastTapAt < 300 && tapTimer) {
        clearTimeout(tapTimer); tapTimer = null; lastTapAt = 0;
        B.onWord(json(word));
        return;
      }
      lastTapAt = now;
      tapTimer = setTimeout(function () {
        tapTimer = null; lastTapAt = 0;
        B.onTap(x / pageW, y / pageH);
      }, 280);
      return;
    }
    if (cfg.wordTap === 'SINGLE_TAP') {
      var zoneX = window.innerWidth ? x / pageW : x;
      if (zoneX > 0.2 && zoneX < 0.8) { var w2 = wordAt(x, y); if (w2) { dropPendingTap(); B.onWord(json(w2)); return; } }
    }
    dropPendingTap();
    B.onTap(x / pageW, y / pageH);
  }

  // ---------------------------------------------------------------- blocks, sentences, translation
  function leafBlocks() {
    var out = [], all = document.body.querySelectorAll('p,h1,h2,h3,h4,h5,h6,li,blockquote,dd,dt,td,figcaption,div,pre');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.hasAttribute(SKIP) || el.closest('[' + SKIP + ']')) continue;
      var nested = false;
      for (var c = 0; c < el.children.length; c++) if (BLOCK[el.children[c].tagName]) { nested = true; break; }
      if (nested) continue;
      var t = (el.textContent || '').trim();
      if (t.length < 1) continue;
      out.push(el);
    }
    return out;
  }
  function blocks(from) {
    var list = leafBlocks(), res = [];
    var sec = currentSection();
    var base = isSectioned() && sec ? sec.start : 0;
    var i = 0;
    for (var k = 0; k < list.length; k++) {
      var el = list[k];
      if (sec && !sec.el.contains(el)) continue;
      var r = document.createRange(); r.selectNodeContents(el);
      var gs = posToOffset(r.startContainer, r.startOffset), ge = posToOffset(r.endContainer, r.endOffset);
      var s = gs - base, e = ge - base;
      var local = i++;
      el.setAttribute('data-yomu-i', String(local));
      if (from != null && e < from) continue;
      res.push({ i: local, start: s, end: e, text: (el.textContent || '').trim(), translated: !!(el.nextElementSibling && el.nextElementSibling.classList.contains('yomu-tr')) });
    }
    return res;
  }
  function insertTranslation(i, text) {
    var sec = currentSection();
    var root = sec ? sec.el : document;
    var el = root.querySelector ? root.querySelector('[data-yomu-i="' + i + '"]') : null;
    if (!el) return;
    var n = el.nextElementSibling;
    if (n && n.classList.contains('yomu-tr')) { n.textContent = text; return; }
    var tag = (el.tagName === 'LI' || el.tagName === 'TD') ? 'div' : 'p';
    var t = document.createElement(tag); t.className = 'yomu-tr'; t.setAttribute(SKIP, '1'); t.textContent = text;
    if (el.tagName === 'LI' || el.tagName === 'TD') el.appendChild(t); else el.parentNode.insertBefore(t, el.nextSibling);
  }
  function removeTranslations() { var l = document.querySelectorAll('.yomu-tr'); for (var i = 0; i < l.length; i++) l[i].remove(); }
  function sentences(from) {
    var sec = currentSection();
    var text = isSectioned() ? sectionText(sec) : fullText();
    var out = [], start = Math.max(0, from || 0);
    var list = blocks(start);
    list.forEach(function (b) {
      var s0 = Math.max(b.start, start), t = text.substring(s0, b.end);
      var pieces = [];
      if (window.Intl && Intl.Segmenter) {
        try { var seg = new Intl.Segmenter(cfg.lang || undefined, { granularity: 'sentence' }); var it = seg.segment(t)[Symbol.iterator](), st; while (!(st = it.next()).done) pieces.push({ i: st.value.index, s: st.value.segment }); } catch (e) { pieces = []; }
      }
      if (!pieces.length) { var m, last = 0; SENT_END.lastIndex = 0; while ((m = SENT_END.exec(t))) { pieces.push({ i: last, s: t.substring(last, m.index + m[0].length) }); last = m.index + m[0].length; } if (last < t.length) pieces.push({ i: last, s: t.substring(last) }); }
      pieces.forEach(function (p) { var clean = p.s.trim(); if (clean.length && /[\p{L}\p{N}]/u.test(clean)) { var lead = p.s.length - p.s.replace(/^\s+/, '').length; out.push({ start: s0 + p.i + lead, end: s0 + p.i + lead + clean.length, text: clean }); } });
    });
    return out;
  }
  function ensureVisible(off) {
    var sec = currentSection();
    var g = isSectioned() && sec ? sec.start + off : off;
    if (cfg.paged && !cfg.vertical) {
      var rc = rectOfOffset(g);
      if (rc.left < 0 || rc.left > pageW || rc.top < 0 || rc.top > pageH) goToGlobal(g, true);
    } else {
      var r = rectOfOffset(g);
      if (r.top < 0 || r.bottom > pageH - 20) se.scrollTop = se.scrollTop + r.top - (cfg.marginV || 28);
    }
  }

  // ---------------------------------------------------------------- API
  window.yomu = {
    next: next, prev: prev,
    goToOffset: function (o) { goToOffset(o, false); },
    goToPage: function (p) { if (cfg.paged && !cfg.vertical) setPage(p, false); },
    goToProgression: goToProgression,
    goToAnchor: goToAnchor,
    goToChapter: goToChapter,
    chapterStep: chapterStep,
    setConfig: function (c, css) {
      var keep = ready ? firstVisibleOffset() : null;
      var local = ready && isSectioned() ? toLocal(keep) : null;
      cfg = Object.assign(cfg, c || {});
      if (css != null) { var u = document.getElementById('yomu-user'); if (u) u.textContent = css; }
      applyLayout(); invalidatePages();
      if (local != null) setTimeout(function () { goToOffset(local.offset, false, local.chapter); }, 60);
      else if (keep != null) setTimeout(function () { goToGlobal(keep, false); }, 60);
      // A new font, margin or column count changes the page map: the page chip and the chapter ticks follow it.
      setTimeout(function () { if (ready && isSectioned()) B.onLayout(json(layoutInfo())); }, 160);
    },
    setHighlights: function (list) { highlights = list || []; drawAll(); },
    mark: function (kind, start, end, scroll) {
      marks[kind] = (start == null) ? null : { chapter: currentChapter(), start: start, end: end };
      if (start != null && scroll) ensureVisible(start);
      drawAll();
    },
    setSavedWords: function (list) { savedWords = (list || []).filter(function (w) { return w && w.length > 1; }).slice(0, 3000); savedWordRe = null; drawAll(); },
    clearSelection: clearSelection,
    select: select,
    expand: expand,
    selection: function () { return json(selectionInfo()); },
    blocks: function (from) { return json(blocks(from)); },
    insertTranslation: function (i, t) { insertTranslation(i, t); },
    commitTranslations: function () { var keep = firstVisibleOffset(); invalidate(); if (anchorOffset != null) keep = anchorOffset; setTimeout(function () { goToGlobal(keep, false); drawAll(); if (isSectioned()) B.onLayout(json(layoutInfo())); }, 60); },
    removeTranslations: function () { var keep = firstVisibleOffset(); removeTranslations(); invalidate(); setTimeout(function () { goToGlobal(keep, false); if (isSectioned()) B.onLayout(json(layoutInfo())); }, 60); },
    sentences: function (from) { return json(sentences(from)); },
    visibleText: function () { var a = firstVisibleOffset(), b = lastVisibleOffset(); return textBetween(a, Math.max(a, b)); },
    text: function (s, e) {
      var sec = currentSection();
      var base = isSectioned() && sec ? sec.start : 0;
      return textBetween(base + s, base + e);
    },
    scrollStep: function (px) { if (cfg.paged && !cfg.vertical) return; var before = se.scrollTop; se.scrollTop += px; if (se.scrollTop === before && px > 0) B.onEdge('next'); else reportLater(); },
    state: function () { return json({ page: currentPage(), pages: pageCount(), progression: progression(), offset: firstVisibleOffset(), total: nodes().total }); }
  };

  function start() {
    applyLayout();
    var go = function () {
      invalidate();
      var t = cfg.target || {};
      var ch = cfg.chapter != null ? cfg.chapter : 0;
      if (t.anchor && goToAnchor(t.anchor)) { /* positioned */ }
      else if (t.offset != null) goToOffset(t.offset, false, ch);
      else if (t.progression != null) {
        if (isSectioned()) {
          var sec = nodes().byChapter.get(ch);
          if (sec) goToOffset(Math.round(Math.max(0, Math.min(1, t.progression)) * Math.max(0, sec.len - 1)), false, ch);
        } else {
          goToProgression(t.progression);
        }
      }
      ready = true;
      setTimeout(function () {
        lastReport = '';
        report();
        var info = layoutInfo();
        B.onReady(json({ pages: info.pages || pageCount(), total: nodes().total, starts: info.starts }));
        if (isSectioned() && info.starts.length) B.onLayout(json(info));
        // Fill the feed around the opened chapter: below first, then a little above, so scrolling never runs out.
        if (isStrip() && !cfg.book) setTimeout(function () { stripFill(); }, 100);
      }, 40);
    };
    var fontsReady = document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve();
    // A whole book may hold hundreds of illustrations: waiting for all of them would delay the first page by seconds.
    // Fonts (which change line breaks) are waited for; pictures that arrive later re-anchor the page below.
    var loaded = cfg.book
      ? (document.readyState === 'loading'
        ? new Promise(function (res) { document.addEventListener('DOMContentLoaded', res, { once: true }); setTimeout(res, 1200); })
        : Promise.resolve())
      : (document.readyState === 'complete' ? Promise.resolve() : new Promise(function (res) { window.addEventListener('load', res, { once: true }); setTimeout(res, 2500); }));
    Promise.all([fontsReady, loaded]).then(function () { applyLayout(); setTimeout(go, 30); });
    if (cfg.book) {
      // A late picture changes the line breaks above it: keep the same text on screen and send the new page map.
      var imageLoaded = debounce(function () {
        if (!ready) return;
        var keep = firstVisibleOffset();
        applyLayout(); invalidatePages();
        setTimeout(function () {
          goToGlobal(keep, false);
          if (isSectioned()) B.onLayout(json(layoutInfo()));
        }, 80);
      }, 400);
      document.addEventListener('load', function (e) { if (e.target && e.target.tagName === 'IMG') imageLoaded(); }, true);
    }
    document.addEventListener('selectionchange', onSelectionChange);
    document.addEventListener('touchstart', onTouchStart, { passive: true });
    document.addEventListener('touchend', onTouchEnd, { passive: true });
    document.addEventListener('click', onClick, true);
    window.addEventListener('scroll', function () { if (!cfg.paged || cfg.vertical) reportLater(); stripManage(); }, { passive: true });
    window.addEventListener('resize', debounce(function () {
      var keep = firstVisibleOffset();
      applyLayout(); invalidatePages();
      setTimeout(function () {
        goToGlobal(keep, false);
        if (isSectioned()) B.onLayout(json(layoutInfo()));
      }, 120);
    }, 200));
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
