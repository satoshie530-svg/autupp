# Panel de TV DIGITAL UPDATES: sirve catalog.json + /media a los TVs y el panel
# de administración (subida de APKs → GitHub Releases, imágenes, edición).
#
# En EasyPanel: servicio "App" construido desde este repo con ruta de build
# /panel, un volumen montado en /data y las variables de entorno de README.md.
FROM php:8.3-apache

RUN apt-get update \
 && apt-get install -y --no-install-recommends libzip-dev unzip \
 && docker-php-ext-install zip \
 && rm -rf /var/lib/apt/lists/*

# aapt2 oficial de Google (el mismo que usa Gradle) para leer package,
# versión, nombre e ícono de cada APK.
ARG AAPT2_VERSION=8.13.2-14304508
RUN curl -fsSL -o /tmp/aapt2.jar \
      "https://dl.google.com/android/maven2/com/android/tools/build/aapt2/${AAPT2_VERSION}/aapt2-${AAPT2_VERSION}-linux.jar" \
 && unzip -j /tmp/aapt2.jar aapt2 -d /usr/local/bin \
 && chmod +x /usr/local/bin/aapt2 \
 && rm /tmp/aapt2.jar \
 && aapt2 version

RUN a2enmod headers
COPY apache-panel.conf /etc/apache2/conf-enabled/panel.conf
COPY php-panel.ini /usr/local/etc/php/conf.d/panel.ini
COPY public/ /var/www/html/

# /data es el volumen persistente: catalog.json, backups/, media/ y tmp/.
VOLUME /data
CMD ["sh", "-c", "mkdir -p /data/media /data/tmp /data/backups && chown -R www-data:www-data /data && exec apache2-foreground"]
