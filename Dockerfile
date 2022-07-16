FROM gradle:7.4.2-jdk17-alpine AS build

WORKDIR /gradle

COPY build.gradle .
COPY settings.gradle .
COPY modules ./modules

RUN gradle downloadDependencies --no-daemon

COPY . .

RUN gradle assemble installDist --no-daemon

FROM amazoncorretto:17-alpine3.15

COPY --from=build /gradle/build/install/matcha/ .

RUN addgroup -S -g 1337 matcha && adduser -S -D -H -u 1337 -s /sbin/nologin -G matcha matcha
USER matcha:matcha

ENTRYPOINT ["sh", "bin/matcha"]
