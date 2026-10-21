const $ = (id) => document.getElementById(id);
let S = null;
let logOpen = false;
let keyOpen = null; // null: решает состояние лицензии
let logs = [];

function el(tag, attrs = {}, ...children) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') e.className = v;
    else if (k === 'onclick') e.addEventListener('click', v);
    else if (k === 'checked' || k === 'disabled') e[k] = !!v;
    else e.setAttribute(k, v);
  }
  for (const c of children) e.append(c);
  return e;
}

function renderServices() {
  const box = $('services');
  box.textContent = '';
  for (const p of S.profiles) {
    const on = S.enabled.includes(p.id);
    const sw = el(
      'label',
      { class: 'switch' },
      el('input', {
        type: 'checkbox',
        checked: on,
        disabled: S.running,
        onclick: (e) => window.wt.toggleProfile(p.id, e.target.checked),
      }),
      el('span', { class: 'slider' })
    );
    const card = el('section', { class: 'card' }, el('div', { class: 'svc-head' }, el('span', { class: 'svc-name' }, p.name), sw));
    if (on) {
      const cur = S.chosen[p.id];
      const score = S.probe.scores && S.probe.scores[p.id] ? ` (проверка: ${S.probe.scores[p.id]})` : '';
      card.append(el('div', { class: 'small muted' }, 'Стратегия: ' + (cur === 'none' ? 'без обхода' : cur) + score));
      const chips = el('div', { class: 'chips' });
      for (const s of ['none', ...S.strategies]) {
        chips.append(
          el(
            'button',
            { class: 'chip' + (s === cur ? ' on' : ''), disabled: S.running, onclick: () => window.wt.setStrategy(p.id, s) },
            s === 'none' ? 'без обхода' : s
          )
        );
      }
      card.append(chips);
    }
    box.append(card);
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
  $('status').textContent = S.running ? 'Защита включена' : 'Защита выключена';
  const canRun = S.license.licensed;
  const t = $('toggle');
  t.textContent = S.running ? 'Остановить' : S.busy ? 'Подождите…' : 'Запустить';
  t.disabled = S.busy || S.probe.running || (!S.running && !canRun);
  $('hint').textContent =
    !S.running && S.enabled.length === 0 ? 'Включите хотя бы один сервис ниже' : '';

  $('licenseText').textContent = S.license.text;
  $('deviceId').textContent = S.license.deviceId;
  const showKey = keyOpen === null ? !S.license.licensed : keyOpen;
  $('keyBox').hidden = !showKey;
  $('newKeyBtn').hidden = !(S.license.licensed && !showKey);

  renderServices();

  const probeBtn = $('probeBtn');
  probeBtn.disabled = S.running || S.probe.running || S.enabled.length === 0 || !canRun;
  $('probeProgress').textContent = S.probe.running ? S.probe.progress : '';
  $('probeCancel').hidden = !S.probe.running;
  $('probeReport').textContent = S.probe.report || '';

  $('tgCard').hidden = !S.enabled.includes('telegram');
  $('tgPort').textContent = S.port;

  $('platformNote').textContent =
    S.platform === 'darwin'
      ? 'При первом включении защиты macOS попросит пароль администратора для настройки прокси.'
      : S.platform === 'win32'
        ? 'Универсальные приложения Microsoft Store могут не видеть прокси: используйте браузер.'
        : '';
  renderLog();
}

$('toggle').addEventListener('click', () => window.wt.toggleProtection());
$('probeBtn').addEventListener('click', () => window.wt.startProbe());
$('probeCancel').addEventListener('click', () => window.wt.cancelProbe());
$('newKeyBtn').addEventListener('click', () => {
  keyOpen = true;
  render();
});
$('copyId').addEventListener('click', async () => {
  await window.wt.copyText(S.license.deviceId);
  $('licenseMsg').textContent = 'ID скопирован';
});
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
  $('licenseMsg').textContent = err || 'Ключ принят';
  if (!err) {
    $('keyInput').value = '';
    keyOpen = null;
  }
});
$('tgAdd').addEventListener('click', () => window.wt.telegramAdd());
$('tgCopy').addEventListener('click', () => window.wt.copyText(`127.0.0.1:${S.port}`));
$('logToggle').addEventListener('click', () => {
  logOpen = !logOpen;
  renderLog();
});
$('logCopy').addEventListener('click', () => window.wt.copyText(logs.join('\n')));
$('logClear').addEventListener('click', async () => {
  await window.wt.clearLog();
  logs = [];
  renderLog();
});
$('diag').addEventListener('click', async () => {
  await window.wt.copyDiag();
  $('diagMsg').textContent = 'Диагностика скопирована в буфер обмена: вставьте её в сообщение поддержке (без IP-адресов, трафика и ключа).';
});
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
