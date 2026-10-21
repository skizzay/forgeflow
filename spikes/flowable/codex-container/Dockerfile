FROM ubuntu:24.04
ARG CODEX_VERSION=0.163.0
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates nodejs npm openjdk-21-jdk-headless git curl tini \
    && npm install -g @openai/codex@${CODEX_VERSION} \
    && rm -rf /var/lib/apt/lists/* /root/.npm \
    && useradd --create-home --uid 10001 developer
COPY ConnectProxy.java SandboxVerifier.java /opt/forgeflow/
RUN javac -d /opt/forgeflow /opt/forgeflow/*.java
USER 10001:10001
ENV HOME=/home/developer CODEX_HOME=/home/developer/.codex
WORKDIR /workspace
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["sleep", "1800"]
