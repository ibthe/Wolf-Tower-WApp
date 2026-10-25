const fs = require('fs');

// Имена стратегий -> аргументы ByeDPI. Те же, что в Android (StrategyArgs.kt).
const LIBRARY = {
  split: ['-s1+s'],
  disorder: ['-d1+s'],
  oob: ['-o1+s'],
  disoob: ['-q1+s'],
  'split+tlsrec': ['-s1+s', '-r1+s'],
  'disorder+tlsrec': ['-d1+s', '-r1+s'],
  'oob+tlsrec': ['-o1+s', '-r1+s'],
  'fake+disorder': ['-f-1', '-t', '8', '-d1+s'],
  'fake+oob': ['-f-1', '-t', '8', '-o1+s'],
  'fake+split': ['-f-1', '-t', '8', '-s1+s'],
  'fake+tlsrec': ['-f-1', '-t', '8', '-r1+s'],
  'fake4+disorder': ['-f-1', '-t', '4', '-d1+s'],
  'fake12+disorder': ['-f-1', '-t', '12', '-d1+s'],
  // Абсолютные смещения работают и на трафике без SNI (WhatsApp-чат идёт по Noise, не по TLS).
  disorder0: ['-d1'],
  split0: ['-s1'],
  // Цепочка мелких фрагментов + 1 поддельный UDP-пакет (-a1) для звонков.
  chain: ['-d1', '-s1+s', '-s3+s', '-s6+s', '-s9+s', '-s12+s', '-s15+s', '-s20+s', '-s30+s'],
  'chain+udp': ['-d1', '-s1+s', '-s3+s', '-s6+s', '-s9+s', '-s12+s', '-s15+s', '-s20+s', '-s30+s', '-a1'],
  'disorder0+udp': ['-d1', '-a1'],
  'split+udp': ['-s1+s', '-a1'],
  'disorder+udp': ['-d1+s', '-a1'],
  // Каталог для WhatsApp: сообщество (ByeByeDPI/ByeDPI, 4PDA) и свои варианты; проверены на ciadpi из third_party
  'abs+udp': ['-d1', '-s3', '-s6', '-s10', '-s16', '-s24', '-a1'], // своя: абсолютные смещения, режет и TLS, и чат без SNI
  'abs-sni+udp': ['-d1', '-s3', '-s6', '-s10', '-s16', '-s24', '-s1+s', '-s3+s', '-s6+s', '-s12+s', '-s20+s', '-a1'], // своя: абсолютные + от SNI
  'chain-d+udp': ['-d1', '-s1+s', '-d3+s', '-s6+s', '-d9+s', '-s12+s', '-d15+s', '-s20+s', '-d25+s', '-s30+s', '-d35+s', '-a1'], // сообщество (4PDA), whatsapp.com
  'chain-r+udp': ['-d1', '-d3+s', '-s6+s', '-d9+s', '-s12+s', '-d15+s', '-s20+s', '-d25+s', '-s30+s', '-d35+s', '-r1+s', '-S', '-a1'], // сообщество (ByeByeDPI, список по умолчанию)
  'light+udp': ['-d1', '-r1+s', '-a1'], // byebyedpi.org, лёгкая
  'abs-fake8+udp': ['-d1', '-s3', '-s6', '-s10', '-s16', '-s24', '-f-1', '-t8', '-a1'], // своя: абсолютные + фейк TTL 8 в конце
  'abs-split+udp': ['-s1', '-s3', '-s5', '-s8', '-s12', '-s18', '-s26', '-a1'], // своя: только разбиение
  'drip+udp': ['-s1', '-s2', '-s3', '-s4', '-s5', '-s6', '-s7', '-s8', '-s12', '-s20', '-a1'], // своя: мелкие кусочки по 1 байту
  'abs-disorder+udp': ['-d1', '-d4', '-d9', '-d16', '-s24', '-a1'], // своя: перестановка частей
  'abs-oob+udp': ['-o1', '-s4', '-s9', '-s15', '-s24', '-a1'], // своя: OOB + разбиение
  'abs-disoob+udp': ['-q1', '-s4', '-s9', '-s15', '-s24', '-a1'], // своя: обратный OOB + разбиение
  'abs-fake5+udp': ['-d1', '-s3', '-s6', '-s10', '-s16', '-s24', '-f-1', '-t5', '-a1'], // своя: абсолютные + фейк TTL 5 в конце
  'abs-udp3': ['-d1', '-s3', '-s6', '-s10', '-s16', '-s24', '-a3'], // своя: 3 поддельных UDP-пакета для звонков
  'chain+udp2': ['-d1', '-s1+s', '-s3+s', '-s6+s', '-s9+s', '-s12+s', '-s15+s', '-s20+s', '-s30+s', '-a2'], // ваша строка, 2 поддельных UDP-пакета (как repeats=2 в zapret для STUN)
  'tlsrec+udp': ['-r5+s', '-s25+s', '-a1'], // сообщество (Ростелеком), только TLS
  'tlsrec-abs+udp': ['-d1', '-r5+s', '-s25+s', '-a1'], // своя: Ростелеком + абсолютное смещение
  'tlsrec-s50+udp': ['-s50', '-r5+s', '-s50+s', '-a1', '-r10+s'], // сообщество (Ростелеком)
  'oob-r+udp': ['-q1', '-r25+s', '-a1'], // сообщество (ByeDPI #423)
  'oob2+udp': ['-o2', '-o25+s', '-a1'], // сообщество (ByeDPIAndroid #392, WhatsApp, январь 2026)
  'fake8+udp': ['-f-1', '-t8', '-a1'], // byebyedpi.org, агрессивная
  'mix+udp3': ['-a3', '-o1', '-d1', '-r1+s', '-s1+s', '-s3+s', '-s6+s', '-s9+s', '-s12+s', '-s15+s', '-s20+s', '-s30+s'], // сообщество (Билайн, Москва), упрощена
};

const RAW_PREFIX = 'raw:';
// Разрешены только флаги самого обхода; фильтры групп, порты и режимы задаёт приложение.
const RAW_ALLOWED = 'sdoqftSnlOQeMrmaYR';

/** Строка в стиле ByeByeDPI ("raw:-d1 -s1+s -a1") -> аргументы или null, если есть недопустимые флаги. */
function parseRaw(text) {
  if (typeof text !== 'string' || text.length > 400) return null;
  const tokens = text.trim().split(/\s+/).filter(Boolean);
  if (!tokens.length || tokens.length > 40) return null;
  for (const tk of tokens) {
    if (tk.startsWith('--')) return null;
    if (tk[0] === '-' && tk.length >= 2 && !/[0-9]/.test(tk[1]) && !RAW_ALLOWED.includes(tk[1])) return null;
  }
  return tokens;
}

/** "none" и неизвестные имена = без обхода. */
function forName(name) {
  if (typeof name === 'string' && name.startsWith(RAW_PREFIX)) return parseRaw(name.slice(RAW_PREFIX.length)) || [];
  return LIBRARY[name] || [];
}

/**
 * Своя стратегия для каждого сервиса: группы опций ByeDPI, разделённые --auto=none.
 * Группа по доменам срабатывает по SNI; для сервисов с ip_ranges (Telegram) добавляется
 * вторая группа по IP-диапазонам. selection: [{ profile, strategy }]
 */
function buildGroups(selection) {
  const out = [];
  const group = (flag, value, strategy) => {
    if (out.length) out.push('--auto=none');
    out.push(flag, ':' + value, ...forName(strategy));
  };
  const active = selection.filter((s) => s.strategy !== 'none');
  // Один активный сервис с match_all: без фильтра по SNI, иначе соединения без SNI (чат WhatsApp) не получают обхода.
  if (active.length === 1 && active[0].profile.matchAll) {
    out.push(...forName(active[0].strategy));
    return out;
  }
  for (const { profile, strategy } of selection) {
    if (strategy === 'none') continue;
    if (profile.domains.length) group('--hosts', profile.domains.join(' '), strategy);
    if (profile.ipRanges.length) group('--ipset', profile.ipRanges.join(' '), strategy);
  }
  return out;
}

function parseConfig(json) {
  const profiles = (json.profiles || []).map((o) => ({
    id: o.id,
    name: o.name,
    domains: o.domains || [],
    ipRanges: o.ip_ranges || [],
    probeHosts: o.probe_hosts || [],
    blockQuic: !!o.block_quic,
    matchAll: !!o.match_all,
  }));
  const strategies = json.strategies || [];
  const defaultStrategy = strategies[0];
  return { profiles, strategies, defaultStrategy };
}

function loadConfig(paths) {
  for (const p of paths) {
    try {
      return parseConfig(JSON.parse(fs.readFileSync(p, 'utf8')));
    } catch (_) {
      /* пробуем следующий путь */
    }
  }
  throw new Error('Не найден profiles.json');
}

module.exports = { LIBRARY, forName, parseRaw, buildGroups, parseConfig, loadConfig };
