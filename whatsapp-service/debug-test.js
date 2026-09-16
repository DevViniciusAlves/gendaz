function createMockPool() {
  let insertedRows = new Map();

  const client = {
    query: async (sql, params) => {
      const upperSql = sql.trim().toUpperCase();
      console.log('Full upperSql:', upperSql);
      console.log('Check ON CONFLICT:', upperSql.includes('ON CONFLICT (COMPANY_ID, PROVIDER_MESSAGE_ID) DO NOTHING RETURNING'));
      
      if (upperSql.includes('ON CONFLICT (COMPANY_ID, PROVIDER_MESSAGE_ID) DO NOTHING RETURNING')) {
        const companyId = params[0];
        const providerMessageId = params[1];
        const key = companyId + ':' + providerMessageId;
        console.log('ON CONFLICT path, key exists:', insertedRows.has(key));
        if (insertedRows.has(key)) {
          return { rows: [] };
        }
        insertedRows.set(key, true);
        return { rows: [{ id: 1, company_id: companyId, provider_message_id: providerMessageId, state: 'PENDING' }] };
      }

      if (upperSql.includes('INSERT INTO whatsapp_delivery_outbox') && upperSql.includes('RETURNING') && !upperSql.includes('ON CONFLICT')) {
        const companyId = params[0];
        const providerMessageId = params[1];
        const key = companyId + ':' + providerMessageId;
        console.log('INSERT RETURNING path (no ON CONFLICT), key exists:', insertedRows.has(key));
        if (insertedRows.has(key)) {
          return { rows: [] };
        }
        insertedRows.set(key, true);
        return { rows: [{ id: 1, company_id: companyId, provider_message_id: providerMessageId, state: 'PENDING' }] };
      }

      console.log('Default path, returning empty rows');
      return { rows: [] };
    },
    release: async () => {},
  };

  const pool = {
    query: async (sql, params) => client.query(sql, params),
    connect: async () => ({ ...client }),
    end: async () => {},
  };
  return pool;
}

const pool = createMockPool();

async function test1() {
  console.log('\n--- Test 1: registro novo ---');
  const result = await pool.query(
    "INSERT INTO whatsapp_delivery_outbox (company_id, provider_message_id, state, created_at, updated_at) VALUES ($1, $2, 'PENDING', NOW(), NOW()) ON CONFLICT (company_id, provider_message_id) DO NOTHING RETURNING id, state",
    ['empresa-1', 'msg-1']
  );
  console.log('Result rows:', result.rows.length);
  console.log('Result:', result.rows);
}

async function test2() {
  console.log('\n--- Test 2: registro duplicado ---');
  const result2 = await pool.query(
    "INSERT INTO whatsapp_delivery_outbox (company_id, provider_message_id, state, created_at, updated_at) VALUES ($1, $2, 'PENDING', NOW(), NOW()) ON CONFLICT (company_id, provider_message_id) DO NOTHING RETURNING id, state",
    ['empresa-1', 'msg-1']
  );
  console.log('Result rows:', result2.rows.length);
  console.log('Result:', result2.rows);
}

await test1();
await test2();