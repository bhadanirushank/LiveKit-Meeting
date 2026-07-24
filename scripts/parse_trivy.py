import json
import sys

def parse_trivy():
    with open(r'D:\LiveKit-Meeting\reports\trivy-backend-new.json', 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    for result in data.get("Results", []):
        for vuln in result.get("Vulnerabilities", []):
            if vuln.get("Severity") == "HIGH":
                print(f"CVE: {vuln.get('VulnerabilityID')}")
                print(f"Package: {vuln.get('PkgName')}")
                print(f"Installed: {vuln.get('InstalledVersion')}")
                print(f"Fixed: {vuln.get('FixedVersion')}")
                print(f"Source: {vuln.get('DataSource', {}).get('Name')}")
                print(f"Layer: {result.get('Target')}")
                print("-" * 40)

if __name__ == "__main__":
    parse_trivy()
