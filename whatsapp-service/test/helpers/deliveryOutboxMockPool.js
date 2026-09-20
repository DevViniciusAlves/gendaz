'use strict';

function normalizeSql(sql) {
  return String(sql)
    .replace(/\s+/g, ' ')
    .trim()
    .toUpperCase();
}

function createDeliveryOutboxMockPool(initialRow = {}) {
  const state = {
    row: {
      id: 1,
      company_id: 1,
      provider_message_id: 'WAMID-1',
      state: 'PENDING',
      attempt_count: 0,
      recovery_count: 0,
      http_status: null,
      http_error_message: null,
      next_attempt_at: null,
      locked_until: null,
      last_attempt_at: null,
      updated_at: new Date(),
      ...initialRow,
    },
    queries: [],
  };

  async function execute(sql, params = []) {
    state.queries.push({ sql, params });

    const normalized = normalizeSql(sql);

    // Add row returning logic for SELECT queries
    if (normalized.includes('SELECT ID FROM WHATSAPP_DELIVERY_OUTBOX') && normalized.includes('WHERE STATE = \'DEAD\'')) {
      const [authReasons, maxAuthCycles, transientReasons, maxTransientCycles, recoveryMinAgeMinutes] = params;
      
      const reason = state.row.http_error_message;
      const recoveryCount = Number(state.row.recovery_count || 0);
      
      const isAuthRecoverable = authReasons && authReasons.includes(reason) && recoveryCount < Number(maxAuthCycles);
      const isTransientRecoverable = transientReasons && transientReasons.includes(reason) && recoveryCount < Number(maxTransientCycles);
      
      const updatedAt =
        state.row.updated_at instanceof Date
          ? state.row.updated_at
          : new Date(state.row.updated_at);

      const minAgeMs =
        Number(recoveryMinAgeMinutes) *
        60 *
        1000;

      const oldEnough =
        Number.isFinite(updatedAt.getTime()) &&
        Date.now() - updatedAt.getTime() >=
          minAgeMs;

      // console.log('Mock SELECT:', { reason, recoveryCount, isAuthRecoverable, isTransientRecoverable, oldEnough });
      
      if ((isAuthRecoverable || isTransientRecoverable) && oldEnough) {
        return { rows: [{ id: state.row.id }] };
      }
      return { rows: [] };
    }

    if (
      normalized === 'BEGIN' ||
      normalized === 'COMMIT' ||
      normalized === 'ROLLBACK'
    ) {
      return { rows: [] };
    }

    if (normalized.includes("SET STATE = 'DONE'")) {
      state.row.state = 'DONE';
      return { rows: [] };
    }

    if (normalized.includes("SET STATE = 'DEAD'")) {
      state.row.state = 'DEAD';

      if (params.length >= 2) {
        state.row.http_status = params[0];
        state.row.http_error_message = params[1];
      }

      return { rows: [] };
    }

    if (normalized.includes("SET STATE = 'PENDING'")) {
      // Handle both _handleRetry and _recoverDeadCycle
      state.row.state = 'PENDING';
      
      // If recovery_count increment is part of the query
      if (normalized.includes('RECOVERY_COUNT = RECOVERY_COUNT + 1')) {
        state.row.recovery_count = (state.row.recovery_count || 0) + 1;
      }
      
      if (params.length >= 1) {
        state.row.next_attempt_at = params[0];
      }
      if (params.length >= 3) {
        state.row.http_status = params[1];
        state.row.http_error_message = params[2];
      }
      return { rows: [] };
    }

    return { rows: [] };
  }

  const pool = {
    connect: async () => ({
      query: execute,
      release: () => {},
    }),
    query: execute,
    end: async () => {},
  };

  return { pool, state };
}

module.exports = {
  createDeliveryOutboxMockPool,
};
