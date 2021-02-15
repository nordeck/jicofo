FROM  jitsi/jicofo
RUN   find /usr/share/jicofo  -name \*.jar -type f -delete
ADD   /tmp/jicofo    /usr/share/jicofo
VOLUME /config
