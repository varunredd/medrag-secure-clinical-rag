SHELL := /bin/bash

.PHONY: bootstrap up up-dev rebuild-web down logs test smoke clean
bootstrap:
	./scripts/bootstrap-dev.sh

up:
	docker compose --profile prod-like up --build -d

up-dev:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml --profile dev up --build -d

rebuild-web:
	docker compose build web
	docker compose up -d --force-recreate web

down:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml down --remove-orphans || docker compose down

logs:
	docker compose --profile prod-like logs -f --tail=200

test:
	cd apps/api && ./mvnw test
	cd services/ai && python -m pytest
	cd apps/web && npm test

smoke:
	./scripts/smoke-test.sh

clean:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v --remove-orphans || true
	rm -rf secrets .env apps/api/target services/ai/.pytest_cache services/ai/**/__pycache__ apps/web/.next apps/web/node_modules
