#!/bin/bash
# K6 Docker 실행 편의 스크립트

# 현재 디렉토리를 컨테이너에 마운트하여 K6 실행
docker run --rm \
  -v $(pwd):/src \
  -w /src \
  --network host \
  grafana/k6:latest "$@"