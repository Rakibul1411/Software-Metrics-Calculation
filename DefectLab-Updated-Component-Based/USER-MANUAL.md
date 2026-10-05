# DefectLab - User Manual & Developer Guide

Welcome to the **DefectLab** deployment and operational manual. This document contains complete instructions for:
1. **End-Users / Reviewers**: How to run DefectLab instantly with pre-built Docker images (with Local PostgreSQL or Cloud Neon DB).
2. **Developers (Project Owner)**: How to push code changes, update GitHub, and update Docker Hub images.
3. **Database Customization**: How to switch seamlessly between internal PostgreSQL and remote Neon DB.

---

## 📋 Table of Contents
- [Part 1: End-User Quickstart (How to Run the Project)](#part-1-end-user-quickstart)
  - [Prerequisites](#prerequisites)
  - [Option A: Instant Run with Pre-built Images (Recommended - 1 Minute)](#option-a-instant-run-with-pre-built-images-recommended)
  - [Option B: Run from Cloned Repository](#option-b-run-from-cloned-repository)
  - [Option C: Using Neon DB (Remote PostgreSQL) instead of Local DB](#option-c-using-neon-db-cloud-database-instead-of-local-postgresql)
- [Part 2: How to Stop & Manage Data](#part-2-how-to-stop--manage-data)
- [Part 3: Developer Guide (Code Updates & Docker Hub Publishing)](#part-3-developer-guide-for-project-owner)
  - [Workflow: Updating Code, GitHub & Docker Hub](#workflow-updating-code-github--docker-hub)
  - [Automating with GitHub Actions (CI/CD)](#optional-automating-with-github-actions-cicd)
- [Part 4: Common Troubleshooting & Corner Cases](#part-4-common-troubleshooting--corner-cases)

---

## Part 1: End-User Quickstart

### Prerequisites
You do **not** need Java, Maven, Node.js, or Python installed on your machine. You only need:
- **Docker** and **Docker Compose**:
  - **macOS**: [OrbStack](https://orbstack.dev/) (recommended for speed) or Docker Desktop.
  - **Windows**: [Docker Desktop](https://www.docker.com/products/docker-desktop/) (with WSL2 enabled).
  - **Linux**: Docker Engine + Docker Compose Plugin.

---

### Option A: Instant Run with Pre-built Images (Recommended)

You don't even need to clone the full repository. You can run DefectLab with just the `docker-compose.prod.yml` file.

1. **Create an empty folder and download the compose file:**
   ```bash
   mkdir defectlab && cd defectlab
   curl -o docker-compose.yml https://raw.githubusercontent.com/Rakibul1411/Software-Metrics-Calculation/features/promise-metrics-calculation/DefectLab-Updated-Component-Based/docker-compose.prod.yml
   ```

2. **Start the application:**
   ```bash
   docker compose up -d
   ```

3. **Access the application:**
   Open your browser and navigate to:
   👉 **[http://localhost:4200](http://localhost:4200)**

---

### Option B: Run from Cloned Repository

If you have cloned the source repository:

```bash
git clone https://github.com/Rakibul1411/Software-Metrics-Calculation.git
cd Software-Metrics-Calculation/DefectLab-Updated-Component-Based
```

To run using pre-built images instantly:
```bash
docker compose -f docker-compose.prod.yml up -d
```

*(Or to build from source code locally: `docker compose up --build -d`)*

---

### Option C: Using Neon DB (Cloud Database) instead of Local PostgreSQL

By default, DefectLab spins up an isolated, local PostgreSQL container inside Docker. If a user wants to connect to their own **Neon DB (Cloud PostgreSQL)** instead of running the local Postgres container:

#### Step 1: Create an `.env` file
Inside the folder where you run Docker, create a file named `.env`:

```env
# Point Spring Boot to your Neon DB connection URL:
DEFECTLAB_DB_URL=jdbc:postgresql://<neon-hostname>/<dbname>?sslmode=require
DEFECTLAB_DB_USER=<neon-username>
DEFECTLAB_DB_PASSWORD=<neon-password>
```

> **Example for Neon DB:**
> ```env
> DEFECTLAB_DB_URL=jdbc:postgresql://ep-silent-hill-123456.us-east-2.aws.neon.tech/neondb?sslmode=require
> DEFECTLAB_DB_USER=rakibul_owner
> DEFECTLAB_DB_PASSWORD=npg_secretpassword123
> ```

#### Step 2: (Optional) Disable local PostgreSQL container
If you are using Neon DB, you don't need the local `postgres` container running. You can simply run only the application services:

```bash
docker compose -f docker-compose.prod.yml up -d ml backend frontend
```

Spring Boot will automatically connect to your Neon DB, initialize the schema, seed the default dataset benchmarks, and link with the Python ML microservice and Angular frontend!

---

## Part 2: How to Stop & Manage Data

### To stop DefectLab:
```bash
docker compose down
```
*(Your data inside the database and uploaded datasets will be safely preserved in Docker volumes).*

### To completely reset and delete all local data:
```bash
docker compose down -v
```

---

## Part 3: Developer Guide (For Project Owner)

When you make changes to the source code (Java backend, Python ML service, or Angular frontend), follow this workflow to update both GitHub and the public Docker Hub images.

### Workflow: Updating Code, GitHub & Docker Hub

#### Step 1: Test your code changes locally
Make your desired code edits and ensure your local test passes:
```bash
docker compose up -d --build
```
Verify that the services start and are Healthy at `http://localhost:4200`.

#### Step 2: Re-build and Push new images to Docker Hub
Whenever you want users to receive your latest updates:

```bash
# 1. Build fresh images tagged with your Docker Hub username
docker compose build

# 2. Push updated images to Docker Hub (rakibalnatiq/defectlab-*)
docker compose push
```

#### Step 3: Commit and Push your changes to GitHub
```bash
git add .
git commit -m "feat: updated defect prediction logic and UI improvements"
git push origin features/promise-metrics-calculation
```

Once pushed, any user running `docker compose pull && docker compose up -d` will automatically download your latest images!

---

### (Optional) Automating with GitHub Actions (CI/CD)

If you don't want to run `docker compose push` manually from your laptop every time, you can automate it with a GitHub Actions workflow:

Create `.github/workflows/docker-publish.yml` in your repository:
```yaml
name: Build and Push Docker Images

on:
  push:
    branches: [ "features/promise-metrics-calculation", "main" ]

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Log in to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and Push Images
        working-directory: DefectLab-Updated-Component-Based
        run: |
          docker compose build
          docker compose push
```
*(Add `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` in your GitHub Repository -> Settings -> Secrets and variables -> Actions).*

---

## Part 4: Common Troubleshooting & Corner Cases

| Scenario / Error | Cause | Solution |
| :--- | :--- | :--- |
| **Port already in use (`4200` or `8080`)** | Another service on your computer is occupying port 4200 or 8080. | Stop the local service or change the host port in `docker-compose.prod.yml` (e.g. change `"4200:80"` to `"5000:80"`). |
| **Neon DB SSL connection error** | Missing SSL mode query parameter in JDBC URL. | Ensure your URL ends with `?sslmode=require`. |
| **Changes not showing up after `docker compose up`** | Docker is reusing locally cached image layers. | Run `docker compose build --no-cache && docker compose up -d` (or `docker compose pull`). |
| **Backend marked Unhealthy** | Database not ready yet or network latency on remote Neon DB. | The container has a 20-second startup grace period. Check logs using `docker compose logs backend --tail 50`. |

---
**Made with ❤️ for DefectLab Project.**
