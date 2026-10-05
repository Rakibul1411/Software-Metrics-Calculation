const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const http = require('http');
const https = require('https');
const fs = require('fs');
const { exec } = require('child_process');

let mainWindow;
const TARGET_URL = 'http://localhost:4200';
const PROD_COMPOSE_URL = 'https://raw.githubusercontent.com/Rakibul1411/Software-Metrics-Calculation/master/DefectLab-Updated-Component-Based/docker-compose.prod.yml';

// Extended PATH for Mac & Linux so Electron finds docker/orbstack
const customEnv = {
  ...process.env,
  PATH: `${process.env.HOME}/.orbstack/bin:/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:${process.env.PATH || ''}`
};

function getLocalFallbackComposeFilePath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'docker-compose.prod.yml');
  }
  return path.join(__dirname, 'docker-compose.prod.yml');
}

/**
 * Downloads the latest docker-compose.prod.yml from GitHub repository.
 * If successful, saves to userData directory.
 * If offline or GitHub is unreachable, falls back to existing cached version or packaged version.
 */
function syncRemoteComposeFile() {
  return new Promise((resolve) => {
    const userDataPath = app.getPath('userData');
    if (!fs.existsSync(userDataPath)) {
      try {
        fs.mkdirSync(userDataPath, { recursive: true });
      } catch (err) {
        console.error('Failed to create userData directory:', err);
      }
    }
    const targetFile = path.join(userDataPath, 'docker-compose.prod.yml');
    const localFallback = getLocalFallbackComposeFilePath();

    function useFallback() {
      if (fs.existsSync(targetFile)) {
        console.log('Using previously cached compose file:', targetFile);
        return resolve(targetFile);
      }
      try {
        if (fs.existsSync(localFallback)) {
          fs.copyFileSync(localFallback, targetFile);
          console.log('Initialized compose file from local bundled resources:', targetFile);
          return resolve(targetFile);
        }
      } catch (e) {
        console.error('Error copying local fallback compose file:', e);
      }
      resolve(localFallback);
    }

    const req = https.get(PROD_COMPOSE_URL, { timeout: 8000 }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        // Handle redirect
        https.get(res.headers.location, { timeout: 8000 }, (redirectRes) => {
          if (redirectRes.statusCode === 200) {
            let data = '';
            redirectRes.on('data', (chunk) => { data += chunk; });
            redirectRes.on('end', () => {
              if (data.includes('services:')) {
                fs.writeFileSync(targetFile, data, 'utf8');
                console.log('Successfully updated docker-compose.prod.yml from GitHub!');
                return resolve(targetFile);
              }
              useFallback();
            });
          } else {
            useFallback();
          }
        }).on('error', useFallback);
        return;
      }

      if (res.statusCode === 200) {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => {
          if (data.includes('services:')) {
            fs.writeFileSync(targetFile, data, 'utf8');
            console.log('Successfully updated docker-compose.prod.yml from GitHub!');
            return resolve(targetFile);
          }
          useFallback();
        });
      } else {
        useFallback();
      }
    });

    req.on('error', (err) => {
      console.warn('Network issue fetching compose file from GitHub, using fallback:', err.message);
      useFallback();
    });

    req.on('timeout', () => {
      req.destroy();
      console.warn('Timeout fetching compose file from GitHub, using fallback');
      useFallback();
    });
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1360,
    height: 880,
    minWidth: 1024,
    minHeight: 700,
    title: 'DefectLab',
    backgroundColor: '#0f172a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  // Load splash screen first
  mainWindow.loadFile(path.join(__dirname, 'loading.html'));

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  checkAndStartApp();
}

// Check if http://localhost:4200 is already ready
function isServiceReady() {
  return new Promise((resolve) => {
    const req = http.get(TARGET_URL, (res) => {
      resolve(res.statusCode >= 200 && res.statusCode < 400);
    });
    req.on('error', () => resolve(false));
    req.setTimeout(1500, () => {
      req.destroy();
      resolve(false);
    });
  });
}

async function checkAndStartApp() {
  sendStatus('Checking Docker environment...');

  // 1. Check if Docker is available
  exec('docker info', { env: customEnv }, async (dockerErr) => {
    if (dockerErr) {
      sendError('Docker is not running. Please launch Docker Desktop or OrbStack, then click Retry.');
      return;
    }

    sendStatus('Syncing configuration & checking for updates...');
    const composeFile = await syncRemoteComposeFile();
    const workingDir = path.dirname(composeFile);

    sendStatus('Checking for latest container updates from Docker Hub...');

    // 2. Automatically pull the latest images (timeout after 40s if offline)
    const pullCommand = `docker compose -f "${composeFile}" pull`;
    exec(pullCommand, { cwd: workingDir, env: customEnv, timeout: 40000 }, () => {
      // 3. Launch services (will auto-recreate containers if new images were downloaded)
      sendStatus('Starting DefectLab Docker services...');
      const upCommand = `docker compose -f "${composeFile}" up -d`;

      exec(upCommand, { cwd: workingDir, env: customEnv }, (startErr, stdout, stderr) => {
        if (startErr) {
          sendError(`Failed to start services: ${stderr || startErr.message}`);
          return;
        }

        sendStatus('Connecting to DefectLab Core...');
        pollUntilReady();
      });
    });
  });
}

function pollUntilReady(attempts = 0) {
  if (attempts > 60) { // 60 attempts * 2s = 2 minutes timeout
    sendError('Timeout waiting for DefectLab to start. Please check Docker logs.');
    return;
  }

  setTimeout(async () => {
    const ready = await isServiceReady();
    if (ready) {
      loadApp();
    } else {
      sendStatus(`Starting DefectLab components... (${attempts + 1}/60)`);
      pollUntilReady(attempts + 1);
    }
  }, 2000);
}

function loadApp() {
  sendStatus('Connected! Launching DefectLab...');
  if (mainWindow) {
    mainWindow.loadURL(TARGET_URL);
  }
}

function sendStatus(message) {
  if (mainWindow && mainWindow.webContents) {
    mainWindow.webContents.send('status-update', message);
  }
}

function sendError(errorMessage) {
  if (mainWindow && mainWindow.webContents) {
    mainWindow.webContents.send('error-occurred', errorMessage);
  }
}

ipcMain.on('retry-start', () => {
  if (mainWindow) {
    mainWindow.loadFile(path.join(__dirname, 'loading.html'));
    setTimeout(checkAndStartApp, 500);
  }
});

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});
