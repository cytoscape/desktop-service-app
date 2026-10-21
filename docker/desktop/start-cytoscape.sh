#!/bin/sh
# Starts Cytoscape on a virtual display with CyREST on port 1234.
set -e

# A container restart keeps /tmp, and the previous Xvfb was killed without cleaning up. Its stale
# lock would stop a new Xvfb from starting, leaving Cytoscape without a display.
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1

Xvfb :1 -screen 0 1600x1200x24 -nolisten tcp &
XVFB_PID=$!
export DISPLAY=:1
for i in $(seq 1 100); do
    [ -e /tmp/.X11-unix/X1 ] && break
    if ! kill -0 "$XVFB_PID" 2>/dev/null; then
        echo "Xvfb exited before the display was ready" >&2
        exit 1
    fi
    sleep 0.1
done
[ -e /tmp/.X11-unix/X1 ] || { echo "Xvfb display :1 never came up" >&2; exit 1; }

# cytoscape.sh starts an interactive console that shuts Cytoscape down when its stdin closes,
# which is the normal state in a container. A never-ending pipe keeps stdin open.
mkdir -p /root/CytoscapeConfiguration/3
exec sh -c 'sleep infinity | /opt/cytoscape/cytoscape.sh -R 1234 > /root/CytoscapeConfiguration/3/console.log 2>&1'
