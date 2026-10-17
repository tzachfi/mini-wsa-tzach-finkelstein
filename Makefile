.PHONY: up down logs verify db

up:
	docker compose up --build

down:
	docker compose down

logs:
	docker compose logs -f

verify:
	./mvnw -B verify

db:
	docker compose -f docker-compose.yml -f docker-compose.db.yml up -d --wait postgres
