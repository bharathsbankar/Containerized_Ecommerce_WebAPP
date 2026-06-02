# AI Handoff & Architecture Blueprint - FlashSale Platform

This document serves as the absolute single source of truth for any developer or AI assistant (e.g. Claude Code, Cursor, Copilot) inheriting this repository. It documents the exact technical architecture, package dependencies, codebase map, active database schemas, and critical architectural guardrails required to maintain system integrity.

---

## 1. Project Overview

The **FlashSale Platform** is a containerized, microservices-based, high-concurrency e-commerce application designed to handle extremely high-traffic shopping rushes (flash sales) safely and reliably. 

### Core System Goals
* **Inventory Safety**: Prevent overselling and "Lost Update" anomalies when thousands of users attempt to purchase highly limited stock at the exact same millisecond.
* **Service Decoupling**: Separate high-read inventory search traffic (**Catalog Microservice**) from write-heavy transactional operations (**Order Microservice**).
* **Strict Network Isolation**: Isolate MySQL data layers from the public host machine, routing database traffic through private virtual bridge networks.
* **Premium Client Experience**: Serve a responsive, real-time customer interface with Flipkart-inspired styling, bounds-validated quantity controllers, and simulated secure checkout loading overlays.

---

## 2. Tech Stack & Dependencies

The project is structured as a multi-directory monorepo containing three primary applications compiled and run inside Docker containers.

### Frontend Layer (`web-frontend/`)
* **Runtime**: Node.js (`node:18-alpine`)
* **Web Framework**: Express.js (`^4.19.2`)
* **Template Engine**: EJS (`^3.1.10`) for secure server-rendered pages
* **Styling**: Vanilla CSS + Local Standalone Tailwind JS Engine (served statically from `/public/js/tailwind.js` for 100% offline capability)
* **Http Client**: Axios (`^1.7.2`) for server-to-server data fetching and API Gateway proxying (BFF pattern)

### Catalog Microservice (`catalog-service/`)
* **Runtime**: Java 17 (JBR / Alpine `eclipse-temurin:17-jre-alpine`)
* **Core Framework**: Spring Boot 3.2.5 (`spring-boot-starter-web`)
* **Persistence Layer**: Spring Data JPA (`spring-boot-starter-data-jpa`) + Hibernate
* **Database Driver**: MySQL Connector/J (`com.mysql:mysql-connector-j`)

### Order Microservice (`order-service/`)
* **Runtime**: Java 17 (JBR / Alpine `eclipse-temurin:17-jre-alpine`)
* **Core Framework**: Spring Boot 3.2.5 (`spring-boot-starter-web`)
* **Persistence Layer**: Spring Data JPA + Hibernate
* **Remote Clients**: Spring Cloud OpenFeign 2023.0.1 (`spring-cloud-starter-openfeign`) for declarative HTTP API calls

### Database & Administration Layers
* **Database Engine**: MySQL 8.0 (two distinct, isolated containers)
* **Web GUI Administrators**: Adminer (`adminer:latest` on ports 8083 & 8084)

---

## 3. Architecture & Component Map

The platform is segregated across three isolated Docker virtual bridge networks:
1. `catalog_net`: Connects `catalog-db` and `catalog-service` privately.
2. `order_net`: Connects `order-db` and `order-service` privately.
3. `api_net`: Connects `web-frontend`, `catalog-service`, and `order-service` for internal routing.

```
├── docker-compose.yml       # Primary Docker orchestration configuration
├── .gitignore               # Clean git exclusion policies (excludes Java target/ and Node node_modules/)
├── AGENTS.md                # AI handoff blueprint (this file)
├── catalog-db-init/
│   └── init.sql             # Catalog schema definition and SQL seeding
├── web-frontend/            # Node.js EJS Frontend Client & BFF API Gateway
│   ├── Dockerfile           # Alpine node container builder
│   ├── package.json         # Front-end manifest
│   ├── server.js            # Express router & BFF proxies (handles relative /api/orders and /api/products)
│   ├── public/              # Static local assets (BFF Proxy static mounts)
│   │   └── js/
│   │       └── tailwind.js  # Standalone Tailwind CSS compiler for 100% offline support
│   └── views/
│       ├── index.ejs        # Main deals dashboard with bounds-validated quantity controllers (Rupee ₹ currency)
│       ├── checkout.ejs     # Secure payment simulator (2-second spinner, client relative POST)
│       └── admin.ejs        # Catalog CRUD administrator dashboard (AJAX Create, Update, Delete)
├── catalog-service/         # Product Inventory Microservice
│   ├── Dockerfile           # Multi-stage Maven -> Alpine Temurin builder
│   ├── pom.xml              # Maven dependencies
│   └── src/main/
│       ├── resources/
│       │   └── application.properties  # Port 8081 configurations & MySQL connections
│       └── java/com/flashsale/catalogservice/
│           ├── CatalogServiceApplication.java # Spring Boot entry-point
│           ├── model/Product.java            # Product JPA entity mapping to "products" table
│           ├── repository/ProductRepository.java  # Declares database-level atomic decrements
│           ├── controller/ProductController.java  # Product REST APIs (CORS enabled for port 5000)
│           └── exception/                    # Custom exceptions and JSON mapping GlobalExceptionHandler
└── order-service/           # Order Transaction Microservice
    ├── Dockerfile           # Multi-stage Maven builder
    ├── pom.xml              # Maven dependencies + Spring Cloud Feign
    └── src/main/
        ├── resources/
        │   └── application.properties  # Port 8082 configurations & MySQL connections
        └── java/com/flashsale/orderservice/
            ├── OrderServiceApplication.java # Spring Boot entry-point with @EnableFeignClients
            ├── model/Order.java             # Transaction JPA entity mapping to "orders" table
            ├── repository/OrderRepository.java # Standard JpaRepository
            ├── client/CatalogClient.java    # OpenFeign mappings targeting http://catalog-service:8081
            ├── dto/                         # DTO wrappers (ProductDto, OrderRequest)
            ├── controller/OrderController.java # Transaction controller (Order creation + catalog decrement rollback)
            └── exception/                   # Downstream Feign error parsing & GlobalExceptionHandler
```

---

## 4. Current State & Implementation Progress

All components have been fully coded, validated, compiled, and successfully pushed to the remote GitHub repository:

### Core Workflows Completed
* **Atomic Concurrency Control**: Written a database-level query `UPDATE products SET stock_quantity = stock_quantity - :quantity WHERE id = :id AND stock_quantity >= :quantity` in `ProductRepository` to handle parallel stock subtractions safely.
* **Distributed Transaction Safety**: Configured `@Transactional` on `createOrder` in `OrderController`. If the downstream OpenFeign call to Catalog decrement fails, the order creation in `order_db` rolls back automatically.
* **Zero Database Coupling**: Mapped `product_id` in the `Order` entity as a plain `Long` value rather than a database Join, maintaining physical decoupling.
* **Adminer Integration**: Added isolated database GUIs accessible at `http://localhost:8083` (Catalog) and `http://localhost:8084` (Order) using private container DNS names.
* **Express Admin CRUD**: Added a fully featured AJAX Admin Panel at `http://localhost:5000/admin` allowing developers to Add, Edit, and Delete products dynamically.
* **Rupees Translation**: Replaced all USD ($) symbols with Indian Rupees (**₹**).
* **Secure BFF Proxy Migration**: Migrated the system to the **Backend-for-Frontend (BFF)** proxy pattern. Browser client AJAX scripts use relative URLs (`/api/orders` and `/api/products`), communicating solely with the publicly exposed Node.js gateway (Port 5000). Express server proxies these requests internally over the virtual network to ports `8081` and `8082`, hiding the microservices and resolving CORS securely.
* **Complete Offline CSS Support**: Embedded the standalone Tailwind JS compilation engine directly into the frontend container's public assets (`/public/js/tailwind.js`). Modified EJS templates to reference local script routes, ensuring perfect styling renders even in zero-internet sandbox environments.

---

## 5. Development Guardrails & Rules for Future Agents

Any downstream agent or developer editing this codebase must respect the following architectural guardrails:

> [!CAUTION]
> 1. **No JPA Joins Across Services**: Under no circumstances should `Order` contain a JPA join (`@ManyToOne`, `@OneToMany`, or `@JoinColumn`) pointing to `Product`. They live in separate databases on separate networks. Keep `product_id` as a plain `Long`.
> 2. **Never Expose MySQL Ports (3306)**: Keep `3306` closed to the host machine inside `docker-compose.yml`. Database administration must only be conducted through the host-exposed Adminer GUIs (8083 and 8084) using internal container hostnames (`catalog-db` and `order-db`).
> 3. **High-Concurrency Decoupling**: If modifying stock deduction logic, never subtract stock in Java memory. You must perform deduction using the atomic repository query or a database lock to prevent race conditions.
> 4. **Keep Prices Immutable**: When saving an order, always snap the unit price from the Catalog service via Feign at the exact millisecond of checkout. Never recalculate pricing on-the-fly from the catalog at a later date, as catalog prices will fluctuate.
> 5. **BFF Ingress Boundary**: The client browser must NEVER communicate directly with ports `8081` or `8082` in EJS/HTML scripts. All browser-side AJAX requests must be sent relative to the host (`/api/products` and `/api/orders`), allowing the Express gateway to proxy them internally. This protects microservice ingress ports in cloud environments.
> 6. **Static Resource Isolation**: All public scripts and styling engines must be served locally from `/public` to ensure offline portability inside container networks. Do not use external CSS or JS CDN URLs.
