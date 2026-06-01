CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    stock_quantity INT NOT NULL,
    CONSTRAINT chk_stock CHECK (stock_quantity >= 0)
);

-- Seed initial products for the Flash Sale
INSERT INTO products (name, description, price, stock_quantity) VALUES
('iPhone 15 Pro', '128GB, Blue Titanium', 999.00, 5),
('Sony WH-1000XM5', 'Wireless Noise Canceling Headphones', 349.99, 2),
('Mechanical Keyboard', 'RGB Linear Switches', 120.00, 0);
