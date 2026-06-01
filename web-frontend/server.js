const express = require('express');
const axios = require('axios');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 5000;

// Catalog Service internal endpoint within the shared 'api_net' Docker network
const CATALOG_SERVICE_URL = process.env.CATALOG_SERVICE_URL || 'http://catalog-service:8081/api/products';

// Set up template engine (EJS)
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// Serve static assets if any
app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json());

// Home Page: Fetch inventory from Catalog Service and render
app.get('/', async (req, res) => {
    try {
        console.log(`Fetching products from Catalog Service: ${CATALOG_SERVICE_URL}`);
        const response = await axios.get(CATALOG_SERVICE_URL);
        const products = response.data;
        res.render('index', { products, error: null });
    } catch (err) {
        console.error('Error contacting Catalog Service:', err.message);
        res.render('index', { 
            products: [], 
            error: 'Unable to fetch store inventory. Please make sure the Catalog Service is running and databases are online.' 
        });
    }
});

// Admin Panel: Fetch inventory and serve to admin dashboard
app.get('/admin', async (req, res) => {
    try {
        console.log(`Fetching products for Admin panel from Catalog Service: ${CATALOG_SERVICE_URL}`);
        const response = await axios.get(CATALOG_SERVICE_URL);
        const products = response.data;
        res.render('admin', { products, error: null });
    } catch (err) {
        console.error('Error contacting Catalog Service for Admin:', err.message);
        res.render('admin', { 
            products: [], 
            error: 'Unable to load Admin dashboard. Catalog Service might be down.' 
        });
    }
});

// Checkout Page: Review purchase, display totals, configure simulator
app.get('/checkout', async (req, res) => {
    const { productId, quantity } = req.query;

    if (!productId || !quantity) {
        return res.redirect('/');
    }

    try {
        const qty = parseInt(quantity, 10);
        if (isNaN(qty) || qty < 1) {
            return res.redirect('/');
        }

        console.log(`Fetching product details for ID: ${productId} from Catalog Service`);
        const response = await axios.get(`${CATALOG_SERVICE_URL}/${productId}`);
        const product = response.data;

        // Perform calculation security checks
        const totalPrice = (product.price * qty).toFixed(2);

        res.render('checkout', { 
            product, 
            quantity: qty, 
            totalPrice, 
            error: null 
        });
    } catch (err) {
        console.error(`Error loading checkout for product ID ${productId}:`, err.message);
        res.render('checkout', { 
            product: null, 
            quantity: 0, 
            totalPrice: '0.00', 
            error: 'Failed to retrieve product details. The catalog item may have been updated or removed.' 
        });
    }
});

app.listen(PORT, () => {
    console.log(`Web Frontend Express server listening on port ${PORT}`);
});
