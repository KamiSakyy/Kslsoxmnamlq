const fs = require('fs');
const h = fs.readFileSync('c18-ak-s.html', 'utf8');
let i = h.indexOf('<script type="text/javascript">');
i = h.indexOf('<script', i + 10);
i = h.indexOf('eval(function', i);
const j = h.indexOf('</script>', i);
let call = h.slice(i, j).trim();
call = call.replace(/^eval\(/, 'globalThis.D = (');
globalThis.D = null;
(0, eval)(call);
// safety: do not run payload; D holds the decoded layer
fs.writeFileSync('c18-ak-layer1.js', globalThis.D);
console.log('layer1 bytes:', globalThis.D.length);
console.log('--- head ---');
console.log(globalThis.D.slice(0, 1500));
console.log('--- tail ---');
console.log(globalThis.D.slice(-1500));
