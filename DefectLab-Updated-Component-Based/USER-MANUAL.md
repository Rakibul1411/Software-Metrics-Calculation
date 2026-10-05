# DefectLab - User Manual & Developer Guide

Welcome to the **DefectLab** operational and deployment manual. This document covers:
1. **End-User Guide**: How to install and run DefectLab using the native Desktop App (`.dmg` for Mac / `.exe` for Windows) or Docker Compose, and understanding which database it connects to.
2. **Developer Guide (Project Owner)**: How to make code changes and push updated images to Docker Hub **without ever needing to rebuild `.dmg` or `.exe` files**.
3. **End-User Update Guide**: How users seamlessly receive your latest code updates.

---

## 📋 Table of Contents
- [Part 1: End-User Guide (How to Run DefectLab)](#part-1-end-user-guide)
  - [Prerequisites](#prerequisites)
  - [Method 1: Run with Desktop App (.dmg for Mac / .exe for Windows)](#method-1-native-desktop-app-recommended)
  - [Method 2: Run via Terminal with Docker Compose](#method-2-run-via-terminal-with-docker-compose)
  - [Database Details: Which Database Does It Connect To?](#database-details-which-database-does-it-connect-to)
  - [Optional: Connecting to Cloud Neon DB instead of Local PostgreSQL](#optional-connecting-to-cloud-neon-db)
- [Part 2: Developer Guide (Code Updates & Docker Hub Publishing)](#part-2-developer-guide-for-project-owner)
  - [Why You NEVER Need to Rebuild .dmg or .exe Files](#why-you-never-need-to-rebuild-dmg-or-exe-files)
  - [Step-by-Step: Updating Code and Pushing to Docker Hub](#step-by-step-updating-code-and-pushing-to-docker-hub)
- [Part 3: How Users Receive Your Latest Updates](#part-3-how-users-receive-your-latest-updates)
- [Part 4: Stopping Services and Resetting Data](#part-4-stopping-services-and-resetting-data)
- [Part 5: Troubleshooting & FAQ](#part-5-troubleshooting--faq)

---

## Part 1: End-User Guide

### Prerequisites
Users do **not** need Java, Maven, Node.js, or Python installed on their computer. They only need:
- **Docker** or **OrbStack**:
  - **macOS**: [OrbStack](https://orbstack.dev/) (recommended, super lightweight) or Docker Desktop.
  - **Windows**: [Docker Desktop](https://www.docker.com/products/docker-desktop/) (with WSL2 enabled).

*(Make sure Docker or OrbStack is running before launching DefectLab).*

---

### Method 1: Native Desktop App (Recommended)

DefectLab provides native desktop installers for both macOS and Windows:

#### For macOS Users:
1. Open the **`DefectLab-1.0.0-arm64.dmg`** installer.
2. Drag **DefectLab.app** into your **Applications** folder.
3. Launch **DefectLab** from Launchpad or Applications.

#### For Windows Users:
1. Double-click the **`DefectLab Setup 1.0.0.exe`** installer.
2. Follow the standard setup wizard. A desktop shortcut and start menu entry will be created.
3. Launch **DefectLab** from your Desktop.

#### What Happens When You Open the Desktop App:
- A dark loading screen appears: *"Connecting to DefectLab Core..."*
- The app automatically detects Docker, starts the internal services, and launches the full DefectLab workspace in a clean desktop window (no browser tabs or address bars).

---

### Method 2: Run via Terminal with Docker Compose

If a user prefers running in their browser (`http://localhost:4200`):

1. **Download the production compose file:**
   - **Terminal (1 step):**
     ```bash
     mkdir defectlab && cd defectlab
     curl -o docker-compose.yml https://raw.githubusercontent.com/Rakibul1411/Software-Metrics-Calculation/master/DefectLab-Updated-Component-Based/docker-compose.prod.yml
     ```
   - **Or via Browser:**
     Open [docker-compose.prod.yml on GitHub](https://github.com/Rakibul1411/Software-Metrics-Calculation/blob/master/DefectLab-Updated-Component-Based/docker-compose.prod.yml) and click **Download raw file**.

2. **Start the application:**
   ```bash
   docker compose up -d
   ```
   *(Docker will pull pre-built images from Docker Hub: `rakibalnatiq/defectlab-frontend`, `rakibalnatiq/defectlab-backend`, `rakibalnatiq/defectlab-ml`, and `postgres:16-alpine`)*

3. **Open in browser:**
   👉 **[http://localhost:4200](http://localhost:4200)**

---

### Database Details: Which Database Does It Connect To?

- **Default Database**: By default, DefectLab spins up an **internal, isolated PostgreSQL 16 container** inside Docker.
- **Pre-seeded Benchmarks**: The internal database comes pre-loaded with standard benchmark datasets (PROMISE Ant 1.3, 1.6, 1.7, Lucene 2.4, and AEEEM JDT 3.4, EQ 3.4, PDE 3.4.1, LC 2.4.0, ML 3.1).
- **Security**: The database is connected internally via Docker's bridge network (`defectlab-net`), keeping it secure and isolated from other databases on the host computer.

---

### Optional: Connecting to Cloud Neon DB

If a user wants to connect to a remote **Neon DB (Cloud PostgreSQL)** instead of using the local PostgreSQL container:

1. Download the `.env` template:
   ```bash
   curl -o .env https://raw.githubusercontent.com/Rakibul1411/Software-Metrics-Calculation/master/DefectLab-Updated-Component-Based/.env.example
   ```
   *(Or view on GitHub: [.env.example](https://github.com/Rakibul1411/Software-Metrics-Calculation/blob/master/DefectLab-Updated-Component-Based/.env.example))*

2. Open `.env` and fill in your Neon DB connection details:
   ```env
   DEFECTLAB_DB_URL=jdbc:postgresql://<neon-hostname>/neondb?sslmode=require
   DEFECTLAB_DB_USER=<neon-username>
   DEFECTLAB_DB_PASSWORD=<neon-password>
   ```

3. Start without the local postgres container:
   ```bash
   docker compose -f docker-compose.prod.yml up -d ml backend frontend
   ```
   Spring Boot will connect directly to your Neon DB cloud instance!

---

## Part 2: Developer Guide (For Project Owner)

### Why You NEVER Need to Rebuild `.dmg` or `.exe` Files

The DefectLab desktop app uses a **Smart Web-Wrapper Architecture**:
- The desktop app (`.dmg` / `.exe`) is a lightweight shell that loads the application interface served by Docker.
- The business logic, ML models, and Angular UI reside inside the Docker containers (`rakibalnatiq/defectlab-*`).
- **Benefit:** When you modify Java code, Python ML algorithms, or Angular frontend pages, you **only update the Docker Hub images**. The desktop app automatically renders your latest updates on its next run!

---

### Step-by-Step: Updating Code and Pushing to Docker Hub

Whenever you make improvements to the project:

#### Step 1: Test your changes locally
```bash
docker compose up -d --build
```
Verify the changes at `http://localhost:4200`.

#### Step 2: Re-build and Push updated images to Docker Hub
```bash
# 1. Build updated images with your Docker Hub tags
docker compose build

# 2. Push images to your public Docker Hub account (rakibalnatiq)
docker compose push
```
Docker Hub will push the updated layers for `rakibalnatiq/defectlab-backend:latest`, `rakibalnatiq/defectlab-ml:latest`, and `rakibalnatiq/defectlab-frontend:latest`.

#### Step 3: Commit and Push your source code to GitHub
```bash
git add .
git commit -m "feat: updated defect prediction logic and UI"
git push origin master
```

---

## Part 3: How Users Receive Your Latest Updates

When you push new images to Docker Hub, an end-user does **not** need to reinstall the `.dmg` or `.exe` app, and they don't even need to touch the terminal!

### 1. For Desktop App Users (.dmg / .exe) — 100% Fully Automatic!
- Whenever the user opens the **DefectLab desktop app**, it automatically checks Docker Hub in the background (*"Checking for latest updates from Docker Hub..."*).
- If you have pushed updated code or models, the desktop app **automatically downloads the new Docker images and restarts the services** seamlessly.
- **The user does not need to run ANY terminal command!**

### 2. For Web / Terminal Users (Docker Compose):
If a user is running DefectLab directly via browser/terminal:
```bash
docker compose pull
docker compose up -d
```
The browser at `http://localhost:4200` will instantly reflect your latest updates!

---

## Part 4: Stopping Services and Resetting Data

### To stop DefectLab services:
```bash
docker compose down
```
*(All database records and uploaded datasets remain safely stored in Docker volumes).*

### To completely wipe data and start fresh:
```bash
docker compose down -v
```

---

## Part 5: Troubleshooting & FAQ

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| **"Docker is not running" error on desktop app** | Docker Desktop or OrbStack is closed. | Launch OrbStack or Docker Desktop, then click **Retry** on the app screen. |
| **Port 4200 or 8080 already in use** | Another local app is using that port. | In `docker-compose.prod.yml`, change `"4200:80"` to `"5000:80"` (or another available port). |
| **Changes not appearing after update** | Docker is using cached image layers. | Run `docker compose pull && docker compose up -d`. |
| **Neon DB connection refused** | Missing SSL mode in Neon JDBC URL. | Ensure the URL ends with `?sslmode=require`. |

---
**Maintained with ❤️ by Rakibul Islam for DefectLab Project.**
