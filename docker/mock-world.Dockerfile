FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY scripts/mock_world.py /app/mock_world.py

USER 10001:10001

EXPOSE 9210

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:9210/health', timeout=2)" || exit 1

ENTRYPOINT ["python", "-u", "/app/mock_world.py", "--port", "9210"]
