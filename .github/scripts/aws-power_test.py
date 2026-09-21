"""Exercise the stopped-RDS failure and cleanup of a partially deployed runtime."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('power', Path(__file__).with_name('aws-power.py'))
power = importlib.util.module_from_spec(spec)
spec.loader.exec_module(power)
calls = []


def fake(*args):
    calls.append(args)
    if args[:2] == ('rds', 'describe-db-instances'):
        return {'DBInstances': [{'DBInstanceStatus': 'stopped'}]}
    if args[:2] == ('ecs', 'describe-clusters'):
        return {'clusters': [], 'failures': [{'reason': 'MISSING'}]}
    return {}


power.aws = fake
power.database('db', True)
assert [c[:2] for c in calls] == [('rds', 'describe-db-instances'), ('rds', 'start-db-instance'), ('rds', 'wait')]
calls.clear()
config = {'name_prefix': 'cc-test', 'rds_instances': {'user': 'db'},
          'kafka_instance_id': 'kafka', 'observation_instance_id': 'observation'}
power.main('stop', config)
assert not any('start-db-instance' in c or 'stop-db-instance' in c for c in calls)
assert ('ec2', 'stop-instances', '--instance-ids', 'kafka', 'observation') in calls
assert not any('create' in word for c in calls for word in c)


def failed_ecs(*args):
    if args[0] == 'ecs':
        raise RuntimeError('ECS unavailable')
    return fake(*args)


power.aws = failed_ecs
calls.clear()
try:
    power.main('stop', config)
except RuntimeError as error:
    assert 'ECS unavailable' in str(error)
else:
    raise AssertionError('Cleanup failures must fail the workflow')
assert any(c[:2] == ('ec2', 'stop-instances') for c in calls)
assert any(c[:2] == ('rds', 'describe-db-instances') for c in calls)
print('RDS start ordering and partial deployment stop checks passed')


# A real stop must poll DescribeDBInstances; RDS has no stopped CLI waiter.
from unittest.mock import patch
for running, states in [(False, ['available', 'stopping', 'stopped']),
                        (True, ['stopping', 'stopping', 'stopped'])]:
    calls.clear()
    statuses = iter(states)
    def transition(*args):
        calls.append(args)
        if args[:2] == ('rds', 'describe-db-instances'):
            return {'DBInstances': [{'DBInstanceStatus': next(statuses)}]}
        if args[:2] == ('rds', 'wait'):
            assert args[2] == 'db-instance-available'
        return {}
    power.aws = transition
    with patch.object(power.time, 'sleep'):
        power.database('db', running)
    assert any(c[1] == ('start-db-instance' if running else 'stop-db-instance') for c in calls)

for state, expected in [('stopping', TimeoutError), ('failed', RuntimeError)]:
    power.aws = lambda *args: {'DBInstances': [{'DBInstanceStatus': state}]}
    with patch.object(power.time, 'sleep'):
        try:
            power.wait_database_stopped('db')
        except expected:
            pass
        else:
            raise AssertionError('RDS wait must fail on timeout or unexpected state')
print('RDS stop polling, restart, timeout and failure checks passed')
