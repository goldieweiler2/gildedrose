# Gilded Rose Shop API

A standalone REST API for the Gilded Rose Inn.

Requirements: Java 11 in your path.

## Getting Started

1. Clone the repository
2. cd to dir
3. To run from gradle - `./gradlew bootRun`

You can override the default properties by editing the `application-overrides.properties` file and 
adding properties and values that you want to override. Note that in this system, the `application-overrides.properties`
has been configured with the surge window set to 60s for easier testing.

## Build and run tests

1. cd to dir
2. Run `./gradlew clean build distJar`

This will build the system, execute unit/integration tests and create a JAR.

To execute the application from the jar:

1. cd to jar dir - `build/distributions`
2. Run `java -jar <jarName>`

## Rest API
The REST API produces JSON as this is a lightweight object representation which is the standard to REST data transfer (and implicitly supported in Spring Boot applications).

There are 4 endpoints available in this application:

POST `/api/gildedrose/1/inventory` - this will refresh the stock inventory. The stock inventory is provided in the resources file `inventory.json` and comprises 3 items with base quantities and prices.

`curl -X POST "http://localhost:8080/api/gildedrose/1/inventory"`

GET `/api/gildedrose/1/inventory` - this will retrieve the inventory. The response is a JSON document containing the items available for purchase. This endpoint is provided as the view and purchase item endpoints require the UUID of the item.

`curl "http://localhost:8080/api/gildedrose/1/inventory"`

GET `/api/gildedrose/1/item/{itemId}` - this will retrieve the requested item. Each call to this endpoint is monitored and surge pricing is applied when more than 10 requests are received in 60 mins for an item. If an unknown or invalid UUID is provided, the user will receive an HTTP 404 response.

`curl "http://localhost:8080/api/gildedrose/1/item/a04b8a12-bc87-49a5-9fa9-7f51b7dcad0f"`

POST `/api/gildedrose/1/item/{itemId}?quantity={quantity}` - this allows a user to purchase an item. The endpoint will return the purchased item and the price it was purchased for. As user cannot purchase more items than are currently in stock.
Note that this endpoint required authentication credentials (username: `admin`, password: `admin`). If there is insufficient stock available, the user will receive an HTTP 400 response.

`curl -X POST --user admin:admin "http://localhost:8080/api/gildedrose/1/item/a04b8a12-bc87-49a5-9fa9-7f51b7dcad0f?quantity=1"`

### Example Request

Note: By default, the server will run on port 8080.

```
curl "http://localhost:8080/api/gildedrose/1/item/a04b8a12-bc87-49a5-9fa9-7f51b7dcad0f"
```
### Example Response

```
{
   "uuid":"4575aae4-4974-4d67-9b7f-9e5292e0dbd4",
   "name":"Finest Earl Grey Tea",
   "description":"Leaf Tea - Earl Grey Tin - 80g",
   "price":8
}
```

## Design

The Gilded Rose API is a Spring Boot application. It's design is a proof of concept for
the surge pricing model and does not account for multiple instances of the application
being used to service requests (as request tracking is managed in memory).

The API endpoints produce JSON and are simple to use, as they both simply require the UUID
of the item that is being retrieved or purchased. UUIDs were chosen as they reliably allow
the API to return the correct data.

The surge pricing model has been implemented using an in memory expiring cache. When an item
is requested, it is added to the cache (Guava LoadingCache) with an expiryTime based on the 
settings set in the `application.properties`. The cache will expire items automatically so
there are no additional calls required to recalculate the status of the data.

Surge pricing works on a rolling window. The size of the cache will always represent the number
of requests received in the surge period as older requests automatically expire.

## Authentication

Only one endpoint requires authentication (the POST `/api/gildedrose/1/item/{itemId}?quantity={quantity}`). 
This uses Basic authentication for the POC and has hard-coded credentials. Although this is not the most
secure form of authentication (user/password combination is encoded, not encrypted), for a proof of concept
application, this demonstrates that the application is capable of security at the endpoint level.

In a production environment, Basic Authentication is acceptable if the system is running over SSL but
OAUTH would be a preferable implementation. Basic Authentication was chosen for its simplicity of
implementation in this instance.

## Enhancements

This implementation of the application runs on in-memory data, meaning that if it needed to run under heavy
load, it would not support adding additional instances (behind a load balancer), as surge pricing would not
return accurate or consistent results.

One enhancement that could be added to the application would be to replace the in-memory expiring cache
with a REDIS cache. A redis instance can be shared across multiple instances of the API server and operates
in a thread-safe manner ensuring the surge pricing would return accurate results.
