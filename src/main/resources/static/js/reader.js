/**
 * Lumina Online Library - Reader Studio Engine
 * Powered by Mozilla PDF.js & Java SE Backend
 */

// Reader State
const readerState = {
  pdfDoc: null,
  pageNum: 1,
  pageRendering: false,
  pageNumPending: null,
  scale: 1.25,
  bookId: null,
  bookTitle: 'Document Reader',
  bookAuthor: 'Online Library',
  pdfUrl: null,
  theme: localStorage.getItem('lumina_reader_theme') || 'dark',
};

// DOM Elements
const canvas = document.getElementById('pdfCanvas');
const ctx = canvas.getContext('2d');
const pageNumberInput = document.getElementById('pageNumberInput');
const pageCount = document.getElementById('pageCount');
const prevPageBtn = document.getElementById('prevPageBtn');
const nextPageBtn = document.getElementById('nextPageBtn');
const mobPrevBtn = document.getElementById('mobPrevBtn');
const mobNextBtn = document.getElementById('mobNextBtn');
const mobPageText = document.getElementById('mobPageText');
const zoomInBtn = document.getElementById('zoomInBtn');
const zoomOutBtn = document.getElementById('zoomOutBtn');
const fitWidthBtn = document.getElementById('fitWidthBtn');
const zoomLevelText = document.getElementById('zoomLevelText');
const readerTitle = document.getElementById('readerTitle');
const readerAuthor = document.getElementById('readerAuthor');
const pdfLoadingIndicator = document.getElementById('pdfLoadingIndicator');
const pdfViewerContainer = document.getElementById('pdfViewerContainer');
const fullscreenBtn = document.getElementById('fullscreenBtn');
const downloadPdfBtn = document.getElementById('downloadPdfBtn');
const toggleSidebarBtn = document.getElementById('toggleSidebarBtn');
const closeSidebarBtn = document.getElementById('closeSidebarBtn');
const readerSidebar = document.getElementById('readerSidebar');
const bookNotesInput = document.getElementById('bookNotesInput');
const progressBar = document.getElementById('progressBar');
const progressPercent = document.getElementById('progressPercent');

// Themes
const themeDarkBtn = document.getElementById('themeDarkBtn');
const themeSepiaBtn = document.getElementById('themeSepiaBtn');
const themeLightBtn = document.getElementById('themeLightBtn');

// Jump buttons
const btnJumpStart = document.getElementById('btnJumpStart');
const btnJumpMiddle = document.getElementById('btnJumpMiddle');
const btnJumpEnd = document.getElementById('btnJumpEnd');

// Initialize Reader
document.addEventListener('DOMContentLoaded', async () => {
  if (window.lucide) lucide.createIcons();

  parseUrlParams();
  applyTheme(readerState.theme);
  setupEventListeners();
  loadSavedNotes();
  await initPdf();
});

function parseUrlParams() {
  const params = new URLSearchParams(window.location.search);
  readerState.bookId = params.get('id') || 'book-doc';
  readerState.bookTitle = params.get('title') || 'Public Classic Book';
  readerState.bookAuthor = params.get('author') || 'Online Library Edition';
  readerState.pdfUrl = params.get('pdfUrl');

  if (!readerState.pdfUrl) {
    // Default fallback to Art of War sample
    readerState.pdfUrl = '/samples/the-art-of-war.pdf';
  }

  readerTitle.textContent = readerState.bookTitle;
  readerAuthor.textContent = 'By ' + readerState.bookAuthor;
  document.title = `${readerState.bookTitle} - Lumina Reader`;
}

function setupEventListeners() {
  // Page buttons
  prevPageBtn.addEventListener('click', onPrevPage);
  nextPageBtn.addEventListener('click', onNextPage);
  if (mobPrevBtn) mobPrevBtn.addEventListener('click', onPrevPage);
  if (mobNextBtn) mobNextBtn.addEventListener('click', onNextPage);

  // Direct page input
  pageNumberInput.addEventListener('change', (e) => {
    const val = parseInt(e.target.value, 10);
    if (val >= 1 && val <= readerState.pdfDoc.numPages) {
      queueRenderPage(val);
    } else {
      e.target.value = readerState.pageNum;
    }
  });

  // Zoom
  zoomInBtn.addEventListener('click', () => {
    if (readerState.scale < 3.0) {
      readerState.scale += 0.2;
      updateZoomText();
      queueRenderPage(readerState.pageNum);
    }
  });

  zoomOutBtn.addEventListener('click', () => {
    if (readerState.scale > 0.6) {
      readerState.scale -= 0.2;
      updateZoomText();
      queueRenderPage(readerState.pageNum);
    }
  });

  fitWidthBtn.addEventListener('click', () => {
    const containerWidth = pdfViewerContainer.clientWidth - 48;
    readerState.pdfDoc.getPage(readerState.pageNum).then((page) => {
      const unscaledViewport = page.getViewport({ scale: 1 });
      readerState.scale = Math.min(2.5, Math.max(0.6, containerWidth / unscaledViewport.width));
      updateZoomText();
      queueRenderPage(readerState.pageNum);
    });
  });

  // Themes
  themeDarkBtn.addEventListener('click', () => applyTheme('dark'));
  themeSepiaBtn.addEventListener('click', () => applyTheme('sepia'));
  themeLightBtn.addEventListener('click', () => applyTheme('light'));

  // Sidebar toggle
  toggleSidebarBtn.addEventListener('click', () => {
    readerSidebar.classList.toggle('translate-x-full');
    readerSidebar.classList.toggle('hidden');
  });

  closeSidebarBtn.addEventListener('click', () => {
    readerSidebar.classList.add('hidden');
  });

  // Fullscreen
  fullscreenBtn.addEventListener('click', toggleFullscreen);

  // Download
  downloadPdfBtn.addEventListener('click', () => {
    const a = document.createElement('a');
    a.href = readerState.pdfUrl;
    a.download = `${readerState.bookTitle.replace(/[^a-zA-Z0-9]/g, '_')}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  });

  // Keyboard Navigation
  window.addEventListener('keydown', (e) => {
    if (document.activeElement === bookNotesInput || document.activeElement === pageNumberInput) {
      return;
    }
    if (e.key === 'ArrowLeft' || e.key === 'PageUp' || e.key === 'k') {
      e.preventDefault();
      onPrevPage();
    } else if (e.key === 'ArrowRight' || e.key === 'PageDown' || e.key === 'j') {
      e.preventDefault();
      onNextPage();
    } else if (e.key === '+' || e.key === '=') {
      e.preventDefault();
      zoomInBtn.click();
    } else if (e.key === '-' || e.key === '_') {
      e.preventDefault();
      zoomOutBtn.click();
    } else if (e.key === 'f' || e.key === 'F') {
      e.preventDefault();
      toggleFullscreen();
    }
  });

  // Jump buttons
  btnJumpStart.addEventListener('click', () => queueRenderPage(1));
  btnJumpMiddle.addEventListener('click', () => {
    if (readerState.pdfDoc) {
      queueRenderPage(Math.max(1, Math.floor(readerState.pdfDoc.numPages / 2)));
    }
  });
  btnJumpEnd.addEventListener('click', () => {
    if (readerState.pdfDoc) {
      queueRenderPage(readerState.pdfDoc.numPages);
    }
  });

  // Notes Auto-save with debounce
  let notesTimer;
  bookNotesInput.addEventListener('input', () => {
    clearTimeout(notesTimer);
    notesTimer = setTimeout(saveProgressAndNotes, 800);
  });
}

function updateZoomText() {
  zoomLevelText.textContent = `${Math.round(readerState.scale * 100)}%`;
}

function applyTheme(theme) {
  readerState.theme = theme;
  localStorage.setItem('lumina_reader_theme', theme);
  document.body.className = `theme-${theme} min-h-screen flex flex-col font-sans select-none overflow-hidden`;

  // Update button highlights
  themeDarkBtn.classList.toggle('text-emerald-400', theme === 'dark');
  themeSepiaBtn.classList.toggle('text-amber-500', theme === 'sepia');
  themeLightBtn.classList.toggle('text-blue-500', theme === 'light');
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {});
  } else {
    if (document.exitFullscreen) document.exitFullscreen();
  }
}

// Load and initialize PDF via PDF.js
async function initPdf() {
  pdfLoadingIndicator.classList.remove('hidden');

  let loadUrl = readerState.pdfUrl;
  // If external url, route through proxy
  if (loadUrl.startsWith('http://') || loadUrl.startsWith('https://')) {
    if (!loadUrl.includes(window.location.host)) {
      loadUrl = `/api/pdf/proxy?url=${encodeURIComponent(loadUrl)}`;
    }
  }

  try {
    const loadingTask = pdfjsLib.getDocument({
      url: loadUrl,
      cMapUrl: 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/cmaps/',
      cMapPacked: true,
    });

    readerState.pdfDoc = await loadingTask.promise;
    pageCount.textContent = readerState.pdfDoc.numPages;

    // Load saved page progress if any
    const savedPage = parseInt(localStorage.getItem(`lumina_page_${readerState.bookId}`) || '1', 10);
    readerState.pageNum = Math.min(readerState.pdfDoc.numPages, Math.max(1, savedPage));

    renderPage(readerState.pageNum);
  } catch (err) {
    console.error('PDF rendering error:', err);
    // If loading failed (e.g. sample not yet generated or external link blocked), fallback to dynamic dossier
    showErrorAndGenerateFallback(err.message);
  } finally {
    pdfLoadingIndicator.classList.add('hidden');
  }
}

// Fallback to generated PDF dossier if file not found
async function showErrorAndGenerateFallback(errorMsg) {
  console.warn('Switching to dynamic generated PDF dossier due to:', errorMsg);
  const fallbackUrl = `/api/pdf/generate?id=${encodeURIComponent(readerState.bookId)}&title=${encodeURIComponent(readerState.bookTitle)}&author=${encodeURIComponent(readerState.bookAuthor)}`;
  readerState.pdfUrl = fallbackUrl;
  try {
    const loadingTask = pdfjsLib.getDocument(fallbackUrl);
    readerState.pdfDoc = await loadingTask.promise;
    pageCount.textContent = readerState.pdfDoc.numPages;
    readerState.pageNum = 1;
    renderPage(readerState.pageNum);
  } catch (e) {
    pdfViewerContainer.innerHTML = `
      <div class="m-auto text-center p-8 bg-night-800 rounded-2xl border border-slate-700 max-w-md space-y-4">
        <i data-lucide="alert-triangle" class="w-12 h-12 text-amber-400 mx-auto"></i>
        <h3 class="text-lg font-bold text-white">Could not load PDF</h3>
        <p class="text-sm text-slate-400">${escapeHtml(errorMsg)}</p>
        <a href="/" class="inline-block px-4 py-2 bg-emerald-500 text-night-900 font-semibold rounded-xl text-sm">
          Return to Library
        </a>
      </div>
    `;
    if (window.lucide) lucide.createIcons();
  }
}

// Render Page on Canvas
function renderPage(num) {
  readerState.pageRendering = true;

  readerState.pdfDoc.getPage(num).then((page) => {
    const dpr = window.devicePixelRatio || 1;
    const viewport = page.getViewport({ scale: readerState.scale });

    // Canvas size multiplied by devicePixelRatio for sharp high-DPI text
    canvas.height = viewport.height * dpr;
    canvas.width = viewport.width * dpr;
    canvas.style.height = `${viewport.height}px`;
    canvas.style.width = `${viewport.width}px`;

    const renderContext = {
      canvasContext: ctx,
      transform: [dpr, 0, 0, dpr, 0, 0],
      viewport: viewport,
    };

    const renderTask = page.render(renderContext);

    renderTask.promise.then(() => {
      readerState.pageRendering = false;
      if (readerState.pageNumPending !== null) {
        renderPage(readerState.pageNumPending);
        readerState.pageNumPending = null;
      }
    });
  });

  // Update controls
  pageNumberInput.value = num;
  if (mobPageText) mobPageText.textContent = `${num} / ${readerState.pdfDoc.numPages}`;

  // Update progress
  updateProgress(num);

  // Save page position locally and to server
  localStorage.setItem(`lumina_page_${readerState.bookId}`, num);
  saveProgressAndNotes();
}

function queueRenderPage(num) {
  if (readerState.pageRendering) {
    readerState.pageNumPending = num;
  } else {
    readerState.pageNum = num;
    renderPage(num);
  }
}

function onPrevPage() {
  if (readerState.pageNum <= 1) return;
  readerState.pageNum--;
  queueRenderPage(readerState.pageNum);
}

function onNextPage() {
  if (!readerState.pdfDoc || readerState.pageNum >= readerState.pdfDoc.numPages) return;
  readerState.pageNum++;
  queueRenderPage(readerState.pageNum);
}

// Progress calculation
function updateProgress(page) {
  if (!readerState.pdfDoc) return;
  const total = readerState.pdfDoc.numPages;
  const pct = Math.min(100, Math.round((page / total) * 100));
  progressBar.style.width = `${pct}%`;
  progressPercent.textContent = `${pct}%`;
}

// Save reading position and notes to server
async function saveProgressAndNotes() {
  const notes = bookNotesInput.value;
  localStorage.setItem(`lumina_notes_${readerState.bookId}`, notes);

  try {
    await fetch('/api/shelf/progress', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id: readerState.bookId,
        currentPage: readerState.pageNum,
        notes: notes,
        status: readerState.pageNum >= (readerState.pdfDoc ? readerState.pdfDoc.numPages : 999) ? 'completed' : 'reading',
      }),
    });
  } catch (ignored) {}
}

function loadSavedNotes() {
  const localNotes = localStorage.getItem(`lumina_notes_${readerState.bookId}`);
  if (localNotes) {
    bookNotesInput.value = localNotes;
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return str.toString()
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
