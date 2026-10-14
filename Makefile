.PHONY: up down logs verify

up:
	docker compose up --build


down:
	docker compose down

logs:
	docker compose logs -f

verify:
	./mvnw -q verify
