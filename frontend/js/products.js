// ============================================================
// Catalog (index.html) page logic
// ============================================================

let allProducts = [];
let activeCategories = new Set(['all']);
let maxPriceCents = 500000 * 100; // slider is in Ksh whole units; store cents internally
let sortMode = 'featured';

function productCardHtml(p) {
  const outOfStock = p.stockQuantity <= 0;
  const imgUrl = p.imageUrl || `https://picsum.photos/seed/${p.id}/400/400`;

  return `
    <article class="bg-surface-container-lowest rounded-xl border border-outline-variant flex flex-col product-card-shadow transition-all group overflow-hidden">
      <a href="product.html?id=${p.id}" class="relative aspect-square bg-[#F8FAFC] overflow-hidden block">
        <img class="w-full h-full object-contain p-4 group-hover:scale-105 transition-transform duration-500"
             src="${imgUrl}" alt="${p.name}" loading="lazy"/>
        <span class="absolute top-3 left-3 bg-primary-container text-on-primary-container text-[10px] font-bold px-2 py-1 rounded uppercase tracking-widest">${p.categoryName || ''}</span>
        ${outOfStock ? '<div class="absolute inset-0 bg-white/70 flex items-center justify-center"><span class="bg-error-container text-on-error-container text-xs font-bold px-3 py-1 rounded-full">Out of Stock</span></div>' : ''}
      </a>
      <div class="p-stack-md flex flex-col flex-1">
        <a href="product.html?id=${p.id}"><h2 class="font-headline-md text-body-lg text-on-surface mb-1 hover:text-primary transition-colors">${p.name}</h2></a>
        <p class="text-primary font-bold text-body-md mb-stack-md">${formatCents(p.priceCents)}</p>
        <button ${outOfStock ? 'disabled' : ''} onclick="quickAddToCart(${p.id}, '${p.name.replace(/'/g, "\\'")}')"
          class="mt-auto w-full py-3 bg-primary text-white font-button rounded-xl hover:bg-primary-container transition-all active:scale-95 flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed">
          <span class="material-symbols-outlined text-[20px]">shopping_cart</span>
          ${outOfStock ? 'Unavailable' : 'Add to Cart'}
        </button>
      </div>
    </article>
  `;
}

function buildCategoryFilters() {
  const container = document.getElementById('category-filters');
  const categories = [...new Set(allProducts.map(p => p.categoryName).filter(Boolean))];

  categories.forEach(cat => {
    const label = document.createElement('label');
    label.className = 'flex items-center gap-stack-sm cursor-pointer group';
    label.innerHTML = `
      <input class="category-checkbox rounded text-primary focus:ring-primary border-outline-variant" type="checkbox" value="${cat}"/>
      <span class="text-body-md group-hover:text-primary transition-colors">${cat}</span>
    `;
    container.appendChild(label);
  });

  container.querySelectorAll('.category-checkbox').forEach(cb => {
    cb.addEventListener('change', handleCategoryChange);
  });
}

function handleCategoryChange(e) {
  const checkboxes = document.querySelectorAll('.category-checkbox');
  const allBox = document.querySelector('.category-checkbox[value="all"]');

  if (e.target.value === 'all') {
    checkboxes.forEach(cb => { if (cb !== allBox) cb.checked = false; });
    activeCategories = new Set(['all']);
  } else {
    allBox.checked = false;
    activeCategories = new Set([...checkboxes].filter(cb => cb.checked).map(cb => cb.value));
    if (activeCategories.size === 0) {
      allBox.checked = true;
      activeCategories = new Set(['all']);
    }
  }
  renderGrid();
}

function renderGrid() {
  const grid = document.getElementById('product-grid');
  const empty = document.getElementById('empty-state');
  const searchTerm = (document.getElementById('search-input').value || '').toLowerCase().trim();

  let filtered = allProducts.filter(p => {
    const matchesCategory = activeCategories.has('all') || activeCategories.has(p.categoryName);
    const matchesPrice = p.priceCents <= maxPriceCents;
    const matchesSearch = !searchTerm || p.name.toLowerCase().includes(searchTerm);
    return matchesCategory && matchesPrice && matchesSearch;
  });

  if (sortMode === 'price-asc') filtered = [...filtered].sort((a, b) => a.priceCents - b.priceCents);
  if (sortMode === 'price-desc') filtered = [...filtered].sort((a, b) => b.priceCents - a.priceCents);

  if (filtered.length === 0) {
    grid.classList.add('hidden');
    empty.classList.remove('hidden');
    empty.classList.add('flex');
    return;
  }

  grid.classList.remove('hidden');
  empty.classList.add('hidden');
  empty.classList.remove('flex');
  grid.innerHTML = filtered.map(productCardHtml).join('');
}

async function quickAddToCart(productId, name) {
  if (!Auth.isLoggedIn()) {
    window.location.href = 'login.html';
    return;
  }
  try {
    const existing = await Api.getCart();
    const current = existing.find(i => i.productId === productId);
    await Api.addOrUpdateCartItem(productId, (current?.quantity || 0) + 1);
    showToast(`Added ${name} to cart`, 'success');
    refreshCartCount();
  } catch (err) {
    showToast(err.message || 'Could not add to cart', 'error');
  }
}

function resetFilters() {
  document.querySelectorAll('.category-checkbox').forEach(cb => cb.checked = cb.value === 'all');
  activeCategories = new Set(['all']);
  document.getElementById('search-input').value = '';
  document.getElementById('price-range').value = 500000;
  maxPriceCents = 500000 * 100;
  document.getElementById('price-range-label').textContent = 'Ksh 500k';
  document.getElementById('sort-select').value = 'featured';
  sortMode = 'featured';
  renderGrid();
}

async function initCatalog() {
  document.getElementById('search-input').addEventListener('input', renderGrid);

  document.querySelector('.category-checkbox[value="all"]').addEventListener('change', handleCategoryChange);

  document.getElementById('price-range').addEventListener('input', (e) => {
    const ksh = Number(e.target.value);
    maxPriceCents = ksh * 100;
    document.getElementById('price-range-label').textContent =
      ksh >= 500000 ? 'Ksh 500k' : `Ksh ${ksh.toLocaleString()}`;
    renderGrid();
  });

  document.getElementById('sort-select').addEventListener('change', (e) => {
    sortMode = e.target.value;
    renderGrid();
  });

  try {
    const page = await Api.listProducts(0, 50);
    allProducts = page.content || page;
    buildCategoryFilters();
    renderGrid();
  } catch (err) {
    document.getElementById('product-grid').innerHTML =
      `<p class="col-span-full text-center text-on-secondary-container py-10">Couldn't load products. Is the backend running?</p>`;
  }
}

document.addEventListener('DOMContentLoaded', initCatalog);
