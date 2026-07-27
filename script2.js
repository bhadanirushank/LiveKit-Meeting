const fs = require('fs');
const path = require('path');

let file = path.join('backend', 'src', 'test', 'kotlin', 'com', 'jbcoder', 'meeting', 'ComposeIntegrationTest.kt');
let content = fs.readFileSync(file, 'utf8');

content = content.replace(/val \((deviceId[A-Za-z0-9_]*), \\) = client\.bootstrapDevice\(\)/g, 'val (, _token) = client.bootstrapDevice()');
content = content.replace(/val \((deviceId[A-Za-z0-9_]*), 1_token\) = client\.bootstrapDevice\(\)/g, 'val (, _token) = client.bootstrapDevice()');

fs.writeFileSync(file, content);
