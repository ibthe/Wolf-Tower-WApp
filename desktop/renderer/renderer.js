const $ = (id) => document.getElementById(id);
let S = null;
let logOpen = false;
let advOpen = false;
let keyOpen = null; // null: решает состояние лицензии
let justActivated = false;
let logs = [];
let rawTouched = false;
let engineDraft = null; // выбранный, но ещё не сохранённый способ
let proxyTouched = false;

function plural(n, one, few, many) {
  const m100 = n % 100;
  const m10 = n % 10;
  if (m100 >= 11 && m100 <= 14) return many;
  if (m10 === 1) return one;
  if (m10 >= 2 && m10 <= 4) return few;
  return many;
}

/** "142 дня" / "5 часов" до конца срока; null, если лицензия бессрочная или срок вышел. */
function leftText(untilMs) {
  if (typeof untilMs !== 'number') return null;
  const ms = untilMs - Date.now();
  if (ms <= 0) return null;
  const d = Math.floor(ms / 86400000);
  if (d >= 1) return `${d} ${plural(d, 'день', 'дня', 'дней')}`;
  const h = Math.max(1, Math.floor(ms / 3600000));
  return `${h} ${plural(h, 'час', 'часа', 'часов')}`;
}

function strategyLabel(s) {
  if (s === 'none') return 'без обхода';
  if (s.startsWith('raw:')) return 'своя строка';
  return s;
}

/** Кнопка «копировать»: значок меняется на галочку, подпись на «Скопировано», через 2 с всё возвращается. */
function setupCopyButton(btn, getText) {
  const lbl = btn.querySelector('.lbl');
  const normal = lbl.textContent;
  let timer = null;
  btn.addEventListener('click', async () => {
    await window.wt.copyText(getText());
    btn.classList.add('copied');
    lbl.textContent = 'Скопировано';
    clearTimeout(timer);
    timer = setTimeout(() => {
      btn.classList.remove('copied');
      lbl.textContent = normal;
    }, 2000);
  });
}

function renderChips(profile) {
  const box = $('chips');
  box.textContent = '';
  const cur = S.chosen[profile.id];
  for (const s of ['none', ...S.strategies]) {
    const b = document.createElement('button');
    b.className = 'chip' + (s === cur ? ' on' : '');
    b.disabled = S.running;
    b.textContent = strategyLabel(s);
    b.addEventListener('click', () => window.wt.setStrategy(profile.id, s));
    box.append(b);
  }
}

function renderLog() {
  $('logTitle').textContent = `Лог (${logs.length})`;
  $('logToggle').textContent = logOpen ? 'Свернуть' : 'Развернуть';
  $('logLast').hidden = logOpen;
  $('logBox').hidden = !logOpen;
  $('logLast').textContent = logs.length ? logs[logs.length - 1] : 'Пока пусто';
  if (logOpen) {
    const pre = $('logText');
    pre.textContent = logs.slice(-100).join('\n');
    pre.scrollTop = pre.scrollHeight;
  }
}

function render() {
  if (!S) return;
  const profile = S.profiles[0];
  const canRun = S.license.licensed;
  const busy = S.busy || S.probe.running;

  // ----- кнопка и статус
  const t = $('toggle');
  t.classList.toggle('on', S.running);
  t.classList.toggle('busy', busy);
  t.disabled = busy || (!S.running && !canRun);
  const head = $('status');
  head.textContent = S.running
    ? 'Защита включена'
    : S.probe.running
      ? 'Подбор стратегии…'
      : S.busy
        ? 'Проверка лицензии…'
        : 'Защита выключена';
  head.classList.toggle('on-text', S.running);
  $('hint').textContent = S.running
    ? 'WhatsApp идёт через обход'
    : !canRun
      ? 'Для запуска введите лицензионный ключ'
      : 'Нажмите кнопку, чтобы разблокировать WhatsApp';
  $('hint').classList.toggle('amber', !S.running && !canRun);

  // ----- лицензия: после активации поля ключа убираются, остаётся только срок
  const showKey = keyOpen === null ? !canRun : keyOpen;
  $('licenseChip').hidden = !(canRun && !showKey);
  $('keyCard').hidden = !showKey;
  if (canRun) {
    const left = leftText(S.license.untilMs);
    const soon = typeof S.license.untilMs === 'number' && S.license.untilMs - Date.now() < 7 * 86400000;
    $('licenseChip').classList.toggle('soon', soon);
    $('licTitle').textContent = justActivated ? 'Ключ принят' : S.license.plan === 'trial' ? 'Пробная лицензия' : 'Лицензия активна';
    $('licSub').textContent = left ? `Осталось ${left}` : 'Бессрочная';
  }
  $('licenseText').textContent = canRun ? '' : S.license.text;
  $('licenseText').hidden = canRun;
  $('deviceId').textContent = S.license.deviceId;
  $('keyCancel').hidden = !(canRun && showKey);
  $('newKeyBtn').hidden = !(canRun && !showKey);

  // ----- стратегия
  const cur = S.chosen[profile.id];
  const score = S.probe.scores && S.probe.scores[profile.id] ? ` (проверка: ${S.probe.scores[profile.id]})` : '';
  $('strategyNow').textContent = `Сейчас: ${strategyLabel(cur)}${score}`;
  $('probeBtn').disabled = S.running || S.probe.running || !canRun;
  $('probeProgress').textContent = S.probe.running ? S.probe.progress : '';
  $('probeCancel').hidden = !S.probe.running;
  $('probeReport').textContent = S.probe.report || '';
  $('probeReport').hidden = !S.probe.report;

  // ----- способ обхода
  const engView = engineDraft || S.engine;
  $('strategyCard').hidden = S.engine === 'socks5';
  $('engByedpi').classList.toggle('on', engView === 'byedpi');
  $('engSocks').classList.toggle('on', engView === 'socks5');
  $('engByedpi').disabled = S.running;
  $('engSocks').disabled = S.running;
  $('proxyBox').hidden = engView !== 'socks5';
  $('proxySave').disabled = S.running;
  if (!proxyTouched) {
    $('proxyHost').value = S.proxyHost || '';
    $('proxyPort').value = S.proxyPort ? String(S.proxyPort) : '';
  }

  // ----- дополнительно
  $('adv').hidden = !advOpen;
  $('advToggle').classList.toggle('open', advOpen);
  if (advOpen) {
    renderChips(profile);
    $('rawApply').disabled = S.running;
    if (!rawTouched && cur.startsWith('raw:')) $('rawInput').value = cur.slice(4);
  }
  $('platformNote').textContent =
    S.platform === 'darwin'
      ? 'При первом включении защиты macOS попросит пароль администратора для настройки прокси.'
      : S.platform === 'win32'
        ? 'Приложение WhatsApp из Microsoft Store системный прокси не видит: используйте WhatsApp Web в браузере.'
        : '';
  renderLog();
}

$('engByedpi').addEventListener('click', async () => {
  engineDraft = null;
  const err = await window.wt.setEngine('byedpi');
  $('proxyMsg').textContent = err || '';
  render();
});
$('engSocks').addEventListener('click', () => {
  engineDraft = 'socks5';
  render();
});
$('proxyHost').addEventListener('input', () => { proxyTouched = true; $('proxyMsg').textContent = ''; });
$('proxyPort').addEventListener('input', () => { proxyTouched = true; $('proxyMsg').textContent = ''; });
$('proxySave').addEventListener('click', async () => {
  const err = await window.wt.setEngine('socks5', $('proxyHost').value.trim(), $('proxyPort').value.trim());
  $('proxyMsg').textContent = err || 'Сохранено';
  if (!err) {
    engineDraft = null;
    proxyTouched = false;
  }
  render();
});
$('toggle').addEventListener('click', () => window.wt.toggleProtection());
$('probeBtn').addEventListener('click', () => window.wt.startProbe());
$('probeCancel').addEventListener('click', () => window.wt.cancelProbe());
$('advToggle').addEventListener('click', () => {
  advOpen = !advOpen;
  render();
});
$('newKeyBtn').addEventListener('click', () => {
  keyOpen = true;
  render();
});
$('keyCancel').addEventListener('click', () => {
  keyOpen = null;
  $('licenseMsg').textContent = '';
  render();
});
setupCopyButton($('copyId'), () => S.license.deviceId);
setupCopyButton($('logCopy'), () => logs.join('\n'));
$('paste').addEventListener('click', async () => {
  $('keyInput').value = (await window.wt.paste()).trim();
});
$('activate').addEventListener('click', async () => {
  const btn = $('activate');
  btn.disabled = true;
  btn.textContent = 'Проверка…';
  const err = await window.wt.activate($('keyInput').value);
  btn.disabled = false;
  btn.textContent = 'Активировать';
  $('licenseMsg').textContent = err || '';
  if (!err) {
    $('keyInput').value = '';
    keyOpen = null;
    justActivated = true;
    setTimeout(() => {
      justActivated = false;
      render();
    }, 3000);
    render();
  }
});
$('rawInput').addEventListener('input', () => {
  rawTouched = true;
  $('rawErr').textContent = '';
});
$('rawApply').addEventListener('click', async () => {
  const text = $('rawInput').value.trim();
  if (!text) return;
  const ok = await window.wt.setStrategy(S.profiles[0].id, 'raw:' + text);
  $('rawErr').textContent = ok ? '' : 'Допустимы только флаги обхода: -s -d -o -q -f -t -r -a и т.п.';
  if (ok) rawTouched = false;
});
$('logToggle').addEventListener('click', () => {
  logOpen = !logOpen;
  renderLog();
});
$('logClear').addEventListener('click', async () => {
  await window.wt.clearLog();
  logs = [];
  renderLog();
});
// диагностика копируется в основном процессе; значок на кнопке меняется так же, как у остальных
(() => {
  const btn = $('diag');
  const lbl = btn.querySelector('.lbl');
  const normal = lbl.textContent;
  let timer = null;
  btn.addEventListener('click', async () => {
    await window.wt.copyDiag();
    btn.classList.add('copied');
    lbl.textContent = 'Скопировано';
    clearTimeout(timer);
    timer = setTimeout(() => {
      btn.classList.remove('copied');
      lbl.textContent = normal;
    }, 2000);
  });
})();
$('licensesToggle').addEventListener('click', async () => {
  const pre = $('licensesText');
  pre.hidden = !pre.hidden;
  if (!pre.hidden && !pre.textContent) pre.textContent = await window.wt.componentLicenses();
});

window.wt.onState((s) => {
  S = s;
  render();
});
window.wt.onLog((line) => {
  logs.push(line);
  if (logs.length > 300) logs.shift();
  renderLog();
});
(async () => {
  logs = await window.wt.getLogs();
  S = await window.wt.getState();
  render();
})();
