"""Render the SSM shell template without a provider, state or AWS credentials."""

import json
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[4]
dashboard = root / "docker/grafana/provisioning/dashboards/k6-prometheus.json"
variables = {
    "prometheus_image": "prometheus:test",
    "grafana_image": "grafana:test",
    "zipkin_image": "zipkin:test",
    "grafana_secret_arn": "test-only-secret",
    "cloudmap_namespace": "cc-test.internal",
    "name_prefix": "cc-test",
    "k6_dashboard": json.dumps(json.loads(dashboard.read_text()), separators=(",", ":")),
    "dashboard_provider": (dashboard.parent / "dashboards.yml").read_text(),
    "prometheus_source": (root / "deploy/grafana/provisioning/datasources/prometheus.yml")
    .read_text().replace("http://prometheus:9090", "http://127.0.0.1:9090"),
}
template = Path(__file__).parent / "observation/start-observation.sh.tftpl"
with tempfile.TemporaryDirectory() as directory:
    expression = f"templatefile({json.dumps(str(template))}, {json.dumps(variables)})\n"
    result = subprocess.run(
        ["terraform", "console", "-no-color"], cwd=directory, input=expression,
        text=True, capture_output=True, check=True,
    )
    output = result.stdout.strip()
    # terraform console uses a heredoc for multiline strings.
    rendered = output.split("\n", 1)[1].rsplit("\nEOT", 1)[0] if output.startswith("<<EOT") else json.loads(output)
    subprocess.run(["bash", "-n"], input=rendered, text=True, check=True)
    assert "--web.enable-remote-write-receiver" in rendered
    assert "GF_SERVER_HTTP_ADDR=127.0.0.1" in rendered
    assert "uid: prometheus" in rendered
    embedded = rendered.split("<<'DASHBOARD'\n", 1)[1].split("\nDASHBOARD", 1)[0]
    assert json.loads(embedded) == json.loads(dashboard.read_text())
    document = {"schemaVersion": "2.2", "description": "Observation bootstrap", "mainSteps": [
        {"action": "aws:runShellScript", "name": "StartObservation", "inputs": {"timeoutSeconds": "900", "runCommand": [rendered]}}
    ]}
    size = len(json.dumps(document).encode())
    assert size < 64 * 1024, f"SSM document exceeds 64 KiB: {size}"
    print(f"Observation template passed: shell syntax, embedded dashboard, SSM payload {size} bytes")
