"""Adopt an existing API DNS record without deleting or changing it."""
import json
import os
import subprocess
import urllib.parse
import urllib.request


def main():
    terraform = ['terraform', '-chdir=' + os.environ['TF_DIR']]
    addresses = subprocess.check_output([*terraform, 'state', 'list'], text=True).splitlines()
    if 'cloudflare_dns_record.api' in addresses:
        return
    zone = os.environ['TF_VAR_cloudflare_zone_id']
    hostname = os.environ['TF_VAR_api_hostname']
    query = urllib.parse.urlencode({'name': hostname, 'per_page': 100})
    request = urllib.request.Request(
        f'https://api.cloudflare.com/client/v4/zones/{zone}/dns_records?{query}',
        headers={'Authorization': 'Bearer ' + os.environ['CLOUDFLARE_API_TOKEN']})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.load(response)
    if not data.get('success') or data.get('result_info', {}).get('total_pages', 1) > 1:
        raise RuntimeError('Unable to enumerate API DNS records')
    records = [r for r in data['result'] if r['name'] == hostname and r['type'] in ('A', 'AAAA', 'CNAME')]
    if not records:
        return
    if len(records) != 1:
        raise RuntimeError('Multiple API DNS records exist; review before import')
    subprocess.run([*terraform, 'import', '-input=false', '-no-color',
                    '-var', 'release_sha=' + os.environ['RELEASE_SHA'],
                    '-var', 'app_running=' + os.environ['APP_RUNNING'],
                    'cloudflare_dns_record.api', zone + '/' + records[0]['id']], check=True)


if __name__ == '__main__':
    main()
