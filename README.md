# Matcha

Tea's global broker for ratelimits and other cross-JVM requests.
Matcha is necessary for Tea clusters to communicate (and thus necessary for Tea to run on v2.0.0 and beyond).

Communication with Kafka is generally done through the `kafka` Docker network. Should this network not yet exist, create it using

```sh
sudo docker network create kafka
```

Now you can simply start Kafka as well as Matcha using

```sh
sudo docker compose up -d
```

To communicate with Kafka, make sure to add the containers to the `kafka` network
and point the connection string to `kafka:9092`.
