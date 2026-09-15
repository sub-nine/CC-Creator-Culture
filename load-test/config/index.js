import local from './environments/local.js';
import dev from './environments/dev.js';
import prod from './environments/prod.js';

const environments = { local, dev, prod };
const target = __ENV.TARGET || 'local';

if (!environments[target]) {
  throw new Error(`알 수 없는 TARGET: ${target} (local/dev/prod 중 하나여야 함)`);
}

export default environments[target];
