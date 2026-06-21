-- =============================================================
-- Core Banking — V2 : Seed Data de démonstration
-- Idempotent (exécutable en dev et CI sans duplication)
-- =============================================================

-- =============================================================
-- 1. COMPTES (Aggregate Root)
-- =============================================================

-- Compte courant — Alice Martin
INSERT INTO accounts (id, account_number, owner_name, owner_email, currency, balance, overdraft_limit, status, aggr_version, created_at, updated_at)
SELECT gen_random_uuid(), 'FR7610011000011234567890187', 'Alice Martin', 'alice@example.com', 'EUR', 150000.0000, 5000.0000, 'ACTIVE', 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE account_number = 'FR7610011000011234567890187');

-- Compte courant — Bob Dupont
INSERT INTO accounts (id, account_number, owner_name, owner_email, currency, balance, overdraft_limit, status, aggr_version, created_at, updated_at)
SELECT gen_random_uuid(), 'FR7610011000019876543210135', 'Bob Dupont', 'bob@example.com', 'EUR', 50000.0000, 2000.0000, 'ACTIVE', 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE account_number = 'FR7610011000019876543210135');

-- Compte technique — Banque Centrale (trésorerie interne)
INSERT INTO accounts (id, account_number, owner_name, owner_email, currency, balance, overdraft_limit, status, aggr_version, created_at, updated_at)
SELECT gen_random_uuid(), 'FR7610011000015555666677778', 'Banque Centrale', 'admin@corebanking.com', 'EUR', 10000000.0000, 0.0000, 'ACTIVE', 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE account_number = 'FR7610011000015555666677778');

-- =============================================================
-- 2. ÉVÉNEMENTS (Event Store — append-only)
-- =============================================================

-- Événement : création du compte Alice
INSERT INTO events (id, aggregate_id, aggregate_type, event_type, version, data, metadata, idempotency_key, created_at)
SELECT
    gen_random_uuid(),
    a.id,
    'Account',
    'ACCOUNT_CREATED',
    1,
    jsonb_build_object(
        'account_number', a.account_number,
        'owner_name', a.owner_name,
        'owner_email', a.owner_email,
        'currency', a.currency,
        'initial_balance', a.balance
    ),
    '{"source": "V2_seed", "environment": "dev"}'::jsonb,
    'seed-account-created-alice',
    NOW()
FROM accounts a
WHERE a.account_number = 'FR7610011000011234567890187'
  AND NOT EXISTS (SELECT 1 FROM events WHERE idempotency_key = 'seed-account-created-alice');

-- Événement : création du compte Bob
INSERT INTO events (id, aggregate_id, aggregate_type, event_type, version, data, metadata, idempotency_key, created_at)
SELECT
    gen_random_uuid(),
    a.id,
    'Account',
    'ACCOUNT_CREATED',
    1,
    jsonb_build_object(
        'account_number', a.account_number,
        'owner_name', a.owner_name,
        'owner_email', a.owner_email,
        'currency', a.currency,
        'initial_balance', a.balance
    ),
    '{"source": "V2_seed", "environment": "dev"}'::jsonb,
    'seed-account-created-bob',
    NOW()
FROM accounts a
WHERE a.account_number = 'FR7610011000019876543210135'
  AND NOT EXISTS (SELECT 1 FROM events WHERE idempotency_key = 'seed-account-created-bob');

-- Événement : création du compte Banque Centrale
INSERT INTO events (id, aggregate_id, aggregate_type, event_type, version, data, metadata, idempotency_key, created_at)
SELECT
    gen_random_uuid(),
    a.id,
    'Account',
    'ACCOUNT_CREATED',
    1,
    jsonb_build_object(
        'account_number', a.account_number,
        'owner_name', a.owner_name,
        'owner_email', a.owner_email,
        'currency', a.currency,
        'initial_balance', a.balance
    ),
    '{"source": "V2_seed", "environment": "dev"}'::jsonb,
    'seed-account-created-admin',
    NOW()
FROM accounts a
WHERE a.account_number = 'FR7610011000015555666677778'
  AND NOT EXISTS (SELECT 1 FROM events WHERE idempotency_key = 'seed-account-created-admin');

-- =============================================================
-- 3. HISTORIQUE DES TRANSACTIONS (CQRS Read Model)
-- =============================================================

-- Dépôt initial — Alice
INSERT INTO transaction_history (id, account_id, transaction_type, amount, balance_before, balance_after, counterparty, reference, description, event_id, created_at)
SELECT
    gen_random_uuid(),
    a.id,
    'DEPOSIT',
    150000.0000,
    0.0000,
    150000.0000,
    'SYSTEM',
    'SEED-DEPOSIT-ALICE-001',
    'Dépôt initial — ouverture compte',
    e.id,
    NOW()
FROM accounts a
JOIN events e ON e.aggregate_id = a.id AND e.idempotency_key = 'seed-account-created-alice'
WHERE a.account_number = 'FR7610011000011234567890187'
  AND NOT EXISTS (
      SELECT 1 FROM transaction_history
      WHERE reference = 'SEED-DEPOSIT-ALICE-001'
  );

-- Dépôt initial — Bob
INSERT INTO transaction_history (id, account_id, transaction_type, amount, balance_before, balance_after, counterparty, reference, description, event_id, created_at)
SELECT
    gen_random_uuid(),
    a.id,
    'DEPOSIT',
    50000.0000,
    0.0000,
    50000.0000,
    'SYSTEM',
    'SEED-DEPOSIT-BOB-001',
    'Dépôt initial — ouverture compte',
    e.id,
    NOW()
FROM accounts a
JOIN events e ON e.aggregate_id = a.id AND e.idempotency_key = 'seed-account-created-bob'
WHERE a.account_number = 'FR7610011000019876543210135'
  AND NOT EXISTS (
      SELECT 1 FROM transaction_history
      WHERE reference = 'SEED-DEPOSIT-BOB-001'
  );

-- Dépôt initial — Banque Centrale
INSERT INTO transaction_history (id, account_id, transaction_type, amount, balance_before, balance_after, counterparty, reference, description, event_id, created_at)
SELECT
    gen_random_uuid(),
    a.id,
    'DEPOSIT',
    10000000.0000,
    0.0000,
    10000000.0000,
    'SYSTEM',
    'SEED-DEPOSIT-ADMIN-001',
    'Dotation trésorerie interne',
    e.id,
    NOW()
FROM accounts a
JOIN events e ON e.aggregate_id = a.id AND e.idempotency_key = 'seed-account-created-admin'
WHERE a.account_number = 'FR7610011000015555666677778'
  AND NOT EXISTS (
      SELECT 1 FROM transaction_history
      WHERE reference = 'SEED-DEPOSIT-ADMIN-001'
  );

-- =============================================================
-- 4. BALANCES MATÉRIALISÉES (CQRS Read Model)
-- =============================================================

-- Balance — Alice
INSERT INTO account_balances (account_id, balance, last_event_id, last_updated)
SELECT
    a.id,
    150000.0000,
    e.id,
    NOW()
FROM accounts a
JOIN events e ON e.aggregate_id = a.id AND e.idempotency_key = 'seed-account-created-alice'
WHERE a.account_number = 'FR7610011000011234567890187'
  AND NOT EXISTS (
      SELECT 1 FROM account_balances ab WHERE ab.account_id = a.id
  );

-- Balance — Bob
INSERT INTO account_balances (account_id, balance, last_event_id, last_updated)
SELECT
    a.id,
    50000.0000,
    e.id,
    NOW()
FROM accounts a
JOIN events e ON e.aggregate_id = a.id AND e.idempotency_key = 'seed-account-created-bob'
WHERE a.account_number = 'FR7610011000019876543210135'
  AND NOT EXISTS (
      SELECT 1 FROM account_balances ab WHERE ab.account_id = a.id
  );

-- Balance — Banque Centrale
INSERT INTO account_balances (account_id, balance, last_event_id, last_updated)
SELECT
    a.id,
    10000000.0000,
    e.id,
    NOW()
FROM accounts a
JOIN events e ON e.aggregate_id = a.id AND e.idempotency_key = 'seed-account-created-admin'
WHERE a.account_number = 'FR7610011000015555666677778'
  AND NOT EXISTS (
      SELECT 1 FROM account_balances ab WHERE ab.account_id = a.id
  );

-- =============================================================
-- 5. CLÉS D'IDEMPOTENCE (Backup)
-- =============================================================

INSERT INTO idempotency_keys (idempotency_key, resource_type, resource_id, status, response_body, expires_at, created_at)
SELECT 'seed-account-created-alice', 'Account', a.id, 'COMPLETED',
       jsonb_build_object('account_number', a.account_number, 'status', 'ACTIVE'),
       NOW() + INTERVAL '365 days', NOW()
FROM accounts a
WHERE a.account_number = 'FR7610011000011234567890187'
  AND NOT EXISTS (SELECT 1 FROM idempotency_keys WHERE idempotency_key = 'seed-account-created-alice');

INSERT INTO idempotency_keys (idempotency_key, resource_type, resource_id, status, response_body, expires_at, created_at)
SELECT 'seed-account-created-bob', 'Account', a.id, 'COMPLETED',
       jsonb_build_object('account_number', a.account_number, 'status', 'ACTIVE'),
       NOW() + INTERVAL '365 days', NOW()
FROM accounts a
WHERE a.account_number = 'FR7610011000019876543210135'
  AND NOT EXISTS (SELECT 1 FROM idempotency_keys WHERE idempotency_key = 'seed-account-created-bob');

INSERT INTO idempotency_keys (idempotency_key, resource_type, resource_id, status, response_body, expires_at, created_at)
SELECT 'seed-account-created-admin', 'Account', a.id, 'COMPLETED',
       jsonb_build_object('account_number', a.account_number, 'status', 'ACTIVE'),
       NOW() + INTERVAL '365 days', NOW()
FROM accounts a
WHERE a.account_number = 'FR7610011000015555666677778'
  AND NOT EXISTS (SELECT 1 FROM idempotency_keys WHERE idempotency_key = 'seed-account-created-admin');
