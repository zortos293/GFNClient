import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const pkgRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(pkgRoot, '..');

// Helper to run commands
function runCmd(cmd, cwd) {
  console.log(`Running: ${cmd}`);
  execSync(cmd, { cwd, stdio: 'inherit' });
}

try {
  // 1. Read package version
  const pkgPath = path.join(pkgRoot, 'package.json');
  const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
  const version = pkg.version;
  console.log(`Building webOS IPK for version: ${version}`);

  // 2. Read appinfo.json
  const appinfoPath = path.join(pkgRoot, 'webos', 'appinfo.json');
  if (!fs.existsSync(appinfoPath)) {
    throw new Error(`appinfo.json not found at ${appinfoPath}`);
  }
  const appinfo = JSON.parse(fs.readFileSync(appinfoPath, 'utf8'));
  appinfo.version = version;

  // 3. Ensure Vite build (dist) exists
  const distPath = path.join(pkgRoot, 'dist');
  if (!fs.existsSync(distPath)) {
    throw new Error(`dist folder not found at ${distPath}. Run "npm run build" first.`);
  }

  // 4. Create staging directories
  const tempDir = path.join(pkgRoot, 'dist-webos-temp');
  if (fs.existsSync(tempDir)) {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
  fs.mkdirSync(tempDir, { recursive: true });

  const dataDir = path.join(tempDir, 'data');
  const appDir = path.join(dataDir, 'usr', 'palm', 'applications', appinfo.id);
  fs.mkdirSync(appDir, { recursive: true });

  // 5. Copy dist contents and inject manifest/icon
  console.log(`Staging application files...`);
  fs.cpSync(distPath, appDir, { recursive: true });
  fs.writeFileSync(path.join(appDir, 'appinfo.json'), JSON.stringify(appinfo, null, 2));

  const logoPath = path.join(repoRoot, 'logo.png');
  if (fs.existsSync(logoPath)) {
    fs.copyFileSync(logoPath, path.join(appDir, 'icon.png'));
  } else {
    console.warn(`Warning: logo.png not found at ${logoPath}. No icon copied.`);
  }

  // 6. Create control configuration file
  const controlDir = path.join(tempDir, 'control');
  fs.mkdirSync(controlDir, { recursive: true });
  const controlContent = `Package: ${appinfo.id}
Version: ${version}
Description: ${appinfo.description || 'OpenNOW webOS App'}
Section: misc
Priority: optional
Architecture: all
Maintainer: ${appinfo.vendor || 'OpenCloudGaming'}
`;
  fs.writeFileSync(path.join(controlDir, 'control'), controlContent);

  // 7. Compress control and data tarballs
  console.log(`Compressing tarballs...`);
  runCmd(`tar -czf control.tar.gz -C control control`, tempDir);
  runCmd(`tar -czf data.tar.gz -C data usr`, tempDir);

  // 8. Assemble AR (.ipk) package
  console.log(`Assembling AR package...`);
  const debianBinary = Buffer.from('2.0\n', 'ascii');
  const controlTarGz = fs.readFileSync(path.join(tempDir, 'control.tar.gz'));
  const dataTarGz = fs.readFileSync(path.join(tempDir, 'data.tar.gz'));

  const ipkPath = path.join(pkgRoot, 'dist-release', `${appinfo.id}_${version}_all.ipk`);
  fs.mkdirSync(path.dirname(ipkPath), { recursive: true });

  const files = [
    { name: 'debian-binary', data: debianBinary },
    { name: 'control.tar.gz', data: controlTarGz },
    { name: 'data.tar.gz', data: dataTarGz }
  ];

  function createAr(filesList, outPath) {
    const HEADER = Buffer.from('!<arch>\n', 'ascii');
    const buffers = [HEADER];

    for (const file of filesList) {
      const filename = file.name;
      const content = file.data;

      const pad = (str, len) => str.padEnd(len, ' ');

      const nameBuf = Buffer.alloc(16, ' ');
      nameBuf.write(filename, 0, 'ascii');

      const mtime = '0';
      const owner = '0';
      const group = '0';
      const mode = '100644';
      const size = content.length.toString();

      const header = Buffer.concat([
        nameBuf,
        Buffer.from(pad(mtime, 12), 'ascii'),
        Buffer.from(pad(owner, 6), 'ascii'),
        Buffer.from(pad(group, 6), 'ascii'),
        Buffer.from(pad(mode, 8), 'ascii'),
        Buffer.from(pad(size, 10), 'ascii'),
        Buffer.from('`\n', 'ascii')
      ]);

      buffers.push(header);
      buffers.push(content);

      if (content.length % 2 !== 0) {
        buffers.push(Buffer.from('\n', 'ascii'));
      }
    }

    fs.writeFileSync(outPath, Buffer.concat(buffers));
  }

  createAr(files, ipkPath);
  console.log(`Successfully built: ${ipkPath}`);

  // Cleanup temp files
  fs.rmSync(tempDir, { recursive: true, force: true });
  console.log(`Cleaned up temporary staging files.`);
} catch (error) {
  console.error(`Build failed:`, error);
  process.exit(1);
}
