const fs = require('fs');
const path = require('path');

let file = path.join('backend', 'src', 'test', 'kotlin', 'com', 'jbcoder', 'meeting', 'ComposeIntegrationTest.kt');
let content = fs.readFileSync(file, 'utf8');

if (!content.includes('suspend fun io.ktor.client.HttpClient.bootstrapDevice()')) {
    const helper = `
    private suspend fun io.ktor.client.HttpClient.bootstrapDevice(): Pair<String, String> {
        val res = post("/api/v1/session/bootstrap") {
            io.ktor.client.request.contentType(io.ktor.http.ContentType.Application.Json)
            io.ktor.client.request.setBody("""{"deviceModel":"IntegrationTest", "osVersion":"1.0", "appVersion":"1.0.0"}""")
        }
        val body = kotlinx.serialization.json.Json.parseToJsonElement(res.bodyAsText()).jsonObject
        return Pair(body["deviceSessionId"]!!.jsonPrimitive.content, body["accessToken"]!!.jsonPrimitive.content)
    }
`;
    content = content.replace('class ComposeIntegrationTest {', 'class ComposeIntegrationTest {' + helper);
}

const deviceIdRegex = /val (deviceId[A-Za-z0-9_]*) = UUID\.randomUUID\(\)\.toString\(\)/g;
content = content.replace(deviceIdRegex, 'val ($1, $1_token) = client.bootstrapDevice()');

let lines = content.split('\n');
let currentDeviceId = 'deviceId';
for (let i = 0; i < lines.length; i++) {
    let m = lines[i].match(/val (deviceId[A-Za-z0-9_]*), (deviceId[A-Za-z0-9_]*_token)/);
    if (m) {
        currentDeviceId = m[1];
    }
    
    if (lines[i].includes('/join-request") {')) {
        let j = i + 1;
        while (j < i + 10 && j < lines.length) {
            let m2 = lines[j].match(/"deviceSessionId":\s*"\$([a-zA-Z0-9_]+)"/);
            if (m2) {
                currentDeviceId = m2[1];
                break;
            }
            j++;
        }
        lines[i] = lines[i] + `\n              header(io.ktor.http.HttpHeaders.Authorization, "Bearer \\$${currentDeviceId}_token")`;
    }
    
    if (lines[i].includes('/livekit-token") {')) {
        let j = i + 1;
        while (j < i + 10 && j < lines.length) {
            let m2 = lines[j].match(/"deviceSessionId":\s*"\$([a-zA-Z0-9_]+)"/);
            if (m2) {
                currentDeviceId = m2[1];
                break;
            }
            j++;
        }
        lines[i] = lines[i] + `\n              header(io.ktor.http.HttpHeaders.Authorization, "Bearer \\$${currentDeviceId}_token")\n              header("Idempotency-Key", java.util.UUID.randomUUID().toString())`;
    }
}

fs.writeFileSync(file, lines.join('\n'));
