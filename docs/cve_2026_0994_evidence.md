# CVE-2026-0994 False-Positive Treatment Evidence

## Classification
**NOT APPLICABLE / FALSE-POSITIVE CPE MATCH**

## CVE Information
- **CVE ID:** CVE-2026-0994
- **CVE Description:** An issue in the Python implementation of Google Protobuf allows a denial-of-service or potential memory corruption via malformed JSON input targeting the `google.protobuf.json_format.ParseDict()` function.
- **Affected Module and Function:** Python module `google.protobuf.json_format`, specifically the `ParseDict()` function.

## Java Artifact Identification
- **Artifacts Flagged:**
  - `protobuf-java-3.25.5.jar` (pkg:maven/com.google.protobuf/protobuf-java@3.25.5)
  - `protobuf-java-util-3.25.5.jar` (pkg:maven/com.google.protobuf/protobuf-java-util@3.25.5)
- **Dependency-Check CPE Match:** `cpe:2.3:a:google:protobuf:3.25.5:*:*:*:*:*:*:*`
- **Match Confidence:** HIGH

## False-Positive Justification
The advisory specifically targets a vulnerability within the Python `google.protobuf` module.
The flagged artifacts are exclusively Java compiled byte-code (`.class` files) for the Java runtime.
**Evidence of absence:** The Python implementation (`json_format.py` / `ParseDict()`) is completely physically absent from both `protobuf-java-3.25.5.jar` and `protobuf-java-util-3.25.5.jar`. 
**Cause of Match:** The OWASP Dependency-Check scanner applies a broad `cpe:2.3:a:google:protobuf` identifier which encompasses all language implementations of Protobuf (C++, Java, Python, Go, etc.) rather than distinguishing by the underlying package ecosystem (PyPI vs. Maven).

## Suppression Policy
- **Owner:** Backend security maintainer
- **Review/Expiry Date:** 2026-10-01
- **Trigger for Removing Suppression:** The suppression must be removed immediately if:
  1. The advisory is expanded to explicitly include the Java implementation.
  2. The Java artifacts begin including the affected Python code.
  3. The Protobuf versions or package identifiers change.
  4. Dependency-Check corrects its internal CPE association for this specific vulnerability.
