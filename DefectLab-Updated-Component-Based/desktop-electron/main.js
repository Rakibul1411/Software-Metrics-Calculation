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

function getProjectRoot() {
  // If running from source, project root is parent directory
  return path.resolve(__dirname, '..');
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
  sendStatus('Checking services status...');

  const alreadyRunning = await isServiceReady();
  if (alreadyRunning) {
    loadApp();
    return;
  }

  // Check if Docker is available
  exec('docker info', { env: customEnv }, (dockerErr) => {
    if (dockerErr) {
      sendError('Docker is not running. Please launch Docker Desktop or OrbStack, then click Retry.');
      return;
    }

    sendStatus('Starting DefectLab Docker services...');
    const projectDir = getProjectRoot();
    const command = `docker compose -f ${PROD_COMPOSE_FILE} up -d`;

    exec(command, { cwd: projectDir, env: customEnv }, (startErr, stdout, stderr) => {
      if (startErr) {
        sendError(`Failed to start services: ${stderr || startErr.message}`);
        return;
      }

      sendStatus('Waiting for services to become healthy...');
      pollUntilReady();
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
