#!/usr/bin/env bash
set -e

echo "Waiting for PostgreSQL to be ready..."
until docker exec omniroute-postgres pg_isready -U postgres; do
  echo "PostgreSQL is unavailable - sleeping 2s..."
  sleep 2
done
echo "PostgreSQL is ready."

echo "Waiting for Kafka to be ready..."
until docker exec omniroute-kafka kafka-topics --bootstrap-server kafka:9092 --list > /dev/null 2>&1; do
  echo "Kafka is unavailable - sleeping 2s..."
  sleep 2
done
echo "Kafka is ready."

echo "Infrastructure is healthy."
