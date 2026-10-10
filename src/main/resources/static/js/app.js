/**
 * Lumina Online Library & PDF Reader - Core Frontend Application
 */

// Application State
const state = {
  currentView: 'explore', // 'explore' | 'shelf'
  currentCategory: 'all',
  searchQuery: '',
  books: [],
  shelf: [],
  selectedBook: null,
  isLoading: false,
};

// DOM Elements
const navSearchInput = document.getElementById('navSearchInput');
const heroSearchInput = document.getElementById('heroSearchInput');
const heroSearchForm = document.getElementById('heroSearchForm');
const clearSearchBtn = document.getElementById('clearSearchBtn');
const tabExplore = document.getElementById('tabExplore');
const tabShelf = document.getElementById('tabShelf');
const heroSection = document.getElementById('heroSection');
const exploreView = document.getElementById('exploreView');
const shelfView = document.getElementById('shelfView');
const booksGrid = document.getElementById('booksGrid');
const shelfGrid = document.getElementById('shelfGrid');
const loadingGrid = document.getElementById('loadingGrid');
const emptyState = document.getElementById('emptyState');
const emptyShelfState = document.getElementById('emptyShelfState');
const shelfBadge = document.getElementById('shelfBadge');
const sectionTitle = document.getElementById('sectionTitle');
const sectionSubtitle = document.getElementById('sectionSubtitle');
const resultsCount = document.getElementById('resultsCount');
const btnResetSearch = document.getElementById('btnResetSearch');
const btnOpenUpload = document.getElementById('btnOpenUpload');
const btnShelfUpload = document.getElementById('btnShelfUpload');
const btnShelfExplore = document.getElementById('btnShelfExplore');

// Modals
const bookModal = document.getElementById('bookModal');
const closeModalBtn = document.getElementById('closeModalBtn');
const uploadModal = document.getElementById('uploadModal');
const closeUploadModalBtn = document.getElementById('closeUploadModalBtn');
const uploadForm = document.getElementById('uploadForm');
const pdfFileInput = document.getElementById('pdfFileInput');
const uploadFileName = document.getElementById('uploadFileName');
const uploadTitle = document.getElementById('uploadTitle');
const uploadAuthor = document.getElementById('uploadAuthor');

// Modal Detail Elements
const modalCover = document.getElementById('modalCover');
const modalTitle = document.getElementById('modalTitle');
const modalAuthor = document.getElementById('modalAuthor');
const modalCategories = document.getElementById('modalCategories');
const modalPublisher = document.getElementById('modalPublisher');
const modalDate = document.getElementById('modalDate');
const modalPages = document.getElementById('modalPages');
const modalDescription = document.getElementById('modalDescription');
const modalRatingText = document.getElementById('modalRatingText');
const modalReadPdfBtn = document.getElementById('modalReadPdfBtn');
const modalAddToShelfBtn = document.getElementById('modalAddToShelfBtn');
const modalShelfText = document.getElementById('modalShelfText');
const modalDownloadDossierBtn = document.getElementById('modalDownloadDossierBtn');

// Initialize
document.addEventListener('DOMContentLoaded', async () => {
  if (window.lucide) lucide.createIcons();
  setupEventListeners();
  await loadShelf();
  await loadFeaturedBooks();
});

// Event Listeners Setup
function setupEventListeners() {
  // Navigation Tabs
  tabExplore.addEventListener('click', () => switchView('explore'));
  tabShelf.addEventListener('click', () => switchView('shelf'));
  btnShelfExplore.addEventListener('click', () => switchView('explore'));

  // Search Inputs
  let debounceTimer;
  const handleSearch = (query) => {
    state.searchQuery = query.trim();
    if (clearSearchBtn) {
      clearSearchBtn.classList.toggle('hidden', !state.searchQuery);
    }
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      if (state.searchQuery.length > 0) {
        performSearch(state.searchQuery, state.currentCategory);
      } else {
        loadFeaturedBooks();
      }
    }, 400);
  };

  navSearchInput.addEventListener('input', (e) => {
    heroSearchInput.value = e.target.value;
    handleSearch(e.target.value);
  });

  heroSearchInput.addEventListener('input', (e) => {
    navSearchInput.value = e.target.value;
    handleSearch(e.target.value);
  });

  heroSearchForm.addEventListener('submit', (e) => {
    e.preventDefault();
    if (heroSearchInput.value.trim()) {
      performSearch(heroSearchInput.value.trim(), state.currentCategory);
    }
  });

  clearSearchBtn.addEventListener('click', () => {
    navSearchInput.value = '';
    heroSearchInput.value = '';
    clearSearchBtn.classList.add('hidden');
    state.searchQuery = '';
    loadFeaturedBooks();
  });

  btnResetSearch.addEventListener('click', () => {
    navSearchInput.value = '';
    heroSearchInput.value = '';
    state.searchQuery = '';
    loadFeaturedBooks();
  });

  // Global Keyboard Shortcuts
  window.addEventListener('keydown', (e) => {
    if (e.key === '/' && document.activeElement !== navSearchInput && document.activeElement !== heroSearchInput) {
      e.preventDefault();
      navSearchInput.focus();
    }
    if (e.key === 'Escape') {
      closeBookModal();
      closeUploadModal();
    }
  });

  // Category Pills
  document.querySelectorAll('.cat-pill').forEach((pill) => {
    pill.addEventListener('click', () => {
      document.querySelectorAll('.cat-pill').forEach((p) => p.classList.remove('active'));
      pill.classList.add('active');
      state.currentCategory = pill.dataset.cat;
      if (state.searchQuery) {
        performSearch(state.searchQuery, state.currentCategory);
      } else {
        if (state.currentCategory === 'all') {
          loadFeaturedBooks();
        } else {
          performSearch(state.currentCategory, state.currentCategory);
        }
      }
    });
  });

  // Modals
  closeModalBtn.addEventListener('click', closeBookModal);
  bookModal.addEventListener('click', (e) => {
    if (e.target === bookModal) closeBookModal();
  });

  btnOpenUpload.addEventListener('click', openUploadModal);
  btnShelfUpload.addEventListener('click', openUploadModal);
  closeUploadModalBtn.addEventListener('click', closeUploadModal);
  uploadModal.addEventListener('click', (e) => {
    if (e.target === uploadModal) closeUploadModal();
  });

  // File Upload Form
  pdfFileInput.addEventListener('change', (e) => {
    if (e.target.files.length > 0) {
      const file = e.target.files[0];
      uploadFileName.textContent = file.name;
      if (!uploadTitle.value) {
        uploadTitle.value = file.name.replace(/\.[^/.]+$/, '');
      }
    }
  });

  uploadForm.addEventListener('submit', handleUploadSubmit);

  // Modal Actions
  modalReadPdfBtn.addEventListener('click', () => {
    if (state.selectedBook) {
      openInReader(state.selectedBook);
    }
  });

  modalAddToShelfBtn.addEventListener('click', () => {
    if (state.selectedBook) {
      toggleShelf(state.selectedBook);
    }
  });

  modalDownloadDossierBtn.addEventListener('click', () => {
    if (state.selectedBook) {
      downloadDossier(state.selectedBook);
    }
  });
}

// Switch between Explore and Shelf views
function switchView(view) {
  state.currentView = view;
  if (view === 'explore') {
    tabExplore.classList.add('active');
    tabShelf.classList.remove('active');
    heroSection.classList.remove('hidden');
    exploreView.classList.remove('hidden');
    shelfView.classList.add('hidden');
  } else {
    tabShelf.classList.add('active');
    tabExplore.classList.remove('active');
    heroSection.classList.add('hidden');
    exploreView.classList.add('hidden');
    shelfView.classList.remove('hidden');
    renderShelf();
  }
}

// Fetch Featured Books
async function loadFeaturedBooks() {
  setLoading(true);
  try {
    const res = await fetch('/api/books/featured');
    if (!res.ok) throw new Error('Failed to load featured books');
    const data = await res.json();
    state.books = data;
    sectionTitle.innerHTML = `<i data-lucide="flame" class="w-6 h-6 text-amber-400"></i><span>Featured Masterpieces & Classics</span>`;
    sectionSubtitle.textContent = 'Carefully curated books with instant full-text PDF reading and verified editions.';
    renderBooks(state.books);
  } catch (err) {
    console.error(err);
    showToast('Failed to load books: ' + err.message, 'error');
  } finally {
    setLoading(false);
  }
}

// Search Books API
async function performSearch(query, category) {
  setLoading(true);
  try {
    let url = `/api/books/search?q=${encodeURIComponent(query)}`;
    if (category && category !== 'all') {
      url += `&category=${encodeURIComponent(category)}`;
    }
    const res = await fetch(url);
    if (!res.ok) throw new Error('Search failed');
    const data = await res.json();

    // Map Google Books or Open Library response to unified format
    let items = [];
    if (data.items && Array.isArray(data.items)) {
      // Google Books API format
      items = data.items.map((item) => {
        const info = item.volumeInfo || {};
        const imageLinks = info.imageLinks || {};
        const thumbnail = (imageLinks.thumbnail || imageLinks.smallThumbnail || '')
          .replace('http://', 'https://');
        
        const authors = info.authors || ['Unknown Author'];
        const categories = info.categories || ['General Literature'];
        const desc = info.description || 'No detailed description available for this volume.';
        const title = info.title || 'Untitled Book';

        return {
          id: item.id || 'book-' + Math.random().toString(36).substr(2, 9),
          title: title,
          authors: authors,
          publisher: info.publisher || 'Verified Publisher',
          publishedDate: info.publishedDate || 'N/A',
          description: desc,
          pageCount: info.pageCount || 0,
          categories: categories,
          thumbnail: thumbnail || getDefaultCover(title),
          rating: info.averageRating || 4.5,
          pdfUrl: `/api/pdf/generate?id=${encodeURIComponent(item.id || '')}&title=${encodeURIComponent(title)}&author=${encodeURIComponent(authors.join(', '))}&category=${encodeURIComponent(categories[0] || '')}&description=${encodeURIComponent(desc)}`,
          hasDirectPdf: false,
        };
      });
    } else if (data.docs && Array.isArray(data.docs)) {
      // Open Library API format
      items = data.docs.map((doc) => {
        const title = doc.title || 'Untitled Book';
        const authors = doc.author_name || ['Unknown Author'];
        const categories = doc.subject ? doc.subject.slice(0, 3) : ['Literature & Science'];
        const coverUrl = doc.cover_i ? `https://covers.openlibrary.org/b/id/${doc.cover_i}-M.jpg` : getDefaultCover(title);
        const year = doc.first_publish_year ? doc.first_publish_year.toString() : 'Classic';
        const publisher = Array.isArray(doc.publisher) ? doc.publisher[0] : (doc.publisher || 'Open Library Archive');
        const desc = `Published in ${year}. Recorded in international library catalogs with ${doc.edition_count || 1} catalogued editions. Explore and analyze this volume through Lumina's interactive study reader.`;

        return {
          id: (doc.key || 'ol-' + Math.random().toString(36).substr(2, 9)).replace('/works/', ''),
          title: title,
          authors: authors,
          publisher: publisher,
          publishedDate: year,
          description: desc,
          pageCount: doc.number_of_pages_median || 150,
          categories: categories,
          thumbnail: coverUrl,
          rating: 4.7,
          pdfUrl: `/api/pdf/generate?id=${encodeURIComponent(doc.key || '')}&title=${encodeURIComponent(title)}&author=${encodeURIComponent(authors.join(', '))}&category=${encodeURIComponent(categories[0] || '')}&description=${encodeURIComponent(desc)}`,
          hasDirectPdf: false,
        };
      });
    }

    state.books = items;
    sectionTitle.innerHTML = `<i data-lucide="search" class="w-6 h-6 text-emerald-400"></i><span>Search Results for "${escapeHtml(query)}"</span>`;
    sectionSubtitle.textContent = `Found ${items.length} titles matching your criteria`;
    renderBooks(state.books);
  } catch (err) {
    console.error(err);
    showToast('Search encountered an error. Please try again.', 'error');
  } finally {
    setLoading(false);
  }
}

// Render Book Cards in Explore Grid
function renderBooks(books) {
  booksGrid.innerHTML = '';
  resultsCount.textContent = books.length;

  if (books.length === 0) {
    emptyState.classList.remove('hidden');
    return;
  }
  emptyState.classList.add('hidden');

  books.forEach((book) => {
    const isSaved = isBookInShelf(book.id);
    const card = document.createElement('div');
    card.className = 'book-card bg-night-800/80 border border-slate-700/80 rounded-2xl p-4 flex flex-col justify-between hover:border-emerald-500/50 backdrop-blur-sm';

    const authors = Array.isArray(book.authors) ? book.authors.join(', ') : (book.authors || 'Unknown');
    const category = Array.isArray(book.categories) ? book.categories[0] : (book.categories || 'Literature');
    const coverUrl = book.thumbnail || getDefaultCover(book.title);

    card.innerHTML = `
      <div>
        <div class="cover-container w-full h-52 rounded-xl bg-slate-900 border border-slate-800 mb-3 flex items-center justify-center relative cursor-pointer" onclick="openBookModal('${book.id}')">
          <img src="${coverUrl}" alt="${escapeHtml(book.title)}" class="w-full h-full object-cover rounded-xl" onerror="this.src='${getDefaultCover(book.title)}'">
          <div class="absolute top-2 right-2">
            <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-night-900/80 backdrop-blur text-amber-400 text-[11px] font-bold border border-slate-700">
              <i data-lucide="star" class="w-3 h-3 fill-amber-400"></i>
              ${book.rating || '4.5'}
            </span>
          </div>
          <div class="absolute bottom-2 left-2">
            <span class="px-2 py-0.5 rounded-md bg-emerald-500/20 backdrop-blur text-emerald-300 text-[10px] font-semibold border border-emerald-500/30">
              ${escapeHtml(category)}
            </span>
          </div>
        </div>

        <h3 class="font-bold text-white text-base leading-snug line-clamp-2 hover:text-emerald-400 transition-colors cursor-pointer mb-1" onclick="openBookModal('${book.id}')">
          ${escapeHtml(book.title)}
        </h3>
        <p class="text-xs text-slate-400 line-clamp-1 mb-2">By ${escapeHtml(authors)}</p>
      </div>

      <div class="pt-3 border-t border-slate-700/60 flex items-center justify-between gap-2 mt-2">
        <button onclick="openBookModal('${book.id}')" class="flex-1 py-2 px-3 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors">
          <i data-lucide="book-open" class="w-3.5 h-3.5"></i>
          <span>Read PDF</span>
        </button>

        <button onclick="toggleShelfById('${book.id}')" class="p-2 rounded-xl ${isSaved ? 'bg-emerald-500 text-night-900' : 'bg-slate-700/50 hover:bg-slate-700 text-slate-300'} text-xs font-semibold transition-all" title="${isSaved ? 'Remove from Shelf' : 'Save to Shelf'}">
          <i data-lucide="bookmark" class="w-4 h-4 ${isSaved ? 'fill-current' : ''}"></i>
        </button>
      </div>
    `;

    booksGrid.appendChild(card);
  });

  if (window.lucide) lucide.createIcons();
}

// Shelf Persistence
async function loadShelf() {
  try {
    const res = await fetch('/api/shelf');
    if (!res.ok) return;
    const data = await res.json();
    state.shelf = Array.isArray(data) ? data : [];
    updateShelfBadge();
  } catch (err) {
    console.error('Failed to load shelf:', err);
  }
}

function updateShelfBadge() {
  const count = state.shelf.length;
  if (count > 0) {
    shelfBadge.textContent = count;
    shelfBadge.classList.remove('hidden');
  } else {
    shelfBadge.classList.add('hidden');
  }
}

function isBookInShelf(id) {
  return state.shelf.some((b) => b.id === id);
}

async function toggleShelf(book) {
  const inShelf = isBookInShelf(book.id);
  if (inShelf) {
    await removeFromShelf(book.id);
    modalShelfText.textContent = 'Save to Shelf';
    showToast(`Removed "${book.title}" from your shelf`, 'info');
  } else {
    await addToShelf(book);
    modalShelfText.textContent = 'Saved in Shelf';
    showToast(`Added "${book.title}" to your shelf`, 'success');
  }
  updateModalShelfButton();
  if (state.currentView === 'explore') renderBooks(state.books);
  if (state.currentView === 'shelf') renderShelf();
}

window.toggleShelfById = (id) => {
  const book = state.books.find((b) => b.id === id) || state.shelf.find((b) => b.id === id);
  if (book) toggleShelf(book);
};

async function addToShelf(book) {
  const item = {
    ...book,
    currentPage: 1,
    status: 'want_to_read',
    savedAt: Date.now(),
    notes: '',
  };
  try {
    const res = await fetch('/api/shelf', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(item),
    });
    if (res.ok) {
      state.shelf.unshift(item);
      updateShelfBadge();
    }
  } catch (err) {
    console.error(err);
  }
}

async function removeFromShelf(id) {
  try {
    const res = await fetch(`/api/shelf?id=${encodeURIComponent(id)}`, { method: 'DELETE' });
    if (res.ok) {
      state.shelf = state.shelf.filter((b) => b.id !== id);
      updateShelfBadge();
    }
  } catch (err) {
    console.error(err);
  }
}

// Render Shelf View
function renderShelf() {
  shelfGrid.innerHTML = '';
  if (state.shelf.length === 0) {
    emptyShelfState.classList.remove('hidden');
    return;
  }
  emptyShelfState.classList.add('hidden');

  state.shelf.forEach((book) => {
    const card = document.createElement('div');
    card.className = 'book-card bg-night-800/80 border border-slate-700/80 rounded-2xl p-4 flex flex-col justify-between hover:border-emerald-500/50 backdrop-blur-sm';

    const authors = Array.isArray(book.authors) ? book.authors.join(', ') : (book.authors || 'Personal');
    const coverUrl = book.thumbnail || getDefaultCover(book.title);
    const currentPage = book.currentPage || 1;
    const pageCount = book.pageCount || 50;
    const progress = Math.min(100, Math.round((currentPage / (pageCount || 1)) * 100));

    card.innerHTML = `
      <div>
        <div class="cover-container w-full h-48 rounded-xl bg-slate-900 border border-slate-800 mb-3 flex items-center justify-center relative cursor-pointer" onclick="openInReaderById('${book.id}')">
          <img src="${coverUrl}" alt="${escapeHtml(book.title)}" class="w-full h-full object-cover rounded-xl" onerror="this.src='${getDefaultCover(book.title)}'">
          <div class="absolute bottom-2 left-2">
            <span class="px-2 py-0.5 rounded-md bg-emerald-500 text-night-900 text-[10px] font-bold">
              ${book.isLocal ? 'Personal PDF' : 'In Library'}
            </span>
          </div>
        </div>

        <h3 class="font-bold text-white text-base leading-snug line-clamp-2 hover:text-emerald-400 transition-colors cursor-pointer mb-1" onclick="openInReaderById('${book.id}')">
          ${escapeHtml(book.title)}
        </h3>
        <p class="text-xs text-slate-400 line-clamp-1 mb-2">By ${escapeHtml(authors)}</p>

        <!-- Progress meter -->
        <div class="my-2 space-y-1">
          <div class="flex justify-between text-[11px] text-slate-400">
            <span>Page ${currentPage}</span>
            <span class="text-emerald-400 font-semibold">${progress}%</span>
          </div>
          <div class="w-full h-1.5 bg-slate-700 rounded-full overflow-hidden">
            <div class="h-full bg-emerald-500" style="width: ${progress}%"></div>
          </div>
        </div>
      </div>

      <div class="pt-3 border-t border-slate-700/60 flex items-center justify-between gap-2 mt-2">
        <button onclick="openInReaderById('${book.id}')" class="flex-1 py-2 px-3 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-600 text-night-900 text-xs font-bold flex items-center justify-center gap-1.5 transition-all">
          <i data-lucide="book-open" class="w-3.5 h-3.5"></i>
          <span>Resume Reading</span>
        </button>

        <button onclick="removeFromShelfAndRender('${book.id}')" class="p-2 rounded-xl bg-slate-700/50 hover:bg-red-500/20 hover:text-red-400 text-slate-400 text-xs transition-colors" title="Remove from shelf">
          <i data-lucide="trash-2" class="w-4 h-4"></i>
        </button>
      </div>
    `;

    shelfGrid.appendChild(card);
  });

  if (window.lucide) lucide.createIcons();
}

window.removeFromShelfAndRender = async (id) => {
  await removeFromShelf(id);
  renderShelf();
  showToast('Removed book from shelf', 'info');
};

// Book Details Modal
window.openBookModal = (id) => {
  const book = state.books.find((b) => b.id === id) || state.shelf.find((b) => b.id === id);
  if (!book) return;
  state.selectedBook = book;

  modalTitle.textContent = book.title;
  modalAuthor.textContent = 'By ' + (Array.isArray(book.authors) ? book.authors.join(', ') : (book.authors || 'Unknown'));
  modalCover.src = book.thumbnail || getDefaultCover(book.title);
  modalPublisher.textContent = book.publisher || 'Verified Digital Publication';
  modalDate.textContent = book.publishedDate || 'N/A';
  modalPages.textContent = book.pageCount ? `${book.pageCount} Pages` : 'Standard Volume';
  modalDescription.innerHTML = book.description ? book.description.replace(/\n/g, '<br>') : 'No synopsis provided for this volume.';
  modalRatingText.textContent = `${book.rating || '4.8'} / 5.0`;

  // Categories pills
  modalCategories.innerHTML = '';
  const cats = Array.isArray(book.categories) ? book.categories : [book.categories || 'Literature'];
  cats.forEach((cat) => {
    const badge = document.createElement('span');
    badge.className = 'px-2.5 py-1 rounded-full bg-slate-800 text-slate-300 text-xs font-medium border border-slate-700';
    badge.textContent = cat;
    modalCategories.appendChild(badge);
  });

  updateModalShelfButton();
  bookModal.classList.remove('hidden');
  bookModal.classList.add('flex');
  if (window.lucide) lucide.createIcons();
};

function updateModalShelfButton() {
  if (!state.selectedBook) return;
  const inShelf = isBookInShelf(state.selectedBook.id);
  if (inShelf) {
    modalShelfText.textContent = 'Saved in Shelf';
    modalAddToShelfBtn.classList.add('border-emerald-500', 'text-emerald-400');
  } else {
    modalShelfText.textContent = 'Save to Shelf';
    modalAddToShelfBtn.classList.remove('border-emerald-500', 'text-emerald-400');
  }
}

function closeBookModal() {
  bookModal.classList.add('hidden');
  bookModal.classList.remove('flex');
}

// Open In Reader
function openInReader(book) {
  // If book has direct PDF or dossier URL
  let pdfUrl = book.pdfUrl;
  if (!pdfUrl) {
    pdfUrl = `/api/pdf/generate?id=${encodeURIComponent(book.id)}&title=${encodeURIComponent(book.title)}&author=${encodeURIComponent((book.authors || []).join(', '))}&category=${encodeURIComponent((book.categories || [])[0] || '')}`;
  }

  // Encode book parameters to reader studio
  const readerUrl = `/reader.html?id=${encodeURIComponent(book.id)}&title=${encodeURIComponent(book.title)}&author=${encodeURIComponent((book.authors || []).join(', '))}&pdfUrl=${encodeURIComponent(pdfUrl)}`;
  window.location.href = readerUrl;
}

window.openInReaderById = (id) => {
  const book = state.shelf.find((b) => b.id === id) || state.books.find((b) => b.id === id);
  if (book) openInReader(book);
};

// Download Java-generated PDF Dossier
function downloadDossier(book) {
  showToast('Generating high-resolution PDF Dossier in Java SE...', 'info');
  const url = `/api/pdf/generate?id=${encodeURIComponent(book.id)}&title=${encodeURIComponent(book.title)}&author=${encodeURIComponent((book.authors || []).join(', '))}&publisher=${encodeURIComponent(book.publisher || '')}&category=${encodeURIComponent((book.categories || [])[0] || '')}&description=${encodeURIComponent(book.description || '')}`;
  const link = document.createElement('a');
  link.href = url;
  link.download = `${book.title.replace(/[^a-zA-Z0-9]/g, '_')}_Dossier.pdf`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  setTimeout(() => showToast('PDF Dossier downloaded successfully!', 'success'), 1200);
}

// Upload Modal & Handler
function openUploadModal() {
  uploadModal.classList.remove('hidden');
  uploadModal.classList.add('flex');
}

function closeUploadModal() {
  uploadModal.classList.add('hidden');
  uploadModal.classList.remove('flex');
  uploadForm.reset();
  uploadFileName.textContent = 'Click to choose PDF or drag & drop';
}

async function handleUploadSubmit(e) {
  e.preventDefault();
  const file = pdfFileInput.files[0];
  if (!file) {
    showToast('Please select a .pdf file', 'error');
    return;
  }

  const title = uploadTitle.value.trim() || file.name;
  const author = uploadAuthor.value.trim() || 'Personal Upload';

  const btn = document.getElementById('btnSubmitUpload');
  btn.disabled = true;
  btn.textContent = 'Uploading PDF...';

  try {
    const url = `/api/upload?title=${encodeURIComponent(title)}&author=${encodeURIComponent(author)}`;
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/pdf' },
      body: file,
    });

    if (!res.ok) throw new Error('Upload failed');
    const newBook = await res.json();

    await loadShelf();
    closeUploadModal();
    showToast(`"${title}" uploaded & added to your shelf!`, 'success');
    switchView('shelf');
  } catch (err) {
    console.error(err);
    showToast('Failed to upload PDF: ' + err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Upload & Add to My Shelf';
  }
}

// Utilities
function setLoading(loading) {
  state.isLoading = loading;
  if (loading) {
    loadingGrid.classList.remove('hidden');
    booksGrid.classList.add('hidden');
    emptyState.classList.add('hidden');
  } else {
    loadingGrid.classList.add('hidden');
    booksGrid.classList.remove('hidden');
  }
}

function getDefaultCover(title) {
  // SVG abstract modern book cover fallback
  const safeTitle = encodeURIComponent(title ? title.substring(0, 24) : 'Book');
  return `https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&auto=format&fit=crop&q=80`;
}

function escapeHtml(str) {
  if (!str) return '';
  return str.toString()
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  const colors = {
    success: 'bg-emerald-600 text-white',
    error: 'bg-red-600 text-white',
    info: 'bg-night-800 border border-slate-700 text-slate-100',
  };
  const icons = {
    success: 'check-circle',
    error: 'alert-circle',
    info: 'info',
  };

  toast.className = `toast-item pointer-events-auto px-4 py-3 rounded-2xl shadow-2xl flex items-center gap-3 text-sm font-medium ${colors[type] || colors.info}`;
  toast.innerHTML = `
    <i data-lucide="${icons[type] || 'info'}" class="w-4 h-4 flex-shrink-0"></i>
    <span>${escapeHtml(message)}</span>
  `;

  container.appendChild(toast);
  if (window.lucide) lucide.createIcons();

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}
