import { spawn } from 'node:child_process';
import { resolve } from 'node:path';

export class McpStdioClient {
  constructor({ key, root = process.cwd(), timeoutMs = 18000 } = {}) {
    if (!key) throw new Error('AMAP_MAPS_API_KEY is required');
    this.timeoutMs = timeoutMs;
    this.pending = new Map();
    this.id = 0;
    this.buffer = '';
    this.stopped = false;
    const source = resolve(root, 'third_party/mcp/amap-maps-mcp-server/build/index.js');
    this.child = spawn(process.execPath, [source], {
      cwd: resolve(root, 'third_party/mcp/amap-maps-mcp-server'),
      env: { ...process.env, AMAP_MAPS_API_KEY: key },
      stdio: ['pipe', 'pipe', 'pipe']
    });
    this.child.stdout.setEncoding('utf8');
    this.child.stdout.on('data', chunk => {
      this.buffer += chunk;
      if (this.buffer.length > 2_000_000) { this.close(); return; }
      let n;
      while ((n = this.buffer.indexOf('\n')) !== -1) {
        const line = this.buffer.slice(0, n).trim();
        this.buffer = this.buffer.slice(n + 1);
        if (!line) continue;
        let msg;
        try { msg = JSON.parse(line); } catch { continue; }
        if (typeof msg.id !== 'number') continue;
        const pending = this.pending.get(msg.id);
        if (!pending) continue;
        clearTimeout(pending.timer);
        this.pending.delete(msg.id);
        if (msg.error) pending.reject(new Error(msg.error.message || 'MCP protocol error'));
        else pending.resolve(msg.result);
      }
    });
    this.child.stderr.setEncoding('utf8');
    this.child.stderr.on('data', chunk => {
      // Never log API keys, coordinates, or upstream response bodies.
      if (/^Fatal error|^Error:/.test(chunk.trim())) console.error('MCP child reported an error');
    });
    this.child.on('exit', () => {
      this.stopped = true;
      for (const p of this.pending.values()) { clearTimeout(p.timer); p.reject(new Error('MCP server exited')); }
      this.pending.clear();
    });
  }
  notify(method, params = {}) {
    if (this.stopped) throw new Error('MCP process closed');
    this.child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method, params }) + '\n');
  }
  request(method, params = {}, timeout = this.timeoutMs) {
    if (this.stopped) return Promise.reject(new Error('MCP process closed'));
    const id = ++this.id;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('MCP request timeout')); }, timeout);
      this.pending.set(id, { resolve, reject, timer });
      this.child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
    });
  }
  async connect() {
    await this.request('initialize', {
      protocolVersion: '2025-03-26', capabilities: {},
      clientInfo: { name: 'navith-mcp-bridge', version: '0.1.0' }
    });
    this.notify('notifications/initialized');
    const result = await this.request('tools/list');
    this.tools = (result?.tools || []).map(x => x.name);
    if (!this.tools.includes('maps_geo') || !this.tools.includes('maps_text_search')) {
      throw new Error('Required AMap MCP tools not advertised');
    }
    return this.tools;
  }
  async call(name, args) {
    if (!this.tools?.includes(name)) throw new Error('Tool not supported');
    const out = await this.request('tools/call', { name, arguments: args });
    if (out?.isError) throw new Error('AMap Web Service rejected the request or its coverage is unavailable');
    const text = out?.content?.find(x => x.type === 'text')?.text;
    if (!text) return {};
    try { return JSON.parse(text); } catch { throw new Error('Unexpected MCP response'); }
  }
  close() { this.stopped = true; this.child.kill('SIGTERM'); }
}
