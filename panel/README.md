# Panel de TV DIGITAL UPDATES

Un solo servicio que reemplaza a nginx + filebrowser:

- `https://apps.tvdigital.shop/catalog.json`: lo que leen los TVs (misma URL de siempre).
- `https://apps.tvdigital.shop/media/...`: íconos e imágenes subidas desde el panel.
- `https://panel.tvdigital.shop`: panel con clave para editar todo y **subir APKs**.
  El panel saca solo el package, la versión, el SHA256 y el ícono, publica el APK
  como Release de GitHub y actualiza el catálogo.

## Instalación en EasyPanel

1. **Token de GitHub**: en <https://github.com/settings/personal-access-tokens/new>
   crear un token *fine-grained*. En *Repository access* elegir **Only select
   repositories → autupp**. En *Permissions → Repository permissions* poner
   **Contents: Read and write**. Copiar el token.
2. **Crear el servicio**: *+ Service → App*, nombre `panel`.
   - *Source*: Git, `https://github.com/satoshie530-svg/autupp.git`, rama `main`,
     **Build path `/panel`**, build con Dockerfile.
   - *Environment*:
     ```
     PANEL_PASSWORD=una-clave-larga-que-elijas
     GITHUB_TOKEN=el-token-del-paso-1
     GITHUB_REPO=satoshie530-svg/autupp
     PUBLIC_BASE_URL=https://apps.tvdigital.shop
     ```
   - *Mounts*: volumen `panel-data` montado en **`/data`**.
   - *Domains*: `panel.tvdigital.shop`, puerto **80**.
   - *Deploy*.
3. **Primera carga**: entrar a `https://panel.tvdigital.shop` con la clave. Ofrece
   importar el catálogo actual de apps.tvdigital.shop: aceptar y tocar
   **💾 Guardar y publicar**.
4. **Pasar el dominio de los TVs**: quitar `apps.tvdigital.shop` del servicio
   nginx y agregarlo al servicio `panel` (puerto 80). Abrir
   `https://apps.tvdigital.shop/catalog.json` y verificar que se ve el catálogo.
5. Cuando todo ande, apagar los servicios nginx y filebrowser.

Si `panel.tvdigital.shop` no resuelve, falta el registro DNS (A apuntando al VPS,
igual que `apps` y `admin`).

## Datos (volumen `/data`)

| Ruta | Qué es |
|---|---|
| `catalog.json` | el catálogo publicado |
| `backups/` | una copia por cada guardado (las últimas 30) |
| `media/` | íconos, miniaturas, logos e imágenes destacadas |
| `tmp/` | APKs a medio subir (se limpian solos a las 24 h) |

Para volver atrás, se copia un archivo de `backups/` sobre `catalog.json` desde
la consola del servicio, o se abre con *Herramientas → Abrir archivo JSON* y se guarda.
