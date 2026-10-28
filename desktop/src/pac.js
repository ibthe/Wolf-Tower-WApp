const http = require('http');

const DOMAIN_OK = /^[a-z0-9.-]+$/i;

/** PAC-файл: через ByeDPI идут только домены выбранных сервисов, всё остальное напрямую. */
const HOST_OK = /^[A-Za-z0-9._:-]{1,253}$/;

function buildPac(domains, port, host = '127.0.0.1') {
  if (typeof host !== 'string' || !HOST_OK.test(host)) host = '127.0.0.1';
  const list = domains.filter((d) => DOMAIN_OK.test(d)).map((d) => d.toLowerCase());
  const hp = host.includes(':') ? `[${host}]` : host; // IPv6-адрес в скобках
  const proxy = `SOCKS5 ${hp}:${port}; SOCKS ${hp}:${port}; DIRECT`;
  return (
    'function FindProxyForURL(url, host) {\n' +
    '  host = host.toLowerCase();\n' +
    `  var d = ${JSON.stringify(list)};\n` +
    '  for (var i = 0; i < d.length; i++) {\n' +
    `    if (host === d[i] || dnsDomainIs(host, "." + d[i])) return "${proxy}";\n` +
    '  }\n' +
    '  return "DIRECT";\n' +
    '}\n'
  );
}

/** Локальный HTTP-сервер, который отдаёт текущий PAC. */
class PacServer {
  constructor() {
    this.content = buildPac([], 0);
    this.server = null;
    this.port = 0;
  }

  async start() {
    if (this.server) return;
    this.server = http.createServer((req, res) => {
      res.writeHead(200, { 'Content-Type': 'application/x-ns-proxy-autoconfig', 'Cache-Control': 'no-store' });
      res.end(this.content);
    });
    await new Promise((resolve, reject) => {
      this.server.once('error', reject);
      this.server.listen(0, '127.0.0.1', resolve);
    });
    this.port = this.server.address().port;
  }

  get url() {
    return `http://127.0.0.1:${this.port}/proxy.pac`;
  }

  /** domains пустой: все запросы идут напрямую (защита выключена). */
  update(domains, socksPort, host = '127.0.0.1') {
    this.content = buildPac(domains, socksPort, host);
  }

  async stop() {
    if (!this.server) return;
    const s = this.server;
    this.server = null;
    await new Promise((resolve) => s.close(resolve));
  }
}

module.exports = { buildPac, PacServer, HOST_OK };
