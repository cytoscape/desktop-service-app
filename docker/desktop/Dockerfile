# Cytoscape Desktop in a container, for the integration tests (make integration-test).
#
# A full JDK is required: the JRE image lacks the jdk.unsupported.desktop module that Cytoscape's
# bundled JavaFX needs, and Cytoscape fails at launch without it. No desktop environment is needed;
# Cytoscape runs against a bare X server (Xvfb).
FROM eclipse-temurin:17-jdk

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb libxext6 libxrender1 libxtst6 libxi6 fontconfig fonts-dejavu-core curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# The Cytoscape Linux installer (install4j). Override to test another Cytoscape version:
#   make integration-test CYTOSCAPE_INSTALLER_URL=<url> [CYTOSCAPE_INSTALLER_SHA256=<sha256>]
# The checksum is verified when given; leave it empty to skip the check.
ARG CYTOSCAPE_INSTALLER_URL
ARG CYTOSCAPE_INSTALLER_SHA256=
RUN test -n "$CYTOSCAPE_INSTALLER_URL" || { echo "CYTOSCAPE_INSTALLER_URL build arg is required" >&2; exit 1; } \
    && curl -fsSL -o /tmp/cytoscape-installer.sh "$CYTOSCAPE_INSTALLER_URL" \
    && if [ -n "$CYTOSCAPE_INSTALLER_SHA256" ]; then \
           echo "$CYTOSCAPE_INSTALLER_SHA256  /tmp/cytoscape-installer.sh" | sha256sum -c -; \
       fi \
    && INSTALL4J_JAVA_HOME="$JAVA_HOME" sh /tmp/cytoscape-installer.sh -q -dir /opt/cytoscape \
    && rm /tmp/cytoscape-installer.sh \
    && test -x /opt/cytoscape/cytoscape.sh

COPY start-cytoscape.sh /usr/local/bin/start-cytoscape.sh
RUN chmod +x /usr/local/bin/start-cytoscape.sh

# A stand-in web browser. Cytoscape opens URLs with xdg-open when no desktop browser is available;
# this one records them in /tmp/opened-urls so the tests can check openURL results.
COPY xdg-open /usr/local/bin/xdg-open
RUN chmod +x /usr/local/bin/xdg-open

EXPOSE 1234
CMD ["/usr/local/bin/start-cytoscape.sh"]
