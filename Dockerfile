# syntax=docker/dockerfile:1
FROM gradle:latest
COPY . /matcha
WORKDIR /matcha
