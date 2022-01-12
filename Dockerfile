# syntax=docker/dockerfile:1
FROM scratch

FROM gradle:latest
COPY . /matcha
WORKDIR /matcha
