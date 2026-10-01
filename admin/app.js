// ===== Firebase Config =====
const firebaseConfig = {
    apiKey: "AIzaSyBhtIalATkDydVD0aiYbR9ngr0MTt-6I6k",
    authDomain: "qless-1ccb9.firebaseapp.com",
    projectId: "qless-1ccb9",
    storageBucket: "qless-1ccb9.firebasestorage.app",
    appId: "1:760900783466:android:8e710ebdc162ffdef26913"
};
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.firestore();
const storage = firebase.storage();

// ===== AUTH =====
function adminLogin() {
    const email = document.getElementById('loginEmail').value;
    const pwd = document.getElementById('loginPassword').value;
    document.getElementById('loginError').textContent = '';
    auth.signInWithEmailAndPassword(email, pwd)
        .catch(e => { document.getElementById('loginError').textContent = e.message; });
}
function adminLogout() { auth.signOut().then(() => location.reload()); }

// Only accounts with the `admin` custom claim get in; app shoppers share
// this Firebase project. Force a token refresh so a newly granted claim
// is picked up without waiting for the hourly token rotation.
auth.onAuthStateChanged(async user => {
    if (!user) return;
    try {
        const token = await user.getIdTokenResult(true);
        if (token.claims.admin === true) {
            showDashboard();
            return;
        }
        document.getElementById('loginError').textContent = 'This account does not have admin access.';
    } catch (e) {
        document.getElementById('loginError').textContent = e.message;
    }
    await auth.signOut();
});

function showDashboard() {
    document.getElementById('loginScreen').style.display = 'none';
    document.getElementById('dashboard').style.display = 'flex';
    document.getElementById('adminEmail').textContent = auth.currentUser.email;
    loadOverview();
}

// ===== MOBILE SIDEBAR TOGGLE =====
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.querySelector('.sidebar-overlay');
    sidebar.classList.toggle('open');
    overlay.classList.toggle('show');
}

// ===== PRODUCT SUGGESTIONS DATABASE =====
const productSuggestions = [
    // Tobacco
    { name: 'Marlboro Red', brand: 'Marlboro', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Marlboro Gold', brand: 'Marlboro', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Marlboro Silver', brand: 'Marlboro', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Marlboro Ice Blast', brand: 'Marlboro', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Camel Blue', brand: 'Camel', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Camel Yellow', brand: 'Camel', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Winston Red', brand: 'Winston', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Winston Blue', brand: 'Winston', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Lucky Strike Original', brand: 'Lucky Strike', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Parliament Night Blue', brand: 'Parliament', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Kent Silver', brand: 'Kent', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'Davidoff Classic', brand: 'Davidoff', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'IQOS HEETS Amber', brand: 'IQOS', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'IQOS HEETS Yellow', brand: 'IQOS', category: 'Tobacco', icon: 'fa-smoking' },
    { name: 'IQOS HEETS Turquoise', brand: 'IQOS', category: 'Tobacco', icon: 'fa-smoking' },

    // Beverages
    { name: 'Coca-Cola Original', brand: 'Coca-Cola', category: 'Beverages', icon: 'fa-glass-water' },
    { name: 'Coca-Cola Zero', brand: 'Coca-Cola', category: 'Beverages', icon: 'fa-glass-water' },
    { name: 'Pepsi Max', brand: 'Pepsi', category: 'Beverages', icon: 'fa-glass-water' },
    { name: 'Pepsi Original', brand: 'Pepsi', category: 'Beverages', icon: 'fa-glass-water' },
    { name: 'Red Bull Energy Drink', brand: 'Red Bull', category: 'Beverages', icon: 'fa-bolt' },
    { name: 'Red Bull Sugar Free', brand: 'Red Bull', category: 'Beverages', icon: 'fa-bolt' },
    { name: 'Monster Energy Green', brand: 'Monster', category: 'Beverages', icon: 'fa-bolt' },
    { name: 'Monster Energy Zero', brand: 'Monster', category: 'Beverages', icon: 'fa-bolt' },
    { name: 'Sprite Original', brand: 'Sprite', category: 'Beverages', icon: 'fa-glass-water' },
    { name: 'Fanta Orange', brand: 'Fanta', category: 'Beverages', icon: 'fa-glass-water' },
    { name: '7UP Original', brand: '7UP', category: 'Beverages', icon: 'fa-glass-water' },
    { name: 'Evian Natural Water', brand: 'Evian', category: 'Beverages', icon: 'fa-droplet' },
    { name: 'Perrier Sparkling Water', brand: 'Perrier', category: 'Beverages', icon: 'fa-droplet' },
    { name: 'Heineken Beer', brand: 'Heineken', category: 'Beverages', icon: 'fa-beer-mug-empty' },
    { name: 'Corona Extra', brand: 'Corona', category: 'Beverages', icon: 'fa-beer-mug-empty' },
    { name: 'Budweiser', brand: 'Budweiser', category: 'Beverages', icon: 'fa-beer-mug-empty' },
    { name: 'Nescafe Classic', brand: 'Nescafe', category: 'Beverages', icon: 'fa-mug-hot' },
    { name: 'Nescafe Gold', brand: 'Nescafe', category: 'Beverages', icon: 'fa-mug-hot' },
    { name: 'Lipton Ice Tea Lemon', brand: 'Lipton', category: 'Beverages', icon: 'fa-glass-water' },

    // Snacks
    { name: 'Lay\'s Classic', brand: 'Lay\'s', category: 'Snacks', icon: 'fa-cookie' },
    { name: 'Lay\'s Sour Cream & Onion', brand: 'Lay\'s', category: 'Snacks', icon: 'fa-cookie' },
    { name: 'Pringles Original', brand: 'Pringles', category: 'Snacks', icon: 'fa-cookie' },
    { name: 'Pringles Sour Cream', brand: 'Pringles', category: 'Snacks', icon: 'fa-cookie' },
    { name: 'Doritos Nacho Cheese', brand: 'Doritos', category: 'Snacks', icon: 'fa-cookie' },
    { name: 'Doritos Cool Ranch', brand: 'Doritos', category: 'Snacks', icon: 'fa-cookie' },
    { name: 'Oreo Original', brand: 'Oreo', category: 'Snacks', icon: 'fa-cookie-bite' },
    { name: 'Oreo Golden', brand: 'Oreo', category: 'Snacks', icon: 'fa-cookie-bite' },
    { name: 'KitKat Original', brand: 'KitKat', category: 'Snacks', icon: 'fa-candy-cane' },
    { name: 'Snickers Bar', brand: 'Snickers', category: 'Snacks', icon: 'fa-candy-cane' },
    { name: 'Mars Bar', brand: 'Mars', category: 'Snacks', icon: 'fa-candy-cane' },
    { name: 'Twix Original', brand: 'Twix', category: 'Snacks', icon: 'fa-candy-cane' },
    { name: 'M&M\'s Peanut', brand: 'M&M\'s', category: 'Snacks', icon: 'fa-candy-cane' },
    { name: 'Nutella Spread', brand: 'Nutella', category: 'Snacks', icon: 'fa-jar' },

    // Electronics
    { name: 'iPhone 15 Pro', brand: 'Apple', category: 'Electronics', icon: 'fa-mobile-screen' },
    { name: 'iPhone 15', brand: 'Apple', category: 'Electronics', icon: 'fa-mobile-screen' },
    { name: 'AirPods Pro', brand: 'Apple', category: 'Electronics', icon: 'fa-headphones' },
    { name: 'MacBook Air M3', brand: 'Apple', category: 'Electronics', icon: 'fa-laptop' },
    { name: 'Samsung Galaxy S24', brand: 'Samsung', category: 'Electronics', icon: 'fa-mobile-screen' },
    { name: 'Samsung Galaxy Buds', brand: 'Samsung', category: 'Electronics', icon: 'fa-headphones' },
    { name: 'Sony WH-1000XM5', brand: 'Sony', category: 'Electronics', icon: 'fa-headphones' },
    { name: 'PlayStation 5', brand: 'Sony', category: 'Electronics', icon: 'fa-gamepad' },
    { name: 'Xbox Series X', brand: 'Microsoft', category: 'Electronics', icon: 'fa-gamepad' },
    { name: 'Nintendo Switch OLED', brand: 'Nintendo', category: 'Electronics', icon: 'fa-gamepad' },

    // Groceries
    { name: 'Basmati Rice 5kg', brand: 'Generic', category: 'Groceries', icon: 'fa-bowl-rice' },
    { name: 'Olive Oil Extra Virgin', brand: 'Generic', category: 'Groceries', icon: 'fa-oil-can' },
    { name: 'Barilla Spaghetti', brand: 'Barilla', category: 'Groceries', icon: 'fa-utensils' },
    { name: 'Barilla Penne', brand: 'Barilla', category: 'Groceries', icon: 'fa-utensils' },
    { name: 'Kellogg\'s Corn Flakes', brand: 'Kellogg\'s', category: 'Groceries', icon: 'fa-bowl-food' },
    { name: 'Kellogg\'s Special K', brand: 'Kellogg\'s', category: 'Groceries', icon: 'fa-bowl-food' },
    { name: 'Heinz Ketchup', brand: 'Heinz', category: 'Groceries', icon: 'fa-bottle-droplet' },
    { name: 'Hellmann\'s Mayonnaise', brand: 'Hellmann\'s', category: 'Groceries', icon: 'fa-jar' },

    // Dairy
    { name: 'Whole Milk 1L', brand: 'Generic', category: 'Dairy', icon: 'fa-cow' },
    { name: 'Greek Yogurt', brand: 'Generic', category: 'Dairy', icon: 'fa-cheese' },
    { name: 'Philadelphia Cream Cheese', brand: 'Philadelphia', category: 'Dairy', icon: 'fa-cheese' },
    { name: 'Danone Activia', brand: 'Danone', category: 'Dairy', icon: 'fa-cheese' },

    // Health & Personal Care
    { name: 'Panadol Extra', brand: 'Panadol', category: 'Health', icon: 'fa-pills' },
    { name: 'Advil Ibuprofen', brand: 'Advil', category: 'Health', icon: 'fa-pills' },
    { name: 'Vitamin D3 1000IU', brand: 'Generic', category: 'Health', icon: 'fa-capsules' },
    { name: 'Colgate Total Toothpaste', brand: 'Colgate', category: 'Personal Care', icon: 'fa-tooth' },
    { name: 'Oral-B Toothbrush', brand: 'Oral-B', category: 'Personal Care', icon: 'fa-tooth' },
    { name: 'Dove Soap Bar', brand: 'Dove', category: 'Personal Care', icon: 'fa-soap' },
    { name: 'Nivea Body Lotion', brand: 'Nivea', category: 'Personal Care', icon: 'fa-pump-soap' },
    { name: 'L\'Oreal Shampoo', brand: 'L\'Oreal', category: 'Personal Care', icon: 'fa-pump-soap' },
    { name: 'Gillette Fusion Razor', brand: 'Gillette', category: 'Personal Care', icon: 'fa-scissors' },

    // Household
    { name: 'Tide Laundry Detergent', brand: 'Tide', category: 'Household', icon: 'fa-jug-detergent' },
    { name: 'Fairy Dish Soap', brand: 'Fairy', category: 'Household', icon: 'fa-soap' },
    { name: 'Bounty Paper Towels', brand: 'Bounty', category: 'Household', icon: 'fa-toilet-paper' },
    { name: 'Clorox Bleach', brand: 'Clorox', category: 'Household', icon: 'fa-spray-can-sparkles' },
];

// Brand to category mapping for auto-detection
const brandCategoryMap = {
    'marlboro': 'Tobacco', 'camel': 'Tobacco', 'winston': 'Tobacco', 'lucky strike': 'Tobacco',
    'parliament': 'Tobacco', 'kent': 'Tobacco', 'davidoff': 'Tobacco', 'iqos': 'Tobacco',
    'coca-cola': 'Beverages', 'pepsi': 'Beverages', 'red bull': 'Beverages', 'monster': 'Beverages',
    'sprite': 'Beverages', 'fanta': 'Beverages', '7up': 'Beverages', 'evian': 'Beverages',
    'perrier': 'Beverages', 'heineken': 'Beverages', 'corona': 'Beverages', 'budweiser': 'Beverages',
    'nescafe': 'Beverages', 'lipton': 'Beverages', 'starbucks': 'Beverages',
    'lay\'s': 'Snacks', 'lays': 'Snacks', 'pringles': 'Snacks', 'doritos': 'Snacks',
    'oreo': 'Snacks', 'kitkat': 'Snacks', 'snickers': 'Snacks', 'mars': 'Snacks',
    'twix': 'Snacks', 'm&m': 'Snacks', 'nutella': 'Snacks', 'cadbury': 'Snacks',
    'apple': 'Electronics', 'samsung': 'Electronics', 'sony': 'Electronics', 'microsoft': 'Electronics',
    'nintendo': 'Electronics', 'lg': 'Electronics', 'dell': 'Electronics', 'hp': 'Electronics',
    'barilla': 'Groceries', 'kellogg\'s': 'Groceries', 'heinz': 'Groceries', 'hellmann\'s': 'Groceries',
    'philadelphia': 'Dairy', 'danone': 'Dairy',
    'panadol': 'Health', 'advil': 'Health', 'tylenol': 'Health',
    'colgate': 'Personal Care', 'oral-b': 'Personal Care', 'dove': 'Personal Care',
    'nivea': 'Personal Care', 'l\'oreal': 'Personal Care', 'gillette': 'Personal Care',
    'tide': 'Household', 'fairy': 'Household', 'bounty': 'Household', 'clorox': 'Household',
};

// Initialize product name autocomplete
document.addEventListener('DOMContentLoaded', () => {
    const prodNameInput = document.getElementById('prodName');
    const dropdown = document.getElementById('productNameDropdown');

    if (prodNameInput && dropdown) {
        let debounceTimer;

        prodNameInput.addEventListener('input', function() {
            clearTimeout(debounceTimer);
            const query = this.value.trim();

            if (query.length < 2) {
                dropdown.classList.remove('show');
                return;
            }

            debounceTimer = setTimeout(() => {
                showProductSuggestions(query);
            }, 150);
        });

        prodNameInput.addEventListener('focus', function() {
            if (this.value.trim().length >= 2) {
                showProductSuggestions(this.value.trim());
            }
        });

        prodNameInput.addEventListener('blur', () => {
            setTimeout(() => dropdown.classList.remove('show'), 200);
        });

        // Also detect brand from manual typing
        prodNameInput.addEventListener('input', function() {
            detectBrandFromInput(this.value);
        });
    }
});

function showProductSuggestions(query) {
    const dropdown = document.getElementById('productNameDropdown');
    const lowerQuery = query.toLowerCase();

    // Filter suggestions
    const filtered = productSuggestions.filter(p =>
        p.name.toLowerCase().includes(lowerQuery) ||
        p.brand.toLowerCase().includes(lowerQuery)
    ).slice(0, 8);

    dropdown.innerHTML = '';

    if (filtered.length === 0) {
        dropdown.innerHTML = '<div class="product-suggestion no-results">No suggestions found. You can enter a custom product.</div>';
        dropdown.classList.add('show');
        return;
    }

    filtered.forEach(product => {
        const item = document.createElement('div');
        item.className = 'product-suggestion';
        const iconClass = getCategoryIconClass(product.category);
        item.innerHTML = `
            <div class="suggestion-icon ${iconClass}">
                <i class="fas ${product.icon || 'fa-box'}"></i>
            </div>
            <div class="suggestion-info">
                <div class="suggestion-name">${highlightMatch(product.name, query)}</div>
                <div class="suggestion-meta">
                    <span class="suggestion-brand"><i class="fas fa-tag"></i> ${product.brand}</span>
                    <span class="suggestion-category"><i class="fas fa-folder"></i> ${product.category}</span>
                </div>
            </div>
        `;
        item.addEventListener('click', () => selectProductSuggestion(product));
        dropdown.appendChild(item);
    });

    dropdown.classList.add('show');
}

function getCategoryIconClass(category) {
    const map = {
        'Tobacco': 'tobacco',
        'Beverages': 'beverages',
        'Snacks': 'snacks',
        'Electronics': 'electronics',
        'Groceries': 'groceries',
        'Health': 'health',
        'Dairy': 'dairy',
        'Personal Care': 'health',
        'Household': 'default'
    };
    return map[category] || 'default';
}

function highlightMatch(text, query) {
    const regex = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
    return text.replace(regex, '<strong style="color:#4F52B8">$1</strong>');
}

function selectProductSuggestion(product) {
    document.getElementById('prodName').value = product.name;
    document.getElementById('prodBrand').value = product.brand;

    // Set category
    const categorySelect = document.getElementById('prodCategory');
    const categoryOptions = Array.from(categorySelect.options);
    const matchingOption = categoryOptions.find(opt =>
        opt.value.toLowerCase() === product.category.toLowerCase() ||
        opt.text.toLowerCase() === product.category.toLowerCase()
    );

    if (matchingOption) {
        categorySelect.value = matchingOption.value;
    }

    document.getElementById('productNameDropdown').classList.remove('show');

    // Focus on next field
    document.getElementById('prodSku').focus();
}

function detectBrandFromInput(input) {
    const lowerInput = input.toLowerCase();

    // Try to detect brand from the input
    for (const [brand, category] of Object.entries(brandCategoryMap)) {
        if (lowerInput.includes(brand)) {
            // Auto-fill brand if empty
            const brandInput = document.getElementById('prodBrand');
            if (!brandInput.value) {
                // Capitalize brand properly
                const properBrand = brand.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
                brandInput.value = properBrand;
            }

            // Auto-set category
            const categorySelect = document.getElementById('prodCategory');
            if (!categorySelect.value) {
                const categoryOptions = Array.from(categorySelect.options);
                const matchingOption = categoryOptions.find(opt =>
                    opt.value.toLowerCase() === category.toLowerCase() ||
                    opt.text.toLowerCase() === category.toLowerCase()
                );
                if (matchingOption) {
                    categorySelect.value = matchingOption.value;
                }
            }
            break;
        }
    }
}

// ===== NAVIGATION =====
function showPage(page) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.getElementById('page-' + page).classList.add('active');
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    event.currentTarget.classList.add('active');
    const titles = { overview:'Overview', products:'Products', stores:'Stores', orders:'Orders & Reservations', users:'Users', analytics:'ML Analytics' };
    document.getElementById('pageTitle').textContent = titles[page] || page;
    if (page === 'overview') loadOverview();
    if (page === 'products') loadProducts();
    if (page === 'stores') loadStores();
    if (page === 'orders') loadOrders();
    if (page === 'users') loadUsers();
    if (page === 'analytics') loadAnalytics();
}

// ===== TOAST =====
function toast(msg) {
    const t = document.getElementById('toast');
    t.textContent = msg; t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 3000);
}

// ===== OVERVIEW =====
function loadOverview() {
    db.collection('products').get().then(s => document.getElementById('statProducts').textContent = s.size);
    db.collection('stores').get().then(s => document.getElementById('statStores').textContent = s.size);
    db.collection('users').get().then(s => document.getElementById('statUsers').textContent = s.size);

    // Count both orders and reservations
    Promise.all([
        db.collection('orders').get(),
        db.collection('reservations').get()
    ]).then(([ordersSnap, reservationsSnap]) => {
        const totalOrders = ordersSnap.size + reservationsSnap.size;
        document.getElementById('statOrders').textContent = totalOrders;
    });

    // Recent events
    db.collection('user_events').orderBy('timestamp', 'desc').limit(20).get().then(snap => {
        const tbody = document.querySelector('#recentEventsTable tbody');
        tbody.innerHTML = '';
        snap.forEach(doc => {
            const d = doc.data();
            const time = d.timestamp ? new Date(d.timestamp).toLocaleString() : '—';
            tbody.innerHTML += `<tr><td>${d.userId?.substring(0,8) || '—'}...</td><td><span class="badge badge-${eventColor(d.eventType)}">${d.eventType}</span></td><td>${d.productId?.substring(0,8) || d.query || '—'}</td><td>${d.category || '—'}</td><td>${time}</td></tr>`;
        });
        if (snap.empty) tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#6E6E78">No events yet. Use the app to generate ML data.</td></tr>';
    });
}
function eventColor(type) {
    if (type === 'view') return 'blue';
    if (type === 'add_to_cart') return 'green';
    if (type === 'purchase') return 'green';
    if (type === 'search') return 'yellow';
    return 'blue';
}

// ===== PRODUCTS =====
let allProducts = []; // Cache all products for filtering
let currentCategoryFilter = 'all';

function loadProducts() {
    db.collection('products').get().then(snap => {
        allProducts = [];
        snap.forEach(doc => {
            allProducts.push({ id: doc.id, ...doc.data() });
        });
        renderProducts(allProducts);
        updateCategoryChipCounts();
    });
}

function renderProducts(products) {
    const tbody = document.querySelector('#productsTable tbody');
    tbody.innerHTML = '';

    if (products.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#6E6E78;padding:40px">No products found in this category</td></tr>';
        return;
    }

    products.forEach(p => {
        const prices = p.prices || [];
        const best = prices.length ? Math.min(...prices.map(x => x.price)) : 0;
        const img = p.imageUrl ? `<img src="${p.imageUrl}" alt="">` : '<div style="width:44px;height:44px;background:#F0EFEB;border-radius:8px"></div>';
        tbody.innerHTML += `<tr data-category="${p.category || ''}">
            <td>${img}</td>
            <td><strong>${p.name || ''}</strong></td>
            <td><span class="badge badge-category">${p.category || ''}</span></td>
            <td>${p.brand || ''}</td>
            <td>${prices.length}</td>
            <td><strong>€${best.toFixed(2)}</strong></td>
            <td>
                <button class="btn btn-sm" onclick="editProduct('${p.id}')"><i class="fas fa-edit"></i></button>
                <button class="btn btn-danger btn-sm" onclick="deleteProduct('${p.id}')"><i class="fas fa-trash"></i></button>
            </td>
        </tr>`;
    });
}

function filterByCategory(category) {
    currentCategoryFilter = category;

    // Update active chip
    document.querySelectorAll('.category-chip').forEach(chip => {
        chip.classList.remove('active');
        if (chip.dataset.category === category) {
            chip.classList.add('active');
        }
    });

    // Filter and render products
    if (category === 'all') {
        renderProducts(allProducts);
    } else {
        const filtered = allProducts.filter(p =>
            p.category && p.category.toLowerCase() === category.toLowerCase()
        );
        renderProducts(filtered);
    }

    // Update page title
    const pageTitle = document.getElementById('pageTitle');
    if (category === 'all') {
        pageTitle.textContent = 'Products';
    } else {
        pageTitle.textContent = `Products - ${category}`;
    }
}

function scrollCategorySlider(direction) {
    const slider = document.getElementById('categorySlider');
    const scrollAmount = 200;
    slider.scrollBy({ left: direction * scrollAmount, behavior: 'smooth' });
}

function updateCategoryChipCounts() {
    // Count products per category
    const counts = { all: allProducts.length };
    allProducts.forEach(p => {
        if (p.category) {
            counts[p.category] = (counts[p.category] || 0) + 1;
        }
    });

    // Update chip labels with counts
    document.querySelectorAll('.category-chip').forEach(chip => {
        const category = chip.dataset.category;
        const count = counts[category] || 0;
        const span = chip.querySelector('span');
        const baseName = span.textContent.split(' (')[0];
        if (count > 0 || category === 'all') {
            span.textContent = `${baseName} (${count})`;
        }
    });
}

async function openProductModal(id) {
    // Load stores for dropdown
    await loadStoresForDropdown();

    document.getElementById('productModalTitle').textContent = id ? 'Edit Product' : 'Add Product';
    document.getElementById('productId').value = '';
    document.getElementById('prodName').value = '';
    document.getElementById('prodBrand').value = '';
    document.getElementById('prodCategory').value = '';
    document.getElementById('prodSku').value = '';
    document.getElementById('prodDescription').value = '';
    document.getElementById('prodImageUrl').value = '';
    document.getElementById('prodImageFile').value = '';
    document.getElementById('imageFileName').textContent = '';
    document.getElementById('prodRating').value = '';
    document.getElementById('prodReviews').value = '';
    document.getElementById('priceRows').innerHTML = '';
    document.getElementById('imagePreview').innerHTML = '';
    document.getElementById('imageUploadProgress').style.display = 'none';
    document.getElementById('productNameDropdown').classList.remove('show');
    selectedImageFile = null;
    addPriceRow();
    document.getElementById('productModal').style.display = 'flex';
}
function closeProductModal() { document.getElementById('productModal').style.display = 'none'; }

async function editProduct(id) {
    await loadStoresForDropdown();

    db.collection('products').doc(id).get().then(doc => {
        const p = doc.data();
        document.getElementById('productModalTitle').textContent = 'Edit Product';
        document.getElementById('productId').value = id;
        document.getElementById('prodName').value = p.name || '';
        document.getElementById('prodBrand').value = p.brand || '';
        document.getElementById('prodCategory').value = p.category || '';
        document.getElementById('prodSku').value = p.sku || '';
        document.getElementById('prodDescription').value = p.description || '';
        document.getElementById('prodImageUrl').value = p.imageUrl || '';
        document.getElementById('prodImageFile').value = '';
        document.getElementById('imageFileName').textContent = '';
        document.getElementById('prodRating').value = p.rating || '';
        document.getElementById('prodReviews').value = p.reviews || '';
        document.getElementById('imageUploadProgress').style.display = 'none';
        selectedImageFile = null;
        if (p.imageUrl) {
            previewImage(p.imageUrl);
            document.getElementById('imageFileName').textContent = 'Current image';
        }
        document.getElementById('priceRows').innerHTML = '';
        (p.prices || []).forEach(pr => addPriceRow(pr));
        document.getElementById('productModal').style.display = 'flex';
    });
}

// ===== STORE CACHE FOR DROPDOWNS =====
let cachedStores = [];

async function loadStoresForDropdown() {
    const snap = await db.collection('stores').get();
    cachedStores = [];
    snap.forEach(doc => {
        const s = doc.data();
        cachedStores.push({ id: doc.id, name: s.name, category: s.category || '' });
    });
    return cachedStores;
}

function addPriceRow(data) {
    const div = document.createElement('div');
    div.className = 'price-row';
    const rowId = 'store-select-' + Date.now() + Math.random().toString(36).substr(2, 5);
    div.innerHTML = `
        <div class="price-row-store">
            <label><i class="fas fa-store"></i> Store</label>
            <div class="store-select-wrapper">
                <input type="text" class="store-search-input" id="${rowId}" placeholder="Search or select a store..." value="${data?.storeName || ''}" autocomplete="off">
                <input type="hidden" class="store-id-input" value="${data?.storeId || ''}">
                <div class="store-dropdown"></div>
            </div>
        </div>
        <div class="price-row-fields">
            <div class="price-row-field">
                <label>Price (€)</label>
                <input type="number" step="0.01" placeholder="0.00" value="${data?.price || ''}">
            </div>
            <div class="price-row-field">
                <label>Original (€)</label>
                <input type="number" step="0.01" placeholder="0.00" value="${data?.originalPrice || ''}">
            </div>
            <div class="price-row-field">
                <label>Quantity</label>
                <input type="number" placeholder="0" value="${data?.quantity || ''}">
            </div>
            <button class="btn-remove" title="Remove this store" onclick="this.closest('.price-row').remove()"><i class="fas fa-trash-alt"></i></button>
        </div>`;
    document.getElementById('priceRows').appendChild(div);

    // Setup store search/dropdown
    const input = div.querySelector('.store-search-input');
    const dropdown = div.querySelector('.store-dropdown');
    const hiddenInput = div.querySelector('.store-id-input');

    input.addEventListener('focus', () => showStoreDropdown(input, dropdown, hiddenInput));
    input.addEventListener('input', () => filterStoreDropdown(input, dropdown, hiddenInput));
    input.addEventListener('blur', () => setTimeout(() => dropdown.classList.remove('show'), 200));
}

function showStoreDropdown(input, dropdown, hiddenInput) {
    renderStoreOptions(dropdown, input, hiddenInput, cachedStores);
    dropdown.classList.add('show');
}

function filterStoreDropdown(input, dropdown, hiddenInput) {
    const query = input.value.toLowerCase().trim();
    const filtered = cachedStores.filter(s =>
        s.name.toLowerCase().includes(query) ||
        s.category.toLowerCase().includes(query)
    );
    renderStoreOptions(dropdown, input, hiddenInput, filtered);
    dropdown.classList.add('show');
}

function renderStoreOptions(dropdown, input, hiddenInput, stores) {
    dropdown.innerHTML = '';
    if (stores.length === 0) {
        dropdown.innerHTML = '<div class="store-dropdown-item no-results">No stores found</div>';
        return;
    }
    stores.forEach(store => {
        const item = document.createElement('div');
        item.className = 'store-dropdown-item';
        item.innerHTML = `
            <i class="fas fa-store"></i>
            <div>
                <div class="store-name">${store.name}</div>
                ${store.category ? `<div class="store-category">${store.category}</div>` : ''}
            </div>
        `;
        item.addEventListener('click', () => {
            input.value = store.name;
            hiddenInput.value = store.id;
            dropdown.classList.remove('show');
        });
        dropdown.appendChild(item);
    });
}

function previewImage(url) {
    const prev = document.getElementById('imagePreview');
    if (url) prev.innerHTML = `<img src="${url}" onerror="this.style.display='none'">`;
    else prev.innerHTML = '';
}

// ===== IMAGE UPLOAD =====
let selectedImageFile = null;

function handleImageSelect(input) {
    const file = input.files[0];
    if (file) {
        selectedImageFile = file;
        document.getElementById('imageFileName').textContent = file.name;

        // Show preview
        const reader = new FileReader();
        reader.onload = function(e) {
            previewImage(e.target.result);
        };
        reader.readAsDataURL(file);
    }
}

function uploadImageToFirebase(file) {
    return new Promise((resolve, reject) => {
        const fileName = `products/${Date.now()}_${file.name.replace(/[^a-zA-Z0-9.]/g, '_')}`;
        const storageRef = storage.ref(fileName);
        const uploadTask = storageRef.put(file);

        const progressDiv = document.getElementById('imageUploadProgress');
        const progressFill = document.getElementById('uploadProgressFill');
        const progressText = document.getElementById('uploadProgressText');
        progressDiv.style.display = 'block';

        uploadTask.on('state_changed',
            (snapshot) => {
                const progress = (snapshot.bytesTransferred / snapshot.totalBytes) * 100;
                progressFill.style.width = progress + '%';
                progressText.textContent = `Uploading... ${Math.round(progress)}%`;
            },
            (error) => {
                progressDiv.style.display = 'none';
                reject(error);
            },
            () => {
                uploadTask.snapshot.ref.getDownloadURL().then((downloadURL) => {
                    progressText.textContent = 'Upload complete!';
                    setTimeout(() => { progressDiv.style.display = 'none'; }, 1000);
                    resolve(downloadURL);
                });
            }
        );
    });
}

async function saveProduct() {
    // Check if user is authenticated
    if (!auth.currentUser) {
        toast('Error: You must be logged in to add products');
        return;
    }

    const id = document.getElementById('productId').value;
    const name = document.getElementById('prodName').value.trim();
    const category = document.getElementById('prodCategory').value;
    if (!name || !category) { toast('Name and Category required!'); return; }

    const prices = [];
    document.querySelectorAll('#priceRows .price-row').forEach(row => {
        const storeNameInput = row.querySelector('.store-search-input');
        const storeIdInput = row.querySelector('.store-id-input');
        const inputs = row.querySelectorAll('input[type="number"]');

        if (storeNameInput && storeNameInput.value.trim()) {
            prices.push({
                storeId: storeIdInput ? storeIdInput.value : '',
                storeName: storeNameInput.value.trim(),
                price: parseFloat(inputs[0].value) || 0,
                originalPrice: parseFloat(inputs[1].value) || 0,
                quantity: parseInt(inputs[2].value) || 0,
                inStock: (parseInt(inputs[2].value) || 0) > 0
            });
        }
    });

    // Handle image upload
    let imageUrl = document.getElementById('prodImageUrl').value.trim();

    if (selectedImageFile) {
        try {
            toast('Uploading image...');
            imageUrl = await uploadImageToFirebase(selectedImageFile);
        } catch (error) {
            toast('Error uploading image: ' + error.message);
            return;
        }
    }

    const data = {
        name, category,
        brand: document.getElementById('prodBrand').value.trim(),
        sku: document.getElementById('prodSku').value.trim(),
        description: document.getElementById('prodDescription').value.trim(),
        imageUrl: imageUrl,
        rating: parseFloat(document.getElementById('prodRating').value) || 0,
        reviews: parseInt(document.getElementById('prodReviews').value) || 0,
        prices,
        updatedAt: Date.now(),
        updatedBy: auth.currentUser.uid
    };

    const promise = id ? db.collection('products').doc(id).update(data) : db.collection('products').add({ ...data, createdAt: Date.now(), createdBy: auth.currentUser.uid });
    promise.then(() => {
        toast(id ? 'Product updated!' : 'Product added!');
        selectedImageFile = null;
        closeProductModal();
        loadProducts();
    }).catch(e => {
        console.error('Firestore error:', e);
        if (e.code === 'permission-denied') {
            toast('Permission denied! Please update Firestore rules. See console for details.');
            console.log('To fix this, go to Firebase Console > Firestore > Rules and update the rules. See FIREBASE_SETUP.md for the correct rules.');
        } else {
            toast('Error: ' + e.message);
        }
    });
}

function deleteProduct(id) {
    if (!confirm('Delete this product?')) return;
    db.collection('products').doc(id).delete().then(() => { toast('Product deleted'); loadProducts(); });
}

// ===== STORES =====
function loadStores() {
    db.collection('stores').get().then(snap => {
        const tbody = document.querySelector('#storesTable tbody');
        tbody.innerHTML = '';
        snap.forEach(doc => {
            const s = doc.data();
            const addr = s.address || {};
            const displayAddr = addr.formattedAddress ? addr.formattedAddress.substring(0, 40) + (addr.formattedAddress.length > 40 ? '...' : '') : '—';
            tbody.innerHTML += `<tr>
                <td><strong>${s.name || ''}</strong></td>
                <td>${s.category || ''}</td>
                <td>${s.phone || ''}</td>
                <td>⭐ ${s.rating || 0}</td>
                <td title="${addr.formattedAddress || ''}">${displayAddr}</td>
                <td>
                    <button class="btn btn-sm" onclick="editStore('${doc.id}')"><i class="fas fa-edit"></i></button>
                    <button class="btn btn-danger btn-sm" onclick="deleteStore('${doc.id}')"><i class="fas fa-trash"></i></button>
                </td>
            </tr>`;
        });
    });
}

function openStoreModal() {
    document.getElementById('storeModalTitle').textContent = 'Add Store';
    document.getElementById('storeId').value = '';
    document.getElementById('storeName').value = '';
    document.getElementById('storeCategory').value = '';
    document.getElementById('storePhone').value = '';
    document.getElementById('storeRating').value = '';
    document.getElementById('storeReviews').value = '';
    document.getElementById('storeLat').value = '';
    document.getElementById('storeLng').value = '';
    document.getElementById('storeAddress').value = '';
    hideAddressDropdown();
    document.getElementById('storeModal').style.display = 'flex';
}
function closeStoreModal() { document.getElementById('storeModal').style.display = 'none'; }

function editStore(id) {
    db.collection('stores').doc(id).get().then(doc => {
        const s = doc.data();
        openStoreModal();
        document.getElementById('storeId').value = id;
        document.getElementById('storeModalTitle').textContent = 'Edit Store';
        document.getElementById('storeName').value = s.name || '';
        document.getElementById('storeCategory').value = s.category || '';
        document.getElementById('storePhone').value = s.phone || '';
        document.getElementById('storeRating').value = s.rating || '';
        document.getElementById('storeReviews').value = s.reviews || '';
        const addr = s.address || {};
        document.getElementById('storeLat').value = addr.latitude || '';
        document.getElementById('storeLng').value = addr.longitude || '';
        document.getElementById('storeAddress').value = addr.formattedAddress || '';
    });
}

function saveStore() {
    // Check if user is authenticated
    if (!auth.currentUser) {
        toast('Error: You must be logged in to add stores');
        return;
    }

    const id = document.getElementById('storeId').value;
    const name = document.getElementById('storeName').value.trim();
    const address = document.getElementById('storeAddress').value.trim();
    if (!name) { toast('Store name required!'); return; }
    if (!address) { toast('Store address required!'); return; }

    const data = {
        name, category: document.getElementById('storeCategory').value.trim(),
        phone: document.getElementById('storePhone').value.trim(),
        rating: parseFloat(document.getElementById('storeRating').value) || 0,
        reviews: parseInt(document.getElementById('storeReviews').value) || 0,
        address: {
            formattedAddress: address,
            latitude: parseFloat(document.getElementById('storeLat').value) || 0,
            longitude: parseFloat(document.getElementById('storeLng').value) || 0
        },
        createdBy: auth.currentUser.uid,
        updatedAt: firebase.firestore.FieldValue.serverTimestamp()
    };

    const promise = id ? db.collection('stores').doc(id).update(data) : db.collection('stores').add(data);
    promise.then(() => { toast(id ? 'Store updated!' : 'Store added!'); closeStoreModal(); loadStores(); })
           .catch(e => {
               console.error('Firestore error:', e);
               if (e.code === 'permission-denied') {
                   toast('Permission denied! Please update Firestore rules. See console for details.');
                   console.log('To fix this, go to Firebase Console > Firestore > Rules and update the rules. See FIREBASE_SETUP.md for the correct rules.');
               } else {
                   toast('Error: ' + e.message);
               }
           });
}

// ===== ADDRESS AUTOCOMPLETE =====
let addressDebounceTimer;
document.addEventListener('DOMContentLoaded', () => {
    const addressInput = document.getElementById('storeAddress');
    if (addressInput) {
        addressInput.addEventListener('input', function() {
            clearTimeout(addressDebounceTimer);
            const query = this.value.trim();
            if (query.length < 3) {
                hideAddressDropdown();
                return;
            }
            addressDebounceTimer = setTimeout(() => searchAddress(query), 300);
        });

        addressInput.addEventListener('blur', () => {
            setTimeout(hideAddressDropdown, 200);
        });
    }
});

function searchAddress(query) {
    const dropdown = document.getElementById('addressDropdown');
    dropdown.innerHTML = '<div class="address-loading"><i class="fas fa-spinner fa-spin"></i> Searching...</div>';
    dropdown.classList.add('show');

    // Using Nominatim (OpenStreetMap) for geocoding - free and no API key required
    fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=5&addressdetails=1`)
        .then(response => response.json())
        .then(results => {
            dropdown.innerHTML = '';
            if (results.length === 0) {
                dropdown.innerHTML = '<div class="address-loading">No results found</div>';
                return;
            }
            results.forEach(place => {
                const item = document.createElement('div');
                item.className = 'address-item';
                const mainText = place.display_name.split(',')[0];
                const secondaryText = place.display_name.split(',').slice(1).join(',').trim();
                item.innerHTML = `
                    <i class="fas fa-map-marker-alt"></i>
                    <div>
                        <div class="address-main">${mainText}</div>
                        <div class="address-secondary">${secondaryText}</div>
                    </div>
                `;
                item.addEventListener('click', () => selectAddress(place));
                dropdown.appendChild(item);
            });
        })
        .catch(err => {
            dropdown.innerHTML = '<div class="address-loading">Error searching address</div>';
            console.error('Address search error:', err);
        });
}

function selectAddress(place) {
    document.getElementById('storeAddress').value = place.display_name;
    document.getElementById('storeLat').value = place.lat;
    document.getElementById('storeLng').value = place.lon;
    hideAddressDropdown();
}

function hideAddressDropdown() {
    document.getElementById('addressDropdown').classList.remove('show');
}

function deleteStore(id) {
    if (!confirm('Delete this store?')) return;
    db.collection('stores').doc(id).delete().then(() => { toast('Store deleted'); loadStores(); });
}

// ===== ORDERS =====
function loadOrders() {
    const tbody = document.querySelector('#ordersTable tbody');
    tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#6E6E78;padding:20px"><i class="fas fa-spinner fa-spin"></i> Loading...</td></tr>';

    let allOrders = [];

    // Load orders
    const ordersPromise = db.collection('orders').get().then(snap => {
        snap.forEach(doc => {
            const o = doc.data();
            allOrders.push({
                id: doc.id,
                type: 'order',
                storeName: o.storeName || '—',
                total: o.totalAmount || 0,
                status: o.status || 'pending',
                paymentStatus: o.paymentStatus || 'unknown',
                date: o.orderDate || o.createdAt,
                userId: o.userId
            });
        });
    });

    // Load reservations (this is where app stores checkout data)
    const reservationsPromise = db.collection('reservations').get().then(snap => {
        snap.forEach(doc => {
            const r = doc.data();
            // Handle different field names - app uses total and createdAt
            const total = r.total || r.totalAmount || 0;
            const timestamp = r.createdAt || r.reservedAt;
            const storeName = r.storeName || (r.items && r.items[0]?.storeName) || '—';

            allOrders.push({
                id: doc.id,
                type: 'reservation',
                storeName: storeName,
                total: total,
                status: r.status || 'pending',
                paymentStatus: r.paymentStatus || 'unknown',
                date: timestamp,
                userId: r.userId,
                items: r.items
            });
        });
    });

    // Render after both load
    Promise.all([ordersPromise, reservationsPromise]).then(() => {
        tbody.innerHTML = '';

        if (allOrders.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#6E6E78;padding:40px"><i class="fas fa-inbox" style="font-size:24px;display:block;margin-bottom:12px"></i>No orders or reservations yet</td></tr>';
            return;
        }

        // Sort by date descending
        allOrders.sort((a, b) => (b.date || 0) - (a.date || 0));

        allOrders.forEach(order => {
            const date = order.date ? new Date(order.date).toLocaleDateString() : '—';

            // Status badge class
            let statusClass = 'badge-yellow';
            if (order.status === 'collected' || order.status === 'completed') statusClass = 'badge-green';
            else if (order.status === 'reserved') statusClass = 'badge-blue';
            else if (order.status === 'expired' || order.status === 'cancelled') statusClass = 'badge-red';
            else if (order.status === 'pending_payment') statusClass = 'badge-yellow';

            // Payment badge class
            let paymentClass = 'badge-yellow';
            if (order.paymentStatus === 'paid') paymentClass = 'badge-green';
            else if (order.paymentStatus === 'failed') paymentClass = 'badge-red';
            else if (order.paymentStatus === 'pending') paymentClass = 'badge-yellow';

            const isReservation = order.type === 'reservation';
            const actionButtons = isReservation ? `
                <button class="btn btn-sm" title="Mark as Collected" onclick="updateReservation('${order.id}','collected')">📦</button>
                <button class="btn btn-danger btn-sm" title="Mark as Expired" onclick="updateReservation('${order.id}','expired')">⏰</button>
                <button class="btn btn-sm" title="View Details" onclick="viewOrderDetails('${order.id}', 'reservation')"><i class="fas fa-eye"></i></button>
            ` : `
                <button class="btn btn-sm" title="Mark as Completed" onclick="updateOrderStatus('${order.id}','completed')">✅</button>
                <button class="btn btn-danger btn-sm" title="Cancel" onclick="updateOrderStatus('${order.id}','cancelled')">❌</button>
            `;

            tbody.innerHTML += `<tr>
                <td title="${order.id}">${order.id.substring(0,12)}...</td>
                <td><strong>${order.storeName}</strong></td>
                <td><strong>€${order.total.toFixed(2)}</strong></td>
                <td><span class="badge ${statusClass}">${order.status}</span></td>
                <td><span class="badge ${paymentClass}">${order.paymentStatus}</span></td>
                <td>${date}</td>
                <td>${actionButtons}</td>
            </tr>`;
        });
    });
}

function updateOrderStatus(id, status) {
    db.collection('orders').doc(id).update({ status }).then(() => { toast('Order ' + status); loadOrders(); });
}
function updateReservation(id, status) {
    db.collection('reservations').doc(id).update({ status }).then(() => { toast('Reservation ' + status); loadOrders(); });
}

function viewOrderDetails(id, type) {
    const collection = type === 'reservation' ? 'reservations' : 'orders';

    db.collection(collection).doc(id).get().then(doc => {
        if (!doc.exists) {
            toast('Order not found');
            return;
        }

        const data = doc.data();

        // Fill in details
        document.getElementById('orderDetailId').textContent = id;
        document.getElementById('orderDetailStore').textContent = data.storeName || (data.items && data.items[0]?.storeName) || '—';
        document.getElementById('orderDetailStatus').innerHTML = `<span class="badge ${getStatusClass(data.status)}">${data.status || 'pending'}</span>`;
        document.getElementById('orderDetailPayment').innerHTML = `<span class="badge ${getPaymentClass(data.paymentStatus)}">${data.paymentStatus || 'unknown'}</span>`;

        // Dates
        const createdDate = data.createdAt || data.reservedAt;
        document.getElementById('orderDetailDate').textContent = createdDate ? new Date(createdDate).toLocaleString() : '—';
        document.getElementById('orderDetailDeadline').textContent = data.pickupDeadline ? new Date(data.pickupDeadline).toLocaleString() : '—';

        // Items
        const itemsTbody = document.querySelector('#orderItemsTable tbody');
        itemsTbody.innerHTML = '';
        const items = data.items || [];
        items.forEach(item => {
            const itemTotal = (item.price || 0) * (item.quantity || 1);
            itemsTbody.innerHTML += `<tr>
                <td>${item.productName || item.name || '—'}</td>
                <td>${item.quantity || 1}</td>
                <td>€${itemTotal.toFixed(2)}</td>
            </tr>`;
        });

        if (items.length === 0) {
            itemsTbody.innerHTML = '<tr><td colspan="3" style="text-align:center;color:#6E6E78">No items</td></tr>';
        }

        // Totals
        document.getElementById('orderDetailSubtotal').textContent = `€${(data.subtotal || 0).toFixed(2)}`;
        document.getElementById('orderDetailFee').textContent = `€${(data.serviceFee || 0).toFixed(2)}`;
        document.getElementById('orderDetailTotal').textContent = `€${(data.total || data.totalAmount || 0).toFixed(2)}`;

        document.getElementById('orderModal').style.display = 'flex';
    }).catch(e => {
        toast('Error loading order: ' + e.message);
    });
}

function getStatusClass(status) {
    if (status === 'collected' || status === 'completed') return 'badge-green';
    if (status === 'reserved') return 'badge-blue';
    if (status === 'expired' || status === 'cancelled') return 'badge-red';
    return 'badge-yellow';
}

function getPaymentClass(status) {
    if (status === 'paid') return 'badge-green';
    if (status === 'failed') return 'badge-red';
    return 'badge-yellow';
}

function closeOrderModal() {
    document.getElementById('orderModal').style.display = 'none';
}

// ===== USERS =====
function loadUsers() {
    db.collection('users').get().then(snap => {
        const tbody = document.querySelector('#usersTable tbody');
        tbody.innerHTML = '';
        snap.forEach(doc => {
            const u = doc.data();
            const joined = u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—';
            tbody.innerHTML += `<tr>
                <td><strong>${(u.firstName || '') + ' ' + (u.lastName || '')}</strong></td>
                <td>${u.email || ''}</td>
                <td>${u.phone || ''}</td>
                <td>${joined}</td>
            </tr>`;
        });
        if (snap.empty) tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;color:#6E6E78">No users yet</td></tr>';
    });
}

// ===== ML ANALYTICS =====
function loadAnalytics() {
    db.collection('user_events').get().then(snap => {
        let views = 0, carts = 0, searches = 0, purchases = 0;
        const catScores = {}, brandScores = {}, searchQueries = {};

        snap.forEach(doc => {
            const d = doc.data();
            if (d.eventType === 'view') views++;
            if (d.eventType === 'add_to_cart') carts++;
            if (d.eventType === 'search') { searches++; if (d.query) searchQueries[d.query] = (searchQueries[d.query] || 0) + 1; }
            if (d.eventType === 'purchase') purchases++;
            if (d.category) catScores[d.category] = (catScores[d.category] || 0) + 1;
            if (d.brand) brandScores[d.brand] = (brandScores[d.brand] || 0) + 1;
        });

        document.getElementById('statViews').textContent = views;
        document.getElementById('statCartAdds').textContent = carts;
        document.getElementById('statSearches').textContent = searches;
        document.getElementById('statPurchases').textContent = purchases;

        // Category bars
        renderBarChart('topCategories', catScores);
        renderBarChart('topBrands', brandScores);

        // Search tags
        const tagDiv = document.getElementById('topSearches');
        tagDiv.innerHTML = '';
        const sorted = Object.entries(searchQueries).sort((a,b) => b[1]-a[1]).slice(0,20);
        sorted.forEach(([q, c]) => tagDiv.innerHTML += `<span class="tag">${q} (${c})</span>`);
        if (!sorted.length) tagDiv.innerHTML = '<span style="color:#6E6E78">No searches yet</span>';
    });
}

function renderBarChart(containerId, scores) {
    const div = document.getElementById(containerId);
    div.innerHTML = '';
    const sorted = Object.entries(scores).sort((a,b) => b[1]-a[1]).slice(0,8);
    const max = sorted.length ? sorted[0][1] : 1;
    sorted.forEach(([label, val]) => {
        const pct = (val / max * 100).toFixed(0);
        div.innerHTML += `<div class="bar-item"><span class="bar-label">${label}</span><div class="bar-track"><div class="bar-fill" style="width:${pct}%"></div></div><span class="bar-value">${val}</span></div>`;
    });
    if (!sorted.length) div.innerHTML = '<span style="color:#6E6E78">No data yet</span>';
}
