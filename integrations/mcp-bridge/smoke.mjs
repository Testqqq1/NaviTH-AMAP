import { McpStdioClient } from './rpc.mjs';
const client = new McpStdioClient({ key: process.env.AMAP_MAPS_API_KEY || 'TEST_NOT_A_REAL_KEY' });
try {
  const tools = await client.connect();
  const required = [
    'maps_geo', 'maps_regeocode', 'maps_text_search', 'maps_search_detail',
    'maps_direction_driving', 'maps_direction_walking', 'maps_weather'
  ];
  for (const name of required) if (!tools.includes(name)) throw new Error('Missing MCP tool: ' + name);
  console.log('MCP STDIO handshake successful. Tools:', tools.length);
} finally { client.close(); }
