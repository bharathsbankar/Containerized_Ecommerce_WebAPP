# Azure Container Apps (ACA) - Step-by-Step Deployment Guide

This document provides a highly transparent, fully-featured, step-by-step Azure CLI script to deploy the **FlashSale Platform** to **Azure Container Apps** using isolated networks, secure internal routing, and persistent database storage shares.

---

## 📋 Prerequisites
Make sure you have the following installed on your host machine:
1. [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli)
2. [Git](https://git-scm.com/)

---

## 🛠️ Step 1: Define Environment Variables
Open a PowerShell (or Bash) terminal and define your variables. Customize the suffixes to ensure globally unique names on Azure:

```bash
# Variables (Customize these prefix values to make them unique)
RESOURCE_GROUP="rg-flashsale-prod"
LOCATION="eastus"
STORAGE_ACCOUNT="safslashsaleprod"
ACR_NAME="acrflashsaleprod"
ACA_ENV="env-flashsale-prod"

# Container App Names
CATALOG_DB_APP="catalog-db"
ORDER_DB_APP="order-db"
CATALOG_SERVICE_APP="catalog-service"
ORDER_SERVICE_APP="order-service"
WEB_FRONTEND_APP="web-frontend"
```

---

## 🛠️ Step 2: Login and Resource Group Provisioning
Authenticate with Azure and initialize your deployment environment:

```bash
# Log in to Azure
az login

# Register Container Apps provider namespace (if not already done)
az provider register --namespace Microsoft.App

# Create Resource Group
az group create --name $RESOURCE_GROUP --location $LOCATION
```

---

## 🛠️ Step 3: Container Registry Setup & Image Building
We will leverage **Azure Container Registry Tasks** to compile the Java binaries and package the Node frontend directly in the cloud. This avoids compiling heavy JARs locally:

```bash
# Create Azure Container Registry (ACR)
az acr create \
  --resource-group $RESOURCE_GROUP \
  --name $ACR_NAME \
  --sku Basic \
  --admin-enabled true

# Retrieve ACR credentials
ACR_USERNAME=$(az acr credential show --name $ACR_NAME --query "username" -o tsv)
ACR_PASSWORD=$(az acr credential show --name $ACR_NAME --query "passwords[0].value" -o tsv)

# Build and Push Catalog Microservice image directly in the cloud
az acr build --registry $ACR_NAME --image catalog-service:latest ./catalog-service

# Build and Push Order Microservice image directly in the cloud
az acr build --registry $ACR_NAME --image order-service:latest ./order-service

# Build and Push Web Frontend image directly in the cloud
az acr build --registry $ACR_NAME --image web-frontend:latest ./web-frontend
```

---

## 🛠️ Step 4: Provision Persistent Storage Shares
Since we are running MySQL inside Container Apps, we will create Azure Files storage shares to persist database tables and mount our seeding scripts:

```bash
# Create Storage Account
az storage account create \
  --resource-group $RESOURCE_GROUP \
  --name $STORAGE_ACCOUNT \
  --location $LOCATION \
  --sku Standard_LRS \
  --kind StorageV2

# Retrieve Storage Account Key
STORAGE_KEY=$(az storage account keys list --resource-group $RESOURCE_GROUP --account-name $STORAGE_ACCOUNT --query "[0].value" -o tsv)

# Create file shares for MySQL schemas
az storage share create --account-name $STORAGE_ACCOUNT --account-key $STORAGE_KEY --name "catalog-mysql-share"
az storage share create --account-name $STORAGE_ACCOUNT --account-key $STORAGE_KEY --name "order-mysql-share"

# Create a file share to hold the seeding init.sql
az storage share create --account-name $STORAGE_ACCOUNT --account-key $STORAGE_KEY --name "catalog-init-share"

# Upload the local init.sql script to the cloud init share
az storage file upload \
  --account-name $STORAGE_ACCOUNT \
  --account-key $STORAGE_KEY \
  --share-name "catalog-init-share" \
  --source "./catalog-db-init/init.sql" \
  --path "init.sql"
```

---

## 🛠️ Step 5: Setup Container Apps Environment & Mount Storage
Now we create the container runtime environment (virtual network subnet wrapper) and bind the Azure storage accounts to it:

```bash
# Create the Container Apps Environment
az containerapp env create \
  --resource-group $RESOURCE_GROUP \
  --name $ACA_ENV \
  --location $LOCATION

# Bind MySQL database storage shares to the Environment
az containerapp env storage set \
  --name $ACA_ENV \
  --resource-group $RESOURCE_GROUP \
  --storage-name "catalog-db-vol" \
  --account-name $STORAGE_ACCOUNT \
  --account-key $STORAGE_KEY \
  --share-name "catalog-mysql-share" \
  --access-mode ReadWrite

az containerapp env storage set \
  --name $ACA_ENV \
  --resource-group $RESOURCE_GROUP \
  --storage-name "order-db-vol" \
  --account-name $STORAGE_ACCOUNT \
  --account-key $STORAGE_KEY \
  --share-name "order-mysql-share" \
  --access-mode ReadWrite

# Bind database seeding share to the Environment
az containerapp env storage set \
  --name $ACA_ENV \
  --resource-group $RESOURCE_GROUP \
  --storage-name "catalog-init-vol" \
  --account-name $STORAGE_ACCOUNT \
  --account-key $STORAGE_KEY \
  --share-name "catalog-init-share" \
  --access-mode ReadWrite
```

---

## 🛠️ Step 6: Deploy Private Databases
We will deploy the database Container Apps with **Internal Ingress** on port 3306. They will have no public endpoints and will securely mount our persistent shares:

### 1. Catalog DB Deployment
Create a configuration schema (`catalog-db.yaml`) to mount the storage shares or deploy directly via CLI:
```bash
az containerapp create \
  --resource-group $RESOURCE_GROUP \
  --name $CATALOG_DB_APP \
  --environment $ACA_ENV \
  --image mysql:8.0 \
  --cpu 0.5 --memory 1.0Gi \
  --env-vars MYSQL_ROOT_PASSWORD=rootpassword MYSQL_DATABASE=catalog_db \
  --ingress internal --target-port 3306 \
  --storage "catalog-db-vol" --mount-path "/var/lib/mysql" \
  --storage "catalog-init-vol" --mount-path "/docker-entrypoint-initdb.d"
```

### 2. Order DB Deployment
```bash
az containerapp create \
  --resource-group $RESOURCE_GROUP \
  --name $ORDER_DB_APP \
  --environment $ACA_ENV \
  --image mysql:8.0 \
  --cpu 0.5 --memory 1.0Gi \
  --env-vars MYSQL_ROOT_PASSWORD=rootpassword MYSQL_DATABASE=order_db \
  --ingress internal --target-port 3306 \
  --storage "order-db-vol" --mount-path "/var/lib/mysql"
```

---

## 🛠️ Step 7: Deploy Private Java Microservices
These microservices communicate privately inside the environment. We configure internal ingress on ports 8081 and 8082, and point them to their respective internal databases:

### 1. Catalog Service Deployment
```bash
az containerapp create \
  --resource-group $RESOURCE_GROUP \
  --name $CATALOG_SERVICE_APP \
  --environment $ACA_ENV \
  --image "${ACR_NAME}.azurecr.io/catalog-service:latest" \
  --registry-server "${ACR_NAME}.azurecr.io" \
  --registry-username $ACR_USERNAME \
  --registry-password $ACR_PASSWORD \
  --cpu 0.75 --memory 1.5Gi \
  --ingress internal --target-port 8081 \
  --env-vars SPRING_DATASOURCE_URL="jdbc:mysql://${CATALOG_DB_APP}:3306/catalog_db?createDatabaseIfNotExist=true" \
             SPRING_DATASOURCE_USERNAME=root \
             SPRING_DATASOURCE_PASSWORD=rootpassword
```

### 2. Order Service Deployment
```bash
az containerapp create \
  --resource-group $RESOURCE_GROUP \
  --name $ORDER_SERVICE_APP \
  --environment $ACA_ENV \
  --image "${ACR_NAME}.azurecr.io/order-service:latest" \
  --registry-server "${ACR_NAME}.azurecr.io" \
  --registry-username $ACR_USERNAME \
  --registry-password $ACR_PASSWORD \
  --cpu 0.75 --memory 1.5Gi \
  --ingress internal --target-port 8082 \
  --env-vars SPRING_DATASOURCE_URL="jdbc:mysql://${ORDER_DB_APP}:3306/order_db?createDatabaseIfNotExist=true" \
             SPRING_DATASOURCE_USERNAME=root \
             SPRING_DATASOURCE_PASSWORD=rootpassword
```

---

## 🛠️ Step 8: Deploy Public Web Frontend
Finally, we deploy our Node.js BFF proxy container. We enable **External Ingress** on port 5000 so users can access the website, and configure the proxy target variables using the private DNS aliases:

```bash
az containerapp create \
  --resource-group $RESOURCE_GROUP \
  --name $WEB_FRONTEND_APP \
  --environment $ACA_ENV \
  --image "${ACR_NAME}.azurecr.io/web-frontend:latest" \
  --registry-server "${ACR_NAME}.azurecr.io" \
  --registry-username $ACR_USERNAME \
  --registry-password $ACR_PASSWORD \
  --cpu 0.5 --memory 1.0Gi \
  --ingress external --target-port 5000 \
  --env-vars PORT=5000 \
             CATALOG_SERVICE_URL="http://${CATALOG_SERVICE_APP}:8081/api/products" \
             ORDER_SERVICE_URL="http://${ORDER_SERVICE_APP}:8082/api/orders"
```

---

## 🎉 Verification
Retrieve your publicly exposed frontend URL:
```bash
az containerapp show \
  --resource-group $RESOURCE_GROUP \
  --name $WEB_FRONTEND_APP \
  --query "properties.configuration.ingress.fqdn" \
  -o tsv
```
*Copy and paste the returned HTTPS URL into your web browser to enjoy your premium, secure Flash Sale system running fully on Azure Container Apps!*
