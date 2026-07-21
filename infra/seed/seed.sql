-- ================================================================
-- Harbor Bank â€” Development Seed Data
-- ----------------------------------------------------------------
-- 100 customers Â· 150 accounts Â· 110 cards Â· 95 loans
-- 246 transactions Â· 100 beneficiaries Â· 110 payments
-- 290 notifications Â· 310 audit events
--
-- Every customer gets support PIN '1234' (bcrypt-hashed, never plaintext).
-- Includes accounts in PENDING_APPROVAL and large transfers held in
-- PENDING_APPROVAL for the admin approval tabs, plus loans attached to the
-- login-able demo customers (demo.customer / alex.rivera).
--
-- Scenarios covered
--   KYC      : VERIFIED (75) Â· PENDING (15) Â· REJECTED (10)
--   Customer : ACTIVE (95) Â· SUSPENDED (5)
--   Account  : ACTIVE Â· CLOSED (secondary accs 41-50) Â· PENDING_APPROVAL (76-80)
--   Card     : ACTIVE Â· FROZEN (66-70) Â· CANCELLED (71-75)
--   Loan     : ACTIVE Â· APPROVED Â· UNDER_REVIEW Â· APPLIED Â· REJECTED
--   Payment  : COMPLETED Â· PENDING Â· FAILED Â· SCHEDULED
--
-- Prerequisites: run AFTER docker-compose up (Flyway must have
--   created all tables first).
--
-- Usage:
--   docker exec -i $(docker ps --format '{{.Names}}' | grep postgres) \
--     psql -U banking < infra/seed/seed.sql
-- ================================================================

\set ON_ERROR_STOP on

-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- 1. CUSTOMER SERVICE
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
\c customerdb

INSERT INTO customers (
  id, external_user_id, email, first_name, last_name, phone,
  date_of_birth, address_line1, city, state, postal_code, country,
  kyc_status, status, created_at, updated_at
)
SELECT
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'kc-seed-' || lpad(n::text, 3, '0'),
  'seed.customer' || lpad(n::text, 3, '0') || '@harborbank.dev',
  -- first name (50-item cycle)
  (ARRAY[
    'James','Mary','John','Patricia','Robert','Jennifer','Michael','Linda',
    'William','Barbara','David','Elizabeth','Richard','Susan','Joseph',
    'Jessica','Thomas','Sarah','Charles','Karen','Christopher','Lisa',
    'Daniel','Nancy','Matthew','Betty','Anthony','Margaret','Mark','Sandra',
    'Donald','Ashley','Steven','Dorothy','Paul','Kimberly','Andrew','Emily',
    'Kenneth','Donna','Joshua','Michelle','Kevin','Carol','Brian','Amanda',
    'George','Melissa','Timothy','Deborah'
  ])[ ((n-1) % 50) + 1 ],
  -- last name (50-item cycle)
  (ARRAY[
    'Smith','Johnson','Williams','Brown','Jones','Garcia','Miller','Davis',
    'Rodriguez','Martinez','Hernandez','Lopez','Gonzalez','Wilson','Anderson',
    'Thomas','Taylor','Moore','Jackson','Martin','Lee','Perez','Thompson',
    'White','Harris','Sanchez','Clark','Ramirez','Lewis','Robinson','Walker',
    'Young','Allen','King','Wright','Scott','Torres','Nguyen','Hill','Flores',
    'Green','Adams','Nelson','Baker','Hall','Rivera','Campbell','Mitchell',
    'Carter','Roberts'
  ])[ ((n-1) % 50) + 1 ],
  '+1 555 ' || lpad((n * 73 % 1000)::text, 3, '0') || ' ' || lpad((n * 137 % 10000)::text, 4, '0'),
  (DATE '1960-01-01' + (n * 113 % 16425)::int),
  n::text || ' Harbor Street',
  (ARRAY[
    'New York','Los Angeles','Chicago','Houston','Phoenix','Philadelphia',
    'San Antonio','San Diego','Dallas','San Jose','Austin','Jacksonville',
    'Columbus','San Francisco','Charlotte','Indianapolis','Seattle','Denver',
    'Nashville','Boston'
  ])[ ((n-1) % 20) + 1 ],
  (ARRAY[
    'NY','CA','IL','TX','AZ','PA','TX','CA','TX','CA',
    'TX','FL','OH','CA','NC','IN','WA','CO','TN','MA'
  ])[ ((n-1) % 20) + 1 ],
  lpad((10000 + n * 97 % 89999)::text, 5, '0'),
  'US',
  -- KYC: 1-50 VERIFIED Â· 51-65 PENDING Â· 66-75 VERIFIED Â· 76-85 REJECTED Â· 86-97 VERIFIED Â· 98-100 PENDING
  CASE
    WHEN n BETWEEN  1 AND  50 THEN 'VERIFIED'
    WHEN n BETWEEN 51 AND  65 THEN 'PENDING'
    WHEN n BETWEEN 66 AND  75 THEN 'VERIFIED'
    WHEN n BETWEEN 76 AND  85 THEN 'REJECTED'
    WHEN n BETWEEN 86 AND  97 THEN 'VERIFIED'
    ELSE 'PENDING'
  END,
  -- Status: 93-97 SUSPENDED, rest ACTIVE
  CASE WHEN n BETWEEN 93 AND 97 THEN 'SUSPENDED' ELSE 'ACTIVE' END,
  NOW() - (n * 3 || ' days')::interval,
  NOW() - (n     || ' hours')::interval
FROM generate_series(1, 100) n
ON CONFLICT (email) DO NOTHING;

-- Support PIN: every customer gets the demo secret PIN '1234', stored as a
-- bcrypt hash (pgcrypto 'bf' == $2a$, compatible with Spring's BCryptPasswordEncoder).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE customers
SET support_pin_hash = crypt('1234', gen_salt('bf', 10)),
    support_pin_set_at = NOW()
WHERE support_pin_hash IS NULL;

-- Resolve the login-able demo customers (created by customer-service on startup
-- with random UUIDs) so we can attach loans to them below. Falls back to seed
-- customers 001/002 when the demo rows do not exist yet.
SELECT COALESCE(
  (SELECT id::text FROM customers WHERE email = 'demo.customer@example.com'),
  'c0000000-0000-4000-8000-000000000001'
) AS demo_customer_id \gset

SELECT COALESCE(
  (SELECT id::text FROM customers WHERE email = 'alex.rivera@example.com'),
  'c0000000-0000-4000-8000-000000000002'
) AS alex_customer_id \gset

-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- 2. ACCOUNT SERVICE
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
\c accountdb

-- Primary USD accounts â€” customers 1-75
INSERT INTO accounts (
  id, account_number, customer_id, balance, currency,
  status, version, created_at, updated_at
)
SELECT
  ('a0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'ACC' || lpad(to_hex(n)::text, 16, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ROUND((n * 1973 % 95000 + 500)::numeric, 2),
  'USD',
  'ACTIVE', 0,
  NOW() - (n * 3 || ' days')::interval,
  NOW() - (n     || ' hours')::interval
FROM generate_series(1, 75) n
ON CONFLICT DO NOTHING;

-- Secondary accounts (mixed currencies) â€” customers 1-50
-- Customers 41-50 get CLOSED secondary accounts (realistic churn scenario)
INSERT INTO accounts (
  id, account_number, customer_id, balance, currency,
  status, version, created_at, updated_at
)
SELECT
  ('a1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'ACC' || lpad(to_hex(1000 + n)::text, 16, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ROUND((n * 3271 % 45000 + 100)::numeric, 2),
  (ARRAY['EUR','GBP','CAD','USD','CHF'])[ ((n-1) % 5) + 1 ],
  CASE WHEN n BETWEEN 41 AND 50 THEN 'CLOSED' ELSE 'ACTIVE' END,
  0,
  NOW() - (n * 2 || ' days')::interval,
  NOW() - (n * 2 || ' hours')::interval
FROM generate_series(1, 50) n
ON CONFLICT DO NOTHING;

-- High-yield savings accounts â€” premium customers 1-20
INSERT INTO accounts (
  id, account_number, customer_id, balance, currency,
  status, version, created_at, updated_at
)
SELECT
  ('a2000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'ACC' || lpad(to_hex(2000 + n)::text, 16, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ROUND((n * 8191 % 250000 + 10000)::numeric, 2),
  'USD',
  'ACTIVE', 0,
  NOW() - (n     || ' days')::interval,
  NOW() - (n     || ' hours')::interval
FROM generate_series(1, 20) n
ON CONFLICT DO NOTHING;

-- Newly opened accounts awaiting admin approval — customers 76-80
INSERT INTO accounts (
  id, account_number, customer_id, balance, currency,
  status, version, created_at, updated_at
)
SELECT
  ('a3000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'ACC' || lpad(to_hex(3000 + n)::text, 16, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  0.00,
  'USD',
  'PENDING_APPROVAL', 0,
  NOW() - ((n - 75) * 5 || ' hours')::interval,
  NOW() - ((n - 75) * 5 || ' hours')::interval
FROM generate_series(76, 80) n
ON CONFLICT DO NOTHING;

-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- 3. CARD SERVICE
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
\c carddb

-- Primary VISA DEBIT â€” customers 1-75
-- 66-70: FROZEN Â· 71-75: CANCELLED Â· rest: ACTIVE
INSERT INTO cards (
  id, customer_id, account_id, card_number_last4,
  card_network, card_type, status,
  daily_limit, monthly_limit, expires_on, created_at, updated_at
)
SELECT
  ('ca000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  lpad((n * 37 % 10000)::text, 4, '0'),
  'VISA', 'DEBIT',
  CASE
    WHEN n BETWEEN 66 AND 70 THEN 'FROZEN'
    WHEN n BETWEEN 71 AND 75 THEN 'CANCELLED'
    ELSE 'ACTIVE'
  END,
  CASE WHEN n <= 20 THEN 5000.00 WHEN n <= 50 THEN 2000.00 ELSE 1000.00 END,
  CASE WHEN n <= 20 THEN 20000.00 WHEN n <= 50 THEN 10000.00 ELSE 5000.00 END,
  DATE '2028-12-31',
  NOW() - (n * 3     || ' days')::interval,
  NOW() - (n         || ' hours')::interval
FROM generate_series(1, 75) n
ON CONFLICT DO NOTHING;

-- Secondary MASTERCARD â€” customers 1-35
-- Linked to their secondary account; even customers get CREDIT type
INSERT INTO cards (
  id, customer_id, account_id, card_number_last4,
  card_network, card_type, status,
  daily_limit, monthly_limit, expires_on, created_at, updated_at
)
SELECT
  ('cb000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  lpad((n * 53 % 10000)::text, 4, '0'),
  'MASTERCARD',
  CASE WHEN n % 2 = 0 THEN 'CREDIT' ELSE 'DEBIT' END,
  'ACTIVE',
  3000.00, 15000.00,
  DATE '2027-06-30',
  NOW() - (n * 2     || ' days')::interval,
  NOW()
FROM generate_series(1, 35) n
ON CONFLICT DO NOTHING;

-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- 4. LOAN SERVICE
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
\c loandb

-- Personal unsecured loans â€” customers 1-60
-- Statuses: 1-20 ACTIVE Â· 21-35 APPROVED Â· 36-47 UNDER_REVIEW Â· 48-55 APPLIED Â· 56-60 REJECTED
INSERT INTO loans (
  id, customer_id, product_code, principal, interest_rate, term_months,
  monthly_payment, outstanding_balance, currency, status, purpose,
  created_at, updated_at
)
SELECT
  ('d0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'PERSONAL_UNSECURED',
  ROUND((n * 1500 % 48000 + 2000)::numeric, 2)                   AS principal,
  ROUND((5.0 + (n * 3 % 60) * 0.1)::numeric, 2)                  AS interest_rate,
  (ARRAY[12,24,36,48,60])[ (n % 5) + 1 ]                         AS term_months,
  -- amortisation: M = PÂ·rÂ·(1+r)^n / ((1+r)^n âˆ’ 1)  where r = APR/1200
  ROUND(
    ROUND((n * 1500 % 48000 + 2000)::numeric, 2)
    * (ROUND((5.0 + (n * 3 % 60) * 0.1)::numeric, 2) / 1200)
    * POWER(1 + ROUND((5.0 + (n * 3 % 60) * 0.1)::numeric, 2) / 1200,
            (ARRAY[12,24,36,48,60])[ (n % 5) + 1 ])
    / (POWER(1 + ROUND((5.0 + (n * 3 % 60) * 0.1)::numeric, 2) / 1200,
             (ARRAY[12,24,36,48,60])[ (n % 5) + 1 ]) - 1)
  , 2)                                                            AS monthly_payment,
  ROUND((n * 1500 % 48000 + 2000)::numeric
    * CASE WHEN n <= 20 THEN 0.4 ELSE 1.0 END, 2)                AS outstanding_balance,
  'USD',
  CASE
    WHEN n <= 20 THEN 'ACTIVE'
    WHEN n <= 35 THEN 'APPROVED'
    WHEN n <= 47 THEN 'UNDER_REVIEW'
    WHEN n <= 55 THEN 'APPLIED'
    ELSE              'REJECTED'
  END,
  (ARRAY[
    'Debt consolidation','Home renovation','Medical expenses','Wedding expenses',
    'Vacation fund','Emergency fund','Education costs','Business startup',
    'Car repair','Electronics purchase'
  ])[ (n % 10) + 1 ],
  NOW() - (n * 5  || ' days')::interval,
  NOW() - (n      || ' hours')::interval
FROM generate_series(1, 60) n
ON CONFLICT DO NOTHING;

-- Auto loans â€” customers 1-30
-- 1-10 ACTIVE Â· 11-22 APPROVED Â· 23-30 UNDER_REVIEW
INSERT INTO loans (
  id, customer_id, product_code, principal, interest_rate, term_months,
  monthly_payment, outstanding_balance, currency, status, purpose,
  created_at, updated_at
)
SELECT
  ('d1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'AUTO',
  ROUND((n * 3700 % 75000 + 8000)::numeric, 2),
  ROUND((4.5 + (n * 2 % 30) * 0.1)::numeric, 2),
  (ARRAY[36,48,60,72,84])[ (n % 5) + 1 ],
  ROUND(
    ROUND((n * 3700 % 75000 + 8000)::numeric, 2)
    * (ROUND((4.5 + (n * 2 % 30) * 0.1)::numeric, 2) / 1200)
    * POWER(1 + ROUND((4.5 + (n * 2 % 30) * 0.1)::numeric, 2) / 1200,
            (ARRAY[36,48,60,72,84])[ (n % 5) + 1 ])
    / (POWER(1 + ROUND((4.5 + (n * 2 % 30) * 0.1)::numeric, 2) / 1200,
             (ARRAY[36,48,60,72,84])[ (n % 5) + 1 ]) - 1)
  , 2),
  ROUND((n * 3700 % 75000 + 8000)::numeric
    * CASE WHEN n <= 10 THEN 0.65 ELSE 1.0 END, 2),
  'USD',
  CASE
    WHEN n <= 10 THEN 'ACTIVE'
    WHEN n <= 22 THEN 'APPROVED'
    ELSE              'UNDER_REVIEW'
  END,
  CASE WHEN n % 2 = 0 THEN 'New vehicle purchase' ELSE 'Used vehicle purchase' END,
  NOW() - ((n + 10) * 4 || ' days')::interval,
  NOW() - (n * 2         || ' hours')::interval
FROM generate_series(1, 30) n
ON CONFLICT DO NOTHING;

-- Home improvement loans â€” customers 5-25
-- 5-10 ACTIVE Â· 11-17 APPROVED Â· 18-21 UNDER_REVIEW Â· 22-25 APPLIED
INSERT INTO loans (
  id, customer_id, product_code, principal, interest_rate, term_months,
  monthly_payment, outstanding_balance, currency, status, purpose,
  created_at, updated_at
)
SELECT
  ('d2000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'HOME_IMPROVEMENT',
  ROUND((n * 4300 % 95000 + 5000)::numeric, 2),
  ROUND((6.5 + (n % 20) * 0.15)::numeric, 2),
  (ARRAY[60,72,84,96,120])[ (n % 5) + 1 ],
  ROUND(
    ROUND((n * 4300 % 95000 + 5000)::numeric, 2)
    * (ROUND((6.5 + (n % 20) * 0.15)::numeric, 2) / 1200)
    * POWER(1 + ROUND((6.5 + (n % 20) * 0.15)::numeric, 2) / 1200,
            (ARRAY[60,72,84,96,120])[ (n % 5) + 1 ])
    / (POWER(1 + ROUND((6.5 + (n % 20) * 0.15)::numeric, 2) / 1200,
             (ARRAY[60,72,84,96,120])[ (n % 5) + 1 ]) - 1)
  , 2),
  ROUND((n * 4300 % 95000 + 5000)::numeric
    * CASE WHEN n <= 10 THEN 0.5 ELSE 1.0 END, 2),
  'USD',
  CASE
    WHEN n <= 10 THEN 'ACTIVE'
    WHEN n <= 17 THEN 'APPROVED'
    WHEN n <= 21 THEN 'UNDER_REVIEW'
    ELSE              'APPLIED'
  END,
  'Home renovation and improvement',
  NOW() - ((n + 20) * 3 || ' days')::interval,
  NOW() - (n * 3         || ' hours')::interval
FROM generate_series(5, 25) n
ON CONFLICT DO NOTHING;

-- Loans for the login-able demo customers so the demo user sees data
INSERT INTO loans (
  id, customer_id, product_code, principal, interest_rate, term_months,
  monthly_payment, outstanding_balance, currency, status, purpose,
  created_at, updated_at
) VALUES
  ('d3000000-0000-4000-8000-000000000001'::uuid, :'demo_customer_id'::uuid,
   'PERSONAL_UNSECURED', 15000.00, 7.25, 36, 464.99, 9800.50, 'USD',
   'ACTIVE', 'Home renovation',
   NOW() - INTERVAL '200 days', NOW() - INTERVAL '3 days'),
  ('d3000000-0000-4000-8000-000000000002'::uuid, :'demo_customer_id'::uuid,
   'AUTO', 32000.00, 5.90, 60, 617.03, 32000.00, 'USD',
   'APPROVED', 'New vehicle purchase',
   NOW() - INTERVAL '12 days', NOW() - INTERVAL '1 day'),
  ('d3000000-0000-4000-8000-000000000003'::uuid, :'demo_customer_id'::uuid,
   'HOME_IMPROVEMENT', 48000.00, 8.10, 120, 585.11, 48000.00, 'USD',
   'UNDER_REVIEW', 'Kitchen remodel',
   NOW() - INTERVAL '4 days', NOW() - INTERVAL '6 hours'),
  ('d3000000-0000-4000-8000-000000000004'::uuid, :'alex_customer_id'::uuid,
   'PERSONAL_UNSECURED', 8000.00, 9.40, 24, 367.27, 8000.00, 'USD',
   'APPLIED', 'Debt consolidation',
   NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- 5. TRANSACTION SERVICE
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
\c transactiondb

-- 200 transfers between primary accounts of customers 1-60
-- ~90% COMPLETED Â· ~5% FAILED Â· ~5% PENDING
INSERT INTO transactions (
  id, from_account_id, to_account_id, amount, currency,
  status, failure_reason, created_at, updated_at
)
SELECT
  ('e0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a0000000-0000-4000-8000-' || lpad(to_hex((n * 3 % 60) + 1), 12, '0'))::uuid,
  ('a0000000-0000-4000-8000-' || lpad(to_hex((n * 7 % 60) + 1), 12, '0'))::uuid,
  ROUND((n * 317 % 4900 + 10)::numeric, 2),
  'USD',
  CASE
    WHEN n % 20 = 0 THEN 'FAILED'
    WHEN n % 10 = 0 THEN 'PENDING'
    ELSE                  'COMPLETED'
  END,
  CASE WHEN n % 20 = 0 THEN 'Insufficient funds' ELSE NULL END,
  NOW() - (n * 12  || ' hours')::interval,
  NOW() - (n * 11  || ' hours')::interval
FROM generate_series(1, 200) n
WHERE (n * 3 % 60) <> (n * 7 % 60)   -- skip self-transfers
ON CONFLICT DO NOTHING;

-- 50 additional cross-currency transfers from secondary accounts
INSERT INTO transactions (
  id, from_account_id, to_account_id, amount, currency,
  status, failure_reason, created_at, updated_at
)
SELECT
  ('e1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ROUND((n * 211 % 2000 + 50)::numeric, 2),
  (ARRAY['EUR','GBP','CAD','USD','CHF'])[ ((n-1) % 5) + 1 ],
  CASE WHEN n % 8 = 0 THEN 'FAILED' ELSE 'COMPLETED' END,
  CASE WHEN n % 8 = 0 THEN 'FX conversion failed' ELSE NULL END,
  NOW() - (n * 6   || ' hours')::interval,
  NOW() - (n * 5   || ' hours')::interval
FROM generate_series(1, 40) n
ON CONFLICT DO NOTHING;

-- Large transfers held for admin approval (>= $10,000 threshold)
INSERT INTO transactions (
  id, from_account_id, to_account_id, amount, currency,
  status, failure_reason, created_at, updated_at
)
SELECT
  ('e2000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a2000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a0000000-0000-4000-8000-' || lpad(to_hex(n + 30), 12, '0'))::uuid,
  ROUND((10000 + n * 3750)::numeric, 2),
  'USD',
  'PENDING_APPROVAL',
  NULL,
  NOW() - (n * 3 || ' hours')::interval,
  NOW() - (n * 3 || ' hours')::interval
FROM generate_series(1, 6) n
ON CONFLICT DO NOTHING;

-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- 6. PAYMENT SERVICE
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
\c paymentdb

-- Beneficiaries â€” customers 1-60 get one external beneficiary
INSERT INTO beneficiaries (
  id, customer_id, nickname, account_number, routing_number, bank_name,
  currency, status, created_at
)
SELECT
  ('b0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  (ARRAY[
    'Mom','Dad','Monthly Rent','Utilities','Insurance','Gym Membership',
    'Streaming Service','Landlord','Car Payment','Student Loan',
    'Business Partner','Freelance Client','Investment Fund','Charity',
    'Church','College Fund','John D.','Sarah M.','Mike T.','Emma K.'
  ])[ (n % 20) + 1 ],
  'EXT' || lpad(to_hex(n * 9871), 16, '0'),
  '0' || lpad((n * 3 % 1000000000)::text, 9, '0'),
  (ARRAY[
    'Chase Bank','Bank of America','Wells Fargo','Citibank','US Bank',
    'PNC Bank','TD Bank','Capital One','Regions Bank','Fifth Third',
    'HSBC','KeyBank','Citizens Bank','Ally Bank','Discover Bank'
  ])[ (n % 15) + 1 ],
  'USD',
  CASE WHEN n % 15 = 0 THEN 'DISABLED' ELSE 'ACTIVE' END,
  NOW() - (n * 4 || ' days')::interval
FROM generate_series(1, 60) n
ON CONFLICT DO NOTHING;

-- Second beneficiary for customers 1-40 (internal Harbor accounts)
INSERT INTO beneficiaries (
  id, customer_id, nickname, account_number, routing_number, bank_name,
  currency, status, created_at
)
SELECT
  ('b1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'Personal Savings (' || (ARRAY['USD','EUR','GBP'])[(n%3)+1] || ')',
  'ACC' || lpad(to_hex(1000 + n)::text, 16, '0'),
  NULL,
  'Harbor Bank',
  (ARRAY['USD','EUR','GBP'])[ (n % 3) + 1 ],
  'ACTIVE',
  NOW() - (n * 2 || ' days')::interval
FROM generate_series(1, 40) n
ON CONFLICT DO NOTHING;

-- Payments batch 1 â€” customers 1-70, one payment each
INSERT INTO payments (
  id, customer_id, from_account_id, beneficiary_id,
  payment_type, amount, currency, status,
  reference, description, scheduled_for, failure_reason,
  created_at, updated_at
)
SELECT
  ('fa000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  CASE WHEN n <= 60
    THEN ('b0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid
    ELSE NULL
  END,
  (ARRAY['ACH','WIRE','BILL_PAY','INTERNAL'])[ (n % 4) + 1 ],
  ROUND((n * 230 % 9000 + 25)::numeric, 2),
  'USD',
  CASE
    WHEN n % 15 = 0 THEN 'FAILED'
    WHEN n % 8  = 0 THEN 'PENDING'
    WHEN n % 20 = 0 THEN 'SCHEDULED'
    ELSE                  'COMPLETED'
  END,
  'REF-' || upper(lpad(to_hex(n * 12345), 8, '0')),
  (ARRAY[
    'Monthly rent','Utility bill','Insurance premium','Loan repayment',
    'Subscription renewal','Service payment','Invoice settlement',
    'Transfer to savings','Bill payment','Regular transfer'
  ])[ (n % 10) + 1 ],
  CASE WHEN n % 20 = 0
    THEN CURRENT_DATE + ((n % 30) || ' days')::interval
    ELSE NULL
  END,
  CASE WHEN n % 15 = 0 THEN 'Insufficient funds' ELSE NULL END,
  NOW() - (n * 10 || ' hours')::interval,
  NOW() - (n * 9  || ' hours')::interval
FROM generate_series(1, 70) n
ON CONFLICT DO NOTHING;

-- Payments batch 2 â€” customers 1-40, a second payment from secondary account
INSERT INTO payments (
  id, customer_id, from_account_id, beneficiary_id,
  payment_type, amount, currency, status,
  reference, description, scheduled_for, failure_reason,
  created_at, updated_at
)
SELECT
  ('fb000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('a1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('b1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'INTERNAL',
  ROUND((n * 570 % 5000 + 50)::numeric, 2),
  (ARRAY['EUR','GBP','CAD','USD','CHF'])[ ((n-1) % 5) + 1 ],
  CASE WHEN n % 12 = 0 THEN 'FAILED' ELSE 'COMPLETED' END,
  'REF-INT-' || lpad(n::text, 6, '0'),
  'Internal transfer to savings',
  NULL,
  CASE WHEN n % 12 = 0 THEN 'Recipient account closed' ELSE NULL END,
  NOW() - (n * 8 || ' hours')::interval,
  NOW() - (n * 7 || ' hours')::interval
FROM generate_series(1, 40) n
ON CONFLICT DO NOTHING;

-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- 7. NOTIFICATION SERVICE
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
\c notificationdb

-- Welcome notifications â€” all 100 customers
INSERT INTO notifications (
  id, customer_id, channel, category, title, body,
  status, read_at, created_at
)
SELECT
  ('f0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'IN_APP', 'ONBOARDING',
  'Welcome to Harbor Bank!',
  'Your Harbor Bank account has been created. Complete your KYC verification to unlock full access.',
  CASE WHEN n <= 60 THEN 'READ' ELSE 'SENT' END,
  CASE WHEN n <= 60 THEN NOW() - (n * 2 || ' days')::interval ELSE NULL END,
  NOW() - (n * 3 || ' days')::interval
FROM generate_series(1, 100) n
ON CONFLICT DO NOTHING;

-- KYC outcome notifications â€” customers 1-85
INSERT INTO notifications (
  id, customer_id, channel, category, title, body,
  status, read_at, created_at
)
SELECT
  ('f1000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  CASE WHEN n % 3 = 0 THEN 'EMAIL' ELSE 'IN_APP' END,
  'KYC',
  CASE
    WHEN n <= 75 THEN 'Identity Verified âœ“'
    ELSE              'Verification Requires Attention'
  END,
  CASE
    WHEN n <= 75 THEN 'Your identity has been successfully verified. You now have full access to all Harbor Bank services.'
    ELSE              'Your KYC verification could not be completed. Please contact support@harborbank.dev to resubmit your documents.'
  END,
  CASE WHEN n % 4 = 0 THEN 'READ' ELSE 'SENT' END,
  CASE WHEN n % 4 = 0 THEN NOW() - (n || ' hours')::interval ELSE NULL END,
  NOW() - (n * 2 + 1 || ' days')::interval
FROM generate_series(1, 85) n
ON CONFLICT DO NOTHING;

-- Loan status notifications â€” customers 1-60
INSERT INTO notifications (
  id, customer_id, channel, category, title, body,
  status, read_at, created_at
)
SELECT
  ('f2000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'IN_APP', 'LOAN',
  CASE
    WHEN n <= 20 THEN 'Loan Activated â€” Funds Disbursed'
    WHEN n <= 35 THEN 'Loan Approved!'
    WHEN n <= 47 THEN 'Loan Application Under Review'
    WHEN n <= 55 THEN 'Loan Application Received'
    ELSE              'Loan Application Declined'
  END,
  CASE
    WHEN n <= 20 THEN 'Your personal loan is now active. Funds have been disbursed to your primary account.'
    WHEN n <= 35 THEN 'Great news! Your loan application has been approved. Log in to confirm activation.'
    WHEN n <= 47 THEN 'Your loan is currently being reviewed by our underwriting team. We will update you within 2 business days.'
    WHEN n <= 55 THEN 'We have received your loan application and will begin reviewing it shortly.'
    ELSE              'Your loan application was not approved at this time. You may re-apply after 90 days.'
  END,
  CASE WHEN n <= 20 THEN 'READ' ELSE 'SENT' END,
  CASE WHEN n <= 20 THEN NOW() - (n || ' hours')::interval ELSE NULL END,
  NOW() - (n * 4 || ' hours')::interval
FROM generate_series(1, 60) n
ON CONFLICT DO NOTHING;

-- Transaction / payment alerts â€” customers 1-75
INSERT INTO notifications (
  id, customer_id, channel, category, title, body,
  status, read_at, created_at
)
SELECT
  ('f3000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  CASE WHEN n % 2 = 0 THEN 'PUSH' ELSE 'IN_APP' END,
  'TRANSACTION',
  CASE WHEN n % 10 = 0 THEN 'Large Transaction Alert' ELSE 'Payment Received' END,
  CASE
    WHEN n % 10 = 0
      THEN 'A transaction of $' || (n * 317 % 4900 + 500)::text || ' was processed on your account.'
    ELSE
      'You received $' || (n * 230 % 9000 + 25)::text || ' in your Harbor Bank account.'
  END,
  CASE WHEN n <= 40 THEN 'READ' ELSE 'SENT' END,
  CASE WHEN n <= 40 THEN NOW() - (n * 30 || ' minutes')::interval ELSE NULL END,
  NOW() - (n * 6 || ' hours')::interval
FROM generate_series(1, 75) n
ON CONFLICT DO NOTHING;

-- Security alerts â€” frozen / cancelled cards (customers 66-75)
INSERT INTO notifications (
  id, customer_id, channel, category, title, body,
  status, read_at, created_at
)
SELECT
  ('f4000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'EMAIL', 'SECURITY',
  CASE
    WHEN n BETWEEN 66 AND 70 THEN 'Your Card Has Been Frozen'
    ELSE                          'Your Card Has Been Cancelled'
  END,
  CASE
    WHEN n BETWEEN 66 AND 70
      THEN 'Your card ending in ' || lpad((n * 37 % 10000)::text, 4, '0') || ' has been temporarily frozen for security reasons. Contact support to unfreeze.'
    ELSE
      'Your card ending in ' || lpad((n * 37 % 10000)::text, 4, '0') || ' has been cancelled. Please request a replacement card in the app or at a branch.'
  END,
  'SENT', NULL,
  NOW() - (n || ' days')::interval
FROM generate_series(66, 75) n
ON CONFLICT DO NOTHING;

-- Suspension notices â€” customers 93-97
INSERT INTO notifications (
  id, customer_id, channel, category, title, body,
  status, read_at, created_at
)
SELECT
  ('f5000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'EMAIL', 'ACCOUNT',
  'Account Access Suspended',
  'Your Harbor Bank account has been suspended pending a compliance review. Please contact compliance@harborbank.dev within 10 business days.',
  'SENT', NULL,
  NOW() - ((n - 90) * 2 || ' days')::interval
FROM generate_series(93, 97) n
ON CONFLICT DO NOTHING;

-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- 8. AUDIT SERVICE
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
\c auditdb

-- CUSTOMER_CREATED â€” customers 1-100
INSERT INTO audit_events (
  id, actor, action, resource_type, resource_id, customer_id,
  details, ip_address, created_at
)
SELECT
  ('ae000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  CASE WHEN n <= 50 THEN 'seed-script' ELSE 'demo.admin' END,
  'CUSTOMER_CREATED',
  'customer',
  'c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'Customer seeded: seed.customer' || lpad(n::text, 3, '0') || '@harborbank.dev',
  '127.0.0.1',
  NOW() - (n * 3 || ' days')::interval
FROM generate_series(1, 100) n
ON CONFLICT DO NOTHING;

-- ACCOUNT_OPENED â€” customers 1-75
INSERT INTO audit_events (
  id, actor, action, resource_type, resource_id, customer_id,
  details, ip_address, created_at
)
SELECT
  ('ae100000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'seed.customer' || lpad(n::text, 3, '0') || '@harborbank.dev',
  'ACCOUNT_OPENED',
  'account',
  'a0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'Primary USD account opened via seed',
  '192.168.' || (n % 256) || '.' || ((n * 7) % 256),
  NOW() - (n * 3 - 1 || ' days')::interval
FROM generate_series(1, 75) n
ON CONFLICT DO NOTHING;

-- KYC_UPDATE â€” customers 1-85
INSERT INTO audit_events (
  id, actor, action, resource_type, resource_id, customer_id,
  details, ip_address, created_at
)
SELECT
  ('ae200000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'demo.admin',
  CASE WHEN n <= 75 THEN 'KYC_VERIFIED' ELSE 'KYC_REJECTED' END,
  'customer',
  'c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  CASE
    WHEN n <= 75 THEN 'KYC verified for customer ' || n
    ELSE              'KYC rejected â€” documents insufficient for customer ' || n
  END,
  '10.0.0.1',
  NOW() - (n * 2 || ' days')::interval
FROM generate_series(1, 85) n
ON CONFLICT DO NOTHING;

-- LOAN decisions â€” customers 1-60
INSERT INTO audit_events (
  id, actor, action, resource_type, resource_id, customer_id,
  details, ip_address, created_at
)
SELECT
  ('ae300000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'demo.admin',
  CASE
    WHEN n <= 20 THEN 'LOAN_ACTIVATED'
    WHEN n <= 35 THEN 'LOAN_APPROVED'
    WHEN n <= 47 THEN 'LOAN_REVIEW'
    WHEN n <= 55 THEN 'LOAN_APPLIED'
    ELSE              'LOAN_REJECTED'
  END,
  'loan',
  'd0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'Loan decision for personal unsecured loan of customer ' || n,
  '10.0.0.1',
  NOW() - (n * 4 || ' hours')::interval
FROM generate_series(1, 60) n
ON CONFLICT DO NOTHING;

-- CARD_ISSUED â€” customers 1-75
INSERT INTO audit_events (
  id, actor, action, resource_type, resource_id, customer_id,
  details, ip_address, created_at
)
SELECT
  ('ae400000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'seed.customer' || lpad(n::text, 3, '0') || '@harborbank.dev',
  'CARD_ISSUED',
  'card',
  'ca000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'VISA DEBIT card issued ending in ' || lpad((n * 37 % 10000)::text, 4, '0'),
  '192.168.' || (n % 256) || '.' || ((n * 3) % 256),
  NOW() - (n * 3 - 1 || ' days')::interval
FROM generate_series(1, 75) n
ON CONFLICT DO NOTHING;

-- CARD_FROZEN / CARD_CANCELLED â€” customers 66-75
INSERT INTO audit_events (
  id, actor, action, resource_type, resource_id, customer_id,
  details, ip_address, created_at
)
SELECT
  ('ae500000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'demo.support',
  CASE WHEN n <= 70 THEN 'CARD_FROZEN' ELSE 'CARD_CANCELLED' END,
  'card',
  'ca000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  CASE WHEN n <= 70 THEN 'Card frozen â€” suspicious activity reported' ELSE 'Card cancelled at customer request' END,
  '10.0.0.2',
  NOW() - (n || ' days')::interval
FROM generate_series(66, 75) n
ON CONFLICT DO NOTHING;

-- LOGIN events â€” all 100 customers
INSERT INTO audit_events (
  id, actor, action, resource_type, resource_id, customer_id,
  details, ip_address, created_at
)
SELECT
  ('ae600000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'seed.customer' || lpad(n::text, 3, '0') || '@harborbank.dev',
  'LOGIN',
  'session',
  NULL,
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'Login from ' || (ARRAY['web','mobile','mobile','web','tablet'])[ (n%5) + 1 ],
  '203.' || (n % 256) || '.' || ((n*5) % 256) || '.' || ((n*11) % 256),
  NOW() - (n * 2 || ' hours')::interval
FROM generate_series(1, 100) n
ON CONFLICT DO NOTHING;

-- ACCOUNT_SUSPENDED â€” customers 93-97
INSERT INTO audit_events (
  id, actor, action, resource_type, resource_id, customer_id,
  details, ip_address, created_at
)
SELECT
  ('ae700000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'demo.admin',
  'CUSTOMER_SUSPENDED',
  'customer',
  'c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
  ('c0000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
  'Account suspended â€” compliance review initiated',
  '10.0.0.1',
  NOW() - ((n - 90) * 2 || ' days')::interval
FROM generate_series(93, 97) n
ON CONFLICT DO NOTHING;

\echo ''
\echo '================================================================'
\echo ' Harbor Bank seed complete.'
\echo '  customers     : 100  (all with support PIN 1234, bcrypt-hashed)'
\echo '  accounts      : 150  (75 primary + 50 secondary + 20 savings + 5 pending approval)'
\echo '  cards         : 110  (75 VISA + 35 MASTERCARD)'
\echo '  loans         :  95  (60 personal + 30 auto + 21 home improv. + 4 demo-user)'
\echo '  transactions  : ~246 (200 primary + 40 cross-currency + 6 pending approval)'
\echo '  beneficiaries : 100  (60 external + 40 internal)'
\echo '  payments      : 110  (70 batch-1 + 40 batch-2)'
\echo '  notifications : ~290 (welcome+KYC+loan+tx+security+suspend)'
\echo '  audit events  : ~380 (created+opened+kyc+loan+card+login+...)'
\echo '================================================================'
