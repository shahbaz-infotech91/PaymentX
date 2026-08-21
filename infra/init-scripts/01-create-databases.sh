#!/bin/bash
# Creates one Postgres database PER SERVICE, not one shared "paymentx" database.
#
# WHY separate databases instead of separate schemas in one database, or
# worse, one shared schema:
#   A shared database is the #1 way microservices silently re-couple.
#   The moment Validation Service can `SELECT * FROM payment_service.transactions`
#   directly, you no longer have independent services - you have a distributed
#   monolith with extra network hops. Separate databases make it a COMPILE-TIME
#   impossibility for one service to reach into another's data, forcing all
#   cross-service data access through the Kafka event bus or a real API call
#   - which is the whole point of the architecture.
set -e
set -u

function create_database() {
  local database=$1
  echo "Creating database '$database'"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE $database;
    GRANT ALL PRIVILEGES ON DATABASE $database TO $POSTGRES_USER;
EOSQL
}

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
  echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
  for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
    create_database "$db"
  done
  echo "Multiple databases created"
fi
