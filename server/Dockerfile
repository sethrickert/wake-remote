FROM python:3.13-alpine
RUN pip install --no-cache-dir segno==1.6.6
RUN addgroup -g 10001 wake && adduser -D -H -u 10001 -G wake wake && mkdir -p /data && chown 10001:10001 /data
WORKDIR /app
COPY --chown=10001:10001 wake_remote ./wake_remote
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["python", "-m", "wake_remote.app"]
