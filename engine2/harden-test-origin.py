"""Apply the already documented p3c reload-safe origin/seeding strategy to older helpers.
This changes no test assertions or app source. Invoke on a fresh extracted acceptance suite.
"""
from pathlib import Path
import sys
p=Path(sys.argv[1])/'tests/helpers.js'
s=p.read_text();start=s.index('async function openApp(');end=s.index('const nav =', start)
replacement='''async function openApp(page, { seed = null, raw = null } = {}) {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.route(/fonts\\.(googleapis|gstatic)\\.com/, (r) => r.abort());
  await page.clock.setFixedTime(new Date('2026-10-27T09:00:00+08:00'));
  await page.addInitScript(([k, seed, raw]) => {
    if (localStorage.getItem('__p3c_seeded')) return;
    localStorage.clear(); localStorage.setItem('__p3c_seeded', '1');
    if (raw !== null) localStorage.setItem(k, raw); else if (seed) localStorage.setItem(k, JSON.stringify(seed));
  }, [K, seed, raw]);
  await page.route('http://stridelab.test/app.html', r => r.fulfill({path:FILE,contentType:'text/html; charset=utf-8'}));
  await page.route('http://stridelab.test/assets/*', r => {
    const name=new URL(r.request().url()).pathname.split('/').pop();
    return r.fulfill({path:path.join(path.dirname(FILE),'assets',name)});
  });
  await page.goto('http://stridelab.test/app.html');
  await page.waitForSelector('#app h1'); page.__errors=errors; return errors;
}
'''
p.write_text(s[:start]+replacement+s[end:])
print('Applied reload-safe HTTP origin and durable seed flag to legacy test helper; assertions unchanged')
