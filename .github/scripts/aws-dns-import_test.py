"""Check DNS adoption, no-op and ambiguous-record handling without cloud calls."""
import importlib.util
import io
import json
import os
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('dns', Path(__file__).with_name('aws-dns-import.py'))
dns = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dns)
env = dict(TF_DIR='runtime', TF_VAR_cloudflare_zone_id='zone', TF_VAR_api_hostname='api.test',
           CLOUDFLARE_API_TOKEN='test', RELEASE_SHA='a' * 40, APP_RUNNING='true')
record = {'name': 'api.test', 'type': 'CNAME', 'id': 'record'}
with patch.dict(os.environ, env), patch.object(dns.subprocess, 'check_output', return_value=''), \
        patch.object(dns.subprocess, 'run') as run:
    for records in [[], [record], [record, dict(record, id='second')]]:
        run.reset_mock()
        with patch.object(dns.urllib.request, 'urlopen', return_value=io.StringIO(json.dumps({'success': True, 'result': records}))):
            if len(records) == 2:
                try:
                    dns.main()
                except RuntimeError:
                    pass
                else:
                    raise AssertionError('Ambiguous DNS must fail closed')
                run.assert_not_called()
            else:
                dns.main()
                assert run.call_count == len(records)
                if records:
                    assert run.call_args.args[0][-2:] == ['cloudflare_dns_record.api', 'zone/record']
    with patch.object(dns.subprocess, 'check_output', return_value='cloudflare_dns_record.api\n'), \
            patch.object(dns.urllib.request, 'urlopen') as request:
        dns.main()
        request.assert_not_called()
print('Existing API DNS import checks passed')
