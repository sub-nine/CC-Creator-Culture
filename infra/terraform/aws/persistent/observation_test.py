"""Render the SSM shell template without a provider, state or AWS credentials."""

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

    if '--promtool' in sys.argv:
        # 실서비스에 요청하지 않고 Prometheus 자체 엔진으로 레이블 변환을 검사한다.
        config = rendered.split("<<'PROM'\n", 1)[1].split('\nPROM', 1)[0]
        discovery = config.split('    eureka_sd_configs:\n', 1)[1].split('    relabel_configs:', 1)[0]
        config = config.replace('    eureka_sd_configs:\n' + discovery,
                                '    file_sd_configs:\n      - files: ["/check/targets.json"]\n')
        directory = Path(directory)
        (directory / 'prometheus.yml').write_text(config)
        names = ['GATEWAY', 'USER-SERVICE', 'PRODUCT-SERVICE', 'ORDER-SERVICE', 'CONFIG-SERVER', 'EUREKA-SERVER']
        targets = [{'targets': ['127.0.0.1:8080'], 'labels': {
            '__meta_eureka_app_name': name, '__meta_eureka_app_instance_status': 'UP',
            '__meta_eureka_app_instance_ip_addr': '127.0.0.1',
        }} for name in names]
        targets.append({'targets': ['127.0.0.2:8080'], 'labels': {
            '__meta_eureka_app_name': 'ORDER-SERVICE', '__meta_eureka_app_instance_status': 'DOWN',
            '__meta_eureka_app_instance_ip_addr': '127.0.0.2',
        }})
        (directory / 'targets.json').write_text(json.dumps(targets))
        # Linux에서도 컨테이너의 nobody 사용자가 검증용 파일을 읽을 수 있게 한다.
        directory.chmod(0o755)
        for filename in ('prometheus.yml', 'targets.json'):
            (directory / filename).chmod(0o644)
        command = ['docker', 'run', '--rm', '--network', 'none', '--entrypoint', 'promtool',
                   '-v', f'{directory}:/check:ro', 'prom/prometheus:v3.13.1', 'check']
        subprocess.run(command + ['config', '/check/prometheus.yml'], check=True)
        result = subprocess.run(command + ['service-discovery', '--timeout=1s',
                                '/check/prometheus.yml', 'spring-apps'],
                                capture_output=True, text=True, check=True)
        labels = [target['labels'] for target in json.loads(result.stdout) if target.get('labels')]
        assert {label['job'] for label in labels} == {name.lower() for name in names[:4]}
        assert all(label['__address__'] == '127.0.0.1:9090' for label in labels)
        print('Prometheus relabel passed: service jobs, management port, core/DOWN exclusion')
