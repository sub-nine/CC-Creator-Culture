"""Start RDS before Terraform; stop existing workloads without creating resources."""
import json
from pathlib import Path
import subprocess
import sys


def aws(*args):
    output = subprocess.check_output(['aws', *args, '--output', 'json'], text=True)
    return json.loads(output) if output.strip() else {}


def database(identifier, running):
    args = ['--db-instance-identifier', identifier]
    state = aws('rds', 'describe-db-instances', *args)['DBInstances'][0]['DBInstanceStatus']
    if state == 'stopping':
        aws('rds', 'wait', 'db-instance-stopped', *args)
        state = 'stopped'
    if running:
        if state == 'stopped':
            aws('rds', 'start-db-instance', *args)
        aws('rds', 'wait', 'db-instance-available', *args)
    elif state != 'stopped':
        if state != 'available':
            aws('rds', 'wait', 'db-instance-available', *args)
        aws('rds', 'stop-db-instance', *args)
        aws('rds', 'wait', 'db-instance-stopped', *args)


def stop_services(cluster):
    description = aws('ecs', 'describe-clusters', '--clusters', cluster)
    failures = description.get('failures', [])
    if any(item['reason'] != 'MISSING' for item in failures):
        raise RuntimeError(failures)
    if not description['clusters'] or description['clusters'][0]['status'] == 'INACTIVE':
        return
    targets = aws('application-autoscaling', 'describe-scalable-targets',
                  '--service-namespace', 'ecs')['ScalableTargets']
    for target in targets:
        if target['ResourceId'].startswith(f'service/{cluster}/'):
            aws('application-autoscaling', 'register-scalable-target', '--service-namespace', 'ecs',
                '--resource-id', target['ResourceId'], '--scalable-dimension', target['ScalableDimension'],
                '--min-capacity', '0', '--max-capacity', str(target['MaxCapacity']),
                '--suspended-state', 'DynamicScalingInSuspended=true,DynamicScalingOutSuspended=true,ScheduledScalingSuspended=true')
    services = aws('ecs', 'list-services', '--cluster', cluster)['serviceArns']
    for service in services:
        aws('ecs', 'update-service', '--cluster', cluster, '--service', service, '--desired-count', '0')
    for service in services:
        aws('ecs', 'wait', 'services-stable', '--cluster', cluster, '--services', service)
    if aws('ecs', 'list-tasks', '--cluster', cluster, '--desired-status', 'RUNNING')['taskArns']:
        raise RuntimeError('Running tasks remain in the runtime cluster')


def main(action, config):
    if action not in ('start', 'stop'):
        raise ValueError('Expected start or stop')
    errors = []
    if action == 'stop':
        try:
            stop_services(config['name_prefix'])
        except Exception as error:
            errors.append(error)
    for identifier in config['rds_instances'].values():
        try:
            database(identifier, action == 'start')
        except Exception as error:
            errors.append(error)
    if action == 'stop':
        try:
            ids = [config['kafka_instance_id'], config['observation_instance_id']]
            aws('ec2', 'stop-instances', '--instance-ids', *ids)
            aws('ec2', 'wait', 'instance-stopped', '--instance-ids', *ids)
        except Exception as error:
            errors.append(error)
    if errors:
        raise RuntimeError('; '.join(map(str, errors)))


if __name__ == '__main__':
    main(sys.argv[1], json.loads(Path(sys.argv[2]).read_text())['persistent_config'])
