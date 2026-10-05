const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const http = require('http');
const { exec } = require('child_process');

let mainWindow;
const TARGET_URL = 'http://localhost:4200';
const PROD_COMPOSE_FILE = 'docker-compose.prod.yml';

// Extended PATH for Mac & Linux so Electron finds docker/orbstack
const customEnv = {
  ...process.env,
  PATH: `${process.env.HOME}/.orbstack/bin:/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:${process.env.PATH || ''}`
};

function getComposeFilePath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'docker-compose.prod.yml');
  }
  return path.join(__dirname, 'docker-compose.prod.yml');
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
  exec('docker info', { env: customEnv }, (dockerErr) => {
    if (dockerErr) {
      sendError('Docker is not running. Please launch Docker Desktop or OrbStack, then click Retry.');
      return;
    }

    const composeFile = getComposeFilePath();
    const workingDir = path.dirname(composeFile);
    sendStatus('Checking for latest updates from Docker Hub...');

    // 2. Automatically pull the latest images (timeout after 30s if offline)
    const pullCommand = `docker compose -f "${composeFile}" pull`;
    exec(pullCommand, { cwd: workingDir, env: customEnv, timeout: 30000 }, () => {
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
