# ⚡ FlashDash Deals - High-Concurrency Flash Sale Platform

Welcome to **FlashDash Deals**, a production-grade, containerized microservices platform designed to handle extremely high-traffic shopping rushes (flash sales) safely and reliably.

The system employs advanced database-level concurrency control, strict transactional rollbacks, private network isolation, and a Backend-for-Frontend (BFF) gateway to guarantee inventory safety and system security under parallel heavy loads.

---

## 🏗️ High-Level System Architecture

The platform separates high-read product queries from write-heavy purchases using two distinct Spring Boot microservices and isolated database schemas. It routes all public traffic securely through a Node.js BFF proxy inside a triple-network Docker virtual subnet:

```
                  ┌────────────────────────┐
                  │  Client Browser (Host) │
                  └───────────┬────────────┘
                              │ Port 5000 (External Ingress)
                              ▼
  ======================== api_net ========================
  │                                                       │
  │     ┌───────────────────────────────────────────┐     │
  │     │       web-frontend Container (Node.js)    │     │
  │     └─────────────┬──────────────────────┬──────┘     │
  │                   │                      │            │
  │                   ▼                      ▼            │
  │     ┌──────────────────────────┐   ┌──────────────────┐
  │     │      catalog-service     │   │   order-service  │
  │     └─────────────┬────────────┘   └────────┬─────────┘
  │                   │                         │         │
  ====================│=========================│==========
                      │ catalog_net             │ order_net
                      ▼                         ▼
                ┌────────────┐            ┌───────────┐
                │ catalog-db │            │  order-db │
                └────────────┘            └───────────┘
```

---

## 🛠️ Technology Stack

- **Frontend Layer**: Node.js (`v18-alpine`), Express.js, server-rendered EJS templates. Styled using a statically served, local **Tailwind CSS standalone engine** for **100% offline capability**.
- **Catalog Microservice**: Java 17, Spring Boot 3.2.5, Spring Data JPA, Hibernate, MySQL Connector/J.
- **Order Microservice**: Java 17, Spring Boot 3.2.5, Spring Cloud OpenFeign (declarative internal REST client), JPA.
- **Database & DevOps Layer**: MySQL 8.0 databases, Adminer (database web GUIs), Docker, and Docker Compose orchestrating three isolated networks (`catalog_net`, `order_net`, and `api_net`).

---

## ✨ Core Concurrency & Security Features

1. **Atomic Concurrency Control (No Overselling)**: Inventory decrements are handled via a thread-safe, database-level update query:
   `UPDATE products SET stock_quantity = stock_quantity - :quantity WHERE id = :id AND stock_quantity >= :quantity`
   This prevents race conditions (Lost Update anomaly) during parallel checkouts without requiring heavy JVM memory locks.
2. **Distributed Transaction Integrity (Rollbacks)**: The Order service uses `@Transactional` blocks. If the downstream Feign call to Catalog decrement fails, the newly created order in `order_db` **automatically rolls back**, ensuring complete system consistency.
3. **Secure BFF (Backend-for-Frontend) Proxy**: Browser AJAX calls never talk directly to backend microservices. They use relative paths (`/api/orders` and `/api/products`), communicating solely with the publicly exposed Node.js server. Express proxies these requests internally, hiding microservice ports and resolving CORS.
4. **Strict Network Isolation**: MySQL containers do not map ports to the host laptop. They reside exclusively inside private database networks (`catalog_net` and `order_net`), reachable only by their respective backend apps.
5. **Adminer DB GUIs**: Integrates secure database GUI administrators at `http://localhost:8083` (Catalog) and `http://localhost:8084` (Order) using private network connections.

---

## 🚀 How to Run Locally

You can spin up the entire multi-network microservice stack with a single command:

```bash
# Build and orchestrate all containers and networks
docker compose up -d --build
```

### Access Endpoints:

- **Customer Dashboard & Admin Panel**: [http://localhost:5000](http://localhost:5000)
- **Catalog DB Web GUI**: [http://localhost:8083](http://localhost:8083)

Server: catalog-db | User: root | Password: rootpassword | Database: catalog_db

- **Order DB Web GUI**: [http://localhost:8084](http://localhost:8084)

Server: order-db | User: root | Password: rootpassword | Database: order_db
