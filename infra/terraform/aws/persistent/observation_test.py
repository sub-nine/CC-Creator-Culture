"""Render the SSM shell template without a provider, state or AWS credentials."""

import base64
import json
from pathlib import Path
import subprocess
import sys
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
    "k6_dashboard": base64.b64encode(
        json.dumps(json.loads(dashboard.read_text()), separators=(",", ":")).encode()
    ).decode(),
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
    assert "{{" not in rendered, "SSM command contains undeclared parameter expressions"
    dashboard_directory = Path(directory) / "grafana/dashboards"
    dashboard_directory.mkdir(parents=True)
    restore_dashboard = rendered.split("PROVIDER\n", 1)[1].split("\nraw=", 1)[0]
    subprocess.run(
        ["bash", "-c", 'ETC_ROOT="$1"\n' + restore_dashboard, "test", directory], check=True,
    )
    assert json.loads((dashboard_directory / dashboard.name).read_text()) == json.loads(dashboard.read_text())
    document = {"schemaVersion": "2.2", "description": "Observation bootstrap", "mainSteps": [
        {"action": "aws:runShellScript", "name": "StartObservation", "inputs": {"timeoutSeconds": "900", "runCommand": [rendered]}}
    ]}
    size = len(json.dumps(document).encode())
    assert size < 64 * 1024, f"SSM document exceeds 64 KiB: {size}"
    print(f"Observation template passed: shell syntax, SSM payload {size} bytes")

    if '--promtool' in sys.argv:
        config = rendered.split("<<'PROM'\n", 1)[1].split('\nPROM', 1)[0]
        directory = Path(directory)
        config_file = directory / 'prometheus.yml'
        config_file.write_text(config)
        # Linux에서도 컨테이너의 nobody 사용자가 검증용 파일을 읽을 수 있게 한다.
        directory.chmod(0o755)
        config_file.chmod(0o644)
        subprocess.run([
            'docker', 'run', '--rm', '--network', 'none', '--entrypoint', 'promtool',
            '-v', f'{directory}:/check:ro', 'prom/prometheus:v3.13.1',
            'check', 'config', '/check/prometheus.yml',
        ], check=True)
