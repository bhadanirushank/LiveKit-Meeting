const fs = require('fs');

const raw = fs.readFileSync('D:\\LiveKit-Meeting\\trivy-backend.json', 'utf8');
const data = JSON.parse(raw);

data.Results?.forEach(result => {
    result.Vulnerabilities?.forEach(vuln => {
        if (vuln.Severity === 'HIGH') {
            console.log(`CVE: ${vuln.VulnerabilityID}`);
            console.log(`Package: ${vuln.PkgName}`);
            console.log(`Installed: ${vuln.InstalledVersion}`);
            console.log(`Fixed: ${vuln.FixedVersion}`);
            console.log(`Target: ${result.Target}`);
            console.log('-'.repeat(40));
        }
    });
});
