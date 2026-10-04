<?php
/**
 * API del panel de TV DIGITAL UPDATES.
 *
 * Guarda el catálogo, recibe APKs en fragmentos, les lee los datos con aapt2,
 * los publica como Release de GitHub y recibe imágenes. Cada pedido tiene que
 * traer la clave del panel en el header X-Panel-Key.
 *
 * Variables de entorno (ver README.md):
 *   PANEL_PASSWORD   clave para entrar al panel (obligatoria)
 *   GITHUB_TOKEN     token con permiso "Contents: read and write" sobre el repo
 *   GITHUB_REPO      dueño/repo donde se publican los APK
 *   PUBLIC_BASE_URL  dominio desde el que los TVs leen catalog.json y /media
 *   IMPORT_URL       catálogo a importar la primera vez (opcional)
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$DATA = rtrim(getenv('DATA_DIR') ?: '/data', '/\\');
$CFG = [
    'password' => (string) getenv('PANEL_PASSWORD'),
    'token'    => (string) getenv('GITHUB_TOKEN'),
    'repo'     => getenv('GITHUB_REPO') ?: 'satoshie530-svg/autupp',
    'base'     => rtrim(getenv('PUBLIC_BASE_URL') ?: 'https://apps.tvdigital.shop', '/'),
    'aapt2'    => getenv('AAPT2') ?: 'aapt2',
    'import'   => getenv('IMPORT_URL') ?: 'https://apps.tvdigital.shop/catalog.json',
    'api'      => rtrim(getenv('GITHUB_API') ?: 'https://api.github.com', '/'), // otro valor solo para pruebas
];

const MAX_APK_BYTES = 400 * 1024 * 1024;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const IMAGE_KINDS = ['icons', 'featured', 'tutorials', 'channels'];

function respond(array $data, int $code = 200): never
{
    http_response_code($code);
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function fail(string $msg, int $code = 400): never
{
    respond(['ok' => false, 'error' => $msg], $code);
}

function data_dir(string $sub): string
{
    global $DATA;
    $dir = "$DATA/$sub";
    if (!is_dir($dir) && !@mkdir($dir, 0775, true) && !is_dir($dir)) {
        fail("No se pudo crear $sub/ en el volumen de datos", 500);
    }
    return $dir;
}

function require_post(): void
{
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') fail('Método no permitido', 405);
}

function upload_id(): string
{
    $id = (string) ($_GET['id'] ?? '');
    if (!preg_match('/^[a-z0-9]{16,40}$/', $id)) fail('Identificador de subida inválido');
    return $id;
}

function tmp_file(string $id, string $ext): string
{
    return data_dir('tmp') . "/$id.$ext";
}

/** Borra restos de subidas abandonadas (más de un día). */
function cleanup_tmp(): void
{
    foreach (glob(data_dir('tmp') . '/*') ?: [] as $f) {
        if (is_file($f) && filemtime($f) < time() - 86400) @unlink($f);
    }
}

/* ======================= Autenticación ======================= */

if ($CFG['password'] === '') fail('Falta configurar PANEL_PASSWORD en el servidor', 500);
if (!hash_equals($CFG['password'], (string) ($_SERVER['HTTP_X_PANEL_KEY'] ?? ''))) {
    usleep(500000); // frena intentos de adivinar la clave
    fail('Clave incorrecta', 403);
}

switch ($_GET['action'] ?? '') {
    case 'ping':
        respond([
            'ok' => true,
            'github' => $CFG['token'] !== '',
            'repo' => $CFG['repo'],
            'hasCatalog' => is_file("$DATA/catalog.json"),
        ]);
    case 'save':    require_post(); action_save();
    case 'import':  action_import();
    case 'chunk':   require_post(); action_chunk();
    case 'analyze': action_analyze();
    case 'publish': require_post(); action_publish();
    case 'result':  action_result();
    case 'discard': require_post(); action_discard();
    case 'image':   require_post(); action_image();
    default:        fail('Acción desconocida', 404);
}

/* ======================= Catálogo ======================= */

function action_save(): never
{
    global $DATA;
    $data = json_decode((string) file_get_contents('php://input'));
    if (!is_object($data) || !isset($data->apps) || !is_array($data->apps)) {
        fail('El catálogo no es válido (falta la lista "apps")');
    }
    $json = json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    $dest = "$DATA/catalog.json";

    // Una copia por guardado; se conservan las últimas 30.
    if (is_file($dest)) {
        $dir = data_dir('backups');
        @copy($dest, "$dir/catalog-" . date('Ymd-His') . '.json');
        $all = glob("$dir/catalog-*.json") ?: [];
        sort($all);
        foreach (array_slice($all, 0, max(0, count($all) - 30)) as $old) @unlink($old);
    }

    // Escritura atómica: los TVs nunca leen un archivo a medio escribir.
    $tmp = "$dest.tmp";
    if (@file_put_contents($tmp, $json) === false || !@rename($tmp, $dest)) {
        fail('No se pudo escribir catalog.json (permisos del volumen)', 500);
    }
    respond(['ok' => true, 'bytes' => strlen($json)]);
}

/** Trae el catálogo del servidor anterior para la primera carga. */
function action_import(): never
{
    global $CFG;
    $url = (string) ($_GET['url'] ?? $CFG['import']);
    if (!preg_match('#^https?://#', $url)) fail('URL inválida');
    [$code, $body] = http_get($url);
    if ($code !== 200) fail("El servidor respondió HTTP $code");
    $data = json_decode($body);
    if (!is_object($data)) fail('Lo que devolvió esa URL no es un catálogo JSON');
    respond(['ok' => true, 'catalog' => $data]);
}

/* ======================= APK: subida y análisis ======================= */

/**
 * Recibe el APK en fragmentos (el proxy de EasyPanel corta pedidos largos).
 * El parámetro offset permite reintentar un fragmento sin duplicarlo.
 */
function action_chunk(): never
{
    $id = upload_id();
    $offset = (int) ($_GET['offset'] ?? -1);
    $body = file_get_contents('php://input');
    if ($body === false || $body === '') fail('Llegó un fragmento vacío');
    $len = strlen($body);
    $path = tmp_file($id, 'apk');

    if ($offset === 0) {
        cleanup_tmp();
        if (file_put_contents($path, $body) === false) fail('No se pudo guardar el archivo', 500);
        respond(['ok' => true, 'size' => $len]);
    }

    clearstatcache(true, $path);
    $have = is_file($path) ? filesize($path) : -1;
    if ($have === $offset + $len) respond(['ok' => true, 'size' => $have]); // reintento de algo ya recibido
    if ($have !== $offset) fail('La subida se interrumpió, probá de nuevo');
    if ($offset + $len > MAX_APK_BYTES) {
        @unlink($path);
        fail('El APK supera el máximo de 400 MB');
    }
    if (file_put_contents($path, $body, FILE_APPEND) === false) fail('No se pudo guardar el archivo', 500);
    respond(['ok' => true, 'size' => $offset + $len]);
}

function action_analyze(): never
{
    $id = upload_id();
    $apk = tmp_file($id, 'apk');
    if (!is_file($apk)) fail('No encuentro el APK subido, probá de nuevo');

    $badging = aapt2(['dump', 'badging', $apk]);
    if (!preg_match('/^package: (.*)$/m', $badging, $m)) {
        fail('No se pudo leer el APK. ¿Es un APK válido? (' . first_line($badging) . ')');
    }
    $pkg = quoted_attr($m[1], 'name');
    $versionCode = quoted_attr($m[1], 'versionCode');
    if ($pkg === '' || !ctype_digit($versionCode)) fail('El APK no informa package o versionCode');

    $label = match_one("/^application-label:'(.*)'$/m", $badging)
        ?: match_one("/^application: label='([^']*)'/m", $badging)
        ?: $pkg;
    $abis = [];
    if (preg_match('/^native-code: (.*)$/m', $badging, $nm)) {
        preg_match_all("/'([^']+)'/", $nm[1], $am);
        $abis = $am[1];
    }

    // Ícono: el de mayor densidad que declare el manifest.
    $icon = null;
    $best = -1;
    if (preg_match_all("/^application-icon-(\d+):'([^']+)'/m", $badging, $im, PREG_SET_ORDER)) {
        foreach ($im as $row) {
            if ((int) $row[1] > $best) {
                $best = (int) $row[1];
                $icon = $row[2];
            }
        }
    }
    $iconSpec = null;
    if ($icon !== null) {
        try {
            $iconSpec = (new IconResolver($apk))->resolveBadgePath($icon);
        } catch (Throwable $e) {
            $iconSpec = null; // sin ícono automático; el panel pide uno a mano
        }
    }

    $meta = [
        'packageName' => $pkg,
        'versionCode' => (int) $versionCode,
        'versionName' => quoted_attr($m[1], 'versionName'),
        'appName' => $label,
        'minSdk' => (int) match_one("/^(?:sdkVersion|minSdkVersion):'(\d+)'/m", $badging),
        'targetSdk' => (int) match_one("/^targetSdkVersion:'(\d+)'/m", $badging),
        'abis' => $abis,
        'tv' => str_contains($badging, 'leanback-launchable-activity'),
        'size' => filesize($apk),
        'sha256' => strtoupper(hash_file('sha256', $apk)),
    ];
    file_put_contents(tmp_file($id, 'json'), json_encode($meta));
    respond(['ok' => true] + $meta + ['icon' => $iconSpec]);
}

function action_discard(): never
{
    $id = upload_id();
    foreach (['apk', 'json', 'result'] as $ext) @unlink(tmp_file($id, $ext));
    respond(['ok' => true]);
}

/* ======================= APK: publicación en GitHub ======================= */

/**
 * Crea un Release con el APK. El resultado también queda en tmp/<id>.result
 * para que el panel lo recupere si la conexión se cortó mientras esperaba.
 */
function action_publish(): never
{
    global $CFG;
    $id = upload_id();
    $apk = tmp_file($id, 'apk');
    $metaFile = tmp_file($id, 'json');
    if (!is_file($apk) || !is_file($metaFile)) fail('No encuentro el APK subido, probá de nuevo');
    if ($CFG['token'] === '') fail('Falta configurar GITHUB_TOKEN en el servidor', 500);

    ignore_user_abort(true);
    set_time_limit(0);

    $meta = json_decode((string) file_get_contents($metaFile), true);
    $input = json_decode((string) file_get_contents('php://input'), true) ?: [];
    $appName = trim((string) ($input['appName'] ?? '')) ?: $meta['appName'];
    $changelog = trim((string) ($input['changelog'] ?? ''));
    $resultFile = tmp_file($id, 'result');
    @unlink($resultFile); // de un intento anterior que falló

    try {
        $slug = slugify($appName) ?: slugify($meta['packageName']);
        $ver = preg_replace('/[^A-Za-z0-9._-]/', '', (string) $meta['versionName']) ?: (string) $meta['versionCode'];
        $assetName = "$slug-v$ver.apk";

        $release = create_release("$slug-v$ver", $appName, $meta, $changelog);
        try {
            $asset = upload_asset($release['upload_url'], $apk, $assetName);
        } catch (Throwable $e) {
            // Que no quede un Release vacío colgado.
            github('DELETE', "/repos/{$CFG['repo']}/releases/{$release['id']}");
            github('DELETE', "/repos/{$CFG['repo']}/git/refs/tags/" . rawurlencode($release['tag_name']));
            throw $e;
        }

        $result = [
            'ok' => true,
            'packageName' => $meta['packageName'],
            'appName' => $appName,
            'versionCode' => $meta['versionCode'],
            'versionName' => $meta['versionName'],
            'sha256' => $meta['sha256'],
            'changelog' => $changelog,
            'downloadUrl' => $asset['browser_download_url'],
            'releaseUrl' => $release['html_url'],
        ];
    } catch (Throwable $e) {
        $result = ['ok' => false, 'error' => $e->getMessage()];
    }

    file_put_contents($resultFile, json_encode($result, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
    if ($result['ok']) {
        @unlink($apk);
        @unlink($metaFile);
    }
    respond($result, $result['ok'] ? 200 : 502);
}

function action_result(): never
{
    $id = upload_id();
    $file = tmp_file($id, 'result');
    if (!is_file($file)) respond(['ok' => true, 'done' => false]);
    respond(['ok' => true, 'done' => true, 'result' => json_decode((string) file_get_contents($file), true)]);
}

function create_release(string $tag, string $appName, array $meta, string $changelog): array
{
    global $CFG;
    $body = "$appName {$meta['versionName']} (versionCode {$meta['versionCode']})\n"
        . "Package: {$meta['packageName']}\nSHA256: {$meta['sha256']}"
        . ($changelog !== '' ? "\n\n$changelog" : '');

    // Si ese tag ya existe (se volvió a subir la misma versión), se le agrega la fecha.
    foreach ([$tag, $tag . '-' . date('YmdHis')] as $candidate) {
        [$code, $res] = github('POST', "/repos/{$CFG['repo']}/releases", [
            'tag_name' => $candidate,
            'name' => "$appName v{$meta['versionName']}",
            'body' => $body,
            'make_latest' => 'false',
        ]);
        if ($code === 201) return $res;
        $exists = $code === 422 && str_contains(json_encode($res), 'already_exists');
        if (!$exists) throw new RuntimeException('GitHub rechazó crear el Release: ' . github_error($code, $res));
    }
    throw new RuntimeException('No se pudo crear el Release: el tag ya existe');
}

function upload_asset(string $uploadUrl, string $file, string $name): array
{
    global $CFG;
    $url = preg_replace('/\{.*\}$/', '', $uploadUrl) . '?name=' . rawurlencode($name);
    $fh = fopen($file, 'rb');
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_UPLOAD => true,           // envía el archivo en streaming, sin cargarlo en memoria
        CURLOPT_CUSTOMREQUEST => 'POST',
        CURLOPT_INFILE => $fh,
        CURLOPT_INFILESIZE => filesize($file),
        CURLOPT_HTTPHEADER => array_merge(github_headers(), [
            'Content-Type: application/vnd.android.package-archive',
            'Expect:',
        ]),
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 20,
        CURLOPT_TIMEOUT => 1800,
    ]);
    $raw = curl_exec($ch);
    $code = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    $err = curl_error($ch);
    curl_close($ch);
    fclose($fh);
    if ($raw === false) throw new RuntimeException("No se pudo subir el APK a GitHub: $err");
    $res = json_decode((string) $raw, true) ?: [];
    if ($code !== 201 || empty($res['browser_download_url'])) {
        throw new RuntimeException('GitHub rechazó el APK: ' . github_error($code, $res));
    }
    return $res;
}

function github_headers(): array
{
    global $CFG;
    return [
        'Authorization: Bearer ' . $CFG['token'],
        'Accept: application/vnd.github+json',
        'X-GitHub-Api-Version: 2022-11-28',
        'User-Agent: tvdigital-panel',
    ];
}

/** @return array{0:int,1:array} */
function github(string $method, string $path, ?array $body = null): array
{
    global $CFG;
    $ch = curl_init($CFG['api'] . $path);
    curl_setopt_array($ch, [
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 20,
        CURLOPT_TIMEOUT => 60,
        CURLOPT_HTTPHEADER => array_merge(github_headers(), ['Content-Type: application/json']),
    ]);
    if ($body !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($body));
    $raw = curl_exec($ch);
    $code = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    $err = curl_error($ch);
    curl_close($ch);
    if ($raw === false) throw new RuntimeException("No se pudo conectar con GitHub: $err");
    return [$code, json_decode((string) $raw, true) ?: []];
}

function github_error(int $code, array $res): string
{
    $msg = $res['message'] ?? "HTTP $code";
    if ($code === 401) return 'el GITHUB_TOKEN es inválido o venció';
    if ($code === 403 || $code === 404) return "$msg (revisá que el token tenga permiso Contents: read and write sobre {$GLOBALS['CFG']['repo']})";
    return $msg;
}

/* ======================= Imágenes ======================= */

function action_image(): never
{
    global $CFG;
    $kind = (string) ($_GET['kind'] ?? '');
    if (!in_array($kind, IMAGE_KINDS, true)) fail('Tipo de imagen inválido');
    $body = (string) file_get_contents('php://input');
    if ($body === '') fail('No llegó ninguna imagen');
    if (strlen($body) > MAX_IMAGE_BYTES) fail('La imagen supera los 10 MB');

    $ext = image_ext($body);
    if ($ext === null) fail('El archivo no es una imagen PNG, JPG, WEBP o GIF');
    $name = date('Ymd-His') . '-' . bin2hex(random_bytes(4)) . ".$ext";
    if (file_put_contents(data_dir("media/$kind") . "/$name", $body) === false) {
        fail('No se pudo guardar la imagen (permisos del volumen)', 500);
    }
    respond(['ok' => true, 'url' => "{$CFG['base']}/media/$kind/$name"]);
}

function image_ext(string $bytes): ?string
{
    if (str_starts_with($bytes, "\x89PNG")) return 'png';
    if (str_starts_with($bytes, "\xFF\xD8\xFF")) return 'jpg';
    if (str_starts_with($bytes, 'GIF8')) return 'gif';
    if (str_starts_with($bytes, 'RIFF') && substr($bytes, 8, 4) === 'WEBP') return 'webp';
    return null;
}

function image_mime(string $ext): string
{
    return $ext === 'jpg' ? 'image/jpeg' : "image/$ext";
}

/* ======================= Utilidades ======================= */

function aapt2(array $args): string
{
    global $CFG;
    // La salida va a un archivo: leer megas de texto por un pipe es muy lento.
    $outFile = tempnam(sys_get_temp_dir(), 'aapt2');
    $cmd = escapeshellarg($CFG['aapt2']) . ' ' . implode(' ', array_map('escapeshellarg', $args))
        . ' > ' . escapeshellarg($outFile) . ' 2>&1';
    exec($cmd);
    $text = (string) file_get_contents($outFile);
    @unlink($outFile);
    return str_replace("\r\n", "\n", $text);
}

function quoted_attr(string $line, string $name): string
{
    return preg_match("/\\b$name='([^']*)'/", $line, $m) ? $m[1] : '';
}

function match_one(string $re, string $text): string
{
    return preg_match($re, $text, $m) ? $m[1] : '';
}

function first_line(string $text): string
{
    return mb_substr(trim(strtok($text, "\n") ?: 'sin detalle'), 0, 160);
}

function slugify(string $s): string
{
    $s = iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $s) ?: $s;
    return trim(preg_replace('/[^a-z0-9]+/', '-', strtolower($s)), '-');
}

/** @return array{0:int,1:string} */
function http_get(string $url): array
{
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_TIMEOUT => 20,
        CURLOPT_HTTPHEADER => ['Cache-Control: no-cache'],
    ]);
    $raw = curl_exec($ch);
    $code = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    $err = curl_error($ch);
    curl_close($ch);
    if ($raw === false) fail("No se pudo conectar: $err", 502);
    return [$code, (string) $raw];
}

/**
 * Arma una descripción del ícono que el panel dibuja en el navegador.
 *
 * La mayoría de las apps modernas no traen un PNG del ícono sino un
 * "adaptive-icon" (fondo + frente) o un vector. Acá se resuelve el árbol de
 * recursos (colores, vectores, degradados, imágenes) a un JSON autocontenido;
 * index.html lo convierte a SVG/canvas y sube el PNG resultante.
 */
final class IconResolver
{
    private const MAX_DEPTH = 8;
    private const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    /** @android:color/white, black y transparent, los únicos del sistema habituales en íconos. */
    private const ANDROID_COLORS = ['@0x0106000b' => '#ffffffff', '@0x0106000c' => '#ff000000', '@0x0106000d' => '#00000000'];
    private const DENSITY = ['ldpi' => 0.75, 'mdpi' => 1, 'tvdpi' => 1.33, 'hdpi' => 1.5, 'xhdpi' => 2, 'xxhdpi' => 3, 'xxxhdpi' => 4];

    private ZipArchive $zip;
    /** @var array<string, array{name:string, entries:list<array{config:string, file?:string, value?:string}>}> */
    private array $res;

    public function __construct(private readonly string $apk)
    {
        $this->zip = new ZipArchive();
        if ($this->zip->open($apk) !== true) throw new RuntimeException('APK ilegible');
        $this->res = self::parseResources(aapt2(['dump', 'resources', $apk]));
    }

    public function resolveBadgePath(string $path): ?array
    {
        foreach ($this->res as $id => $r) {
            foreach ($r['entries'] as $e) {
                if (($e['file'] ?? null) === $path) return $this->resolveRef($id, 0);
            }
        }
        return $this->resolveFile($path, 0);
    }

    /** Devuelve un color "#aarrggbb", un {type:image} o un {type:xml}, o null. */
    private function resolveRef(string $id, int $depth): array|string|null
    {
        $r = $this->res[$id] ?? null;
        if ($r === null || $depth > self::MAX_DEPTH) return null;

        $values = array_values(array_filter($r['entries'], fn($e) => isset($e['value'])));
        if ($values) {
            $v = $this->pickDefault($values)['value'];
            if (preg_match('/^@(0x[0-9a-f]{8})$/', $v, $m)) return $this->resolveRef($m[1], $depth + 1);
            return preg_match('/^#[0-9a-f]{6,8}$/i', $v) ? $v : null;
        }

        // Archivos: el mejor raster (por densidad) queda como respaldo del XML.
        $raster = null;
        $rasterDensity = -1.0;
        $xml = [];
        foreach ($r['entries'] as $e) {
            if (!isset($e['file'])) continue;
            if (str_ends_with($e['file'], '.xml')) {
                $xml[] = $e;
                continue;
            }
            $d = $this->density($e['config']);
            if ($d > $rasterDensity) {
                $rasterDensity = $d;
                $raster = $e['file'];
            }
        }
        $fallback = $raster !== null ? $this->resolveFile($raster, $depth + 1) : null;
        if ($xml) {
            $spec = $this->resolveFile($this->pickXml($xml)['file'], $depth + 1);
            if ($spec !== null) {
                if ($fallback !== null && ($fallback['type'] ?? '') === 'image') $spec['fallback'] = $fallback;
                return $spec;
            }
        }
        return $fallback;
    }

    private function resolveFile(string $path, int $depth): ?array
    {
        if ($depth > self::MAX_DEPTH) return null;
        $bytes = $this->zip->getFromName($path);
        if ($bytes === false) return null;

        $ext = image_ext($bytes);
        if ($ext !== null) {
            if (strlen($bytes) > self::MAX_IMAGE_BYTES) return null;
            return ['type' => 'image', 'src' => 'data:' . image_mime($ext) . ';base64,' . base64_encode($bytes)];
        }
        if (!str_starts_with($bytes, "\x03\x00\x08\x00")) return null; // no es XML compilado

        $tree = self::parseXmlTree(aapt2(['dump', 'xmltree', '--file', $path, $this->apk]));
        if ($tree === null) return null;
        return ['type' => 'xml', 'tree' => $this->resolveTree($tree, $depth)];
    }

    private function resolveTree(array $node, int $depth): array
    {
        foreach ($node['attrs'] as $k => $v) {
            if (!is_string($v)) continue;
            if (isset(self::ANDROID_COLORS[$v])) {
                $node['attrs'][$k] = self::ANDROID_COLORS[$v];
            } elseif (preg_match('/^@(0x7f[0-9a-f]{6})$/', $v, $m)) {
                $node['attrs'][$k] = $this->resolveRef($m[1], $depth + 1);
            }
        }
        foreach ($node['children'] as $i => $child) {
            $node['children'][$i] = $this->resolveTree($child, $depth);
        }
        return $node;
    }

    private function density(string $config): float
    {
        foreach (self::DENSITY as $name => $d) {
            if (preg_match("/(^|-)$name(-|$)/", $config)) return $d;
        }
        return 0.5; // sin densidad (nodpi / por defecto): última opción
    }

    /** Prefiere la variante sin calificadores (ni modo noche, ni idioma, etc.). */
    private function pickDefault(array $entries): array
    {
        foreach ($entries as $e) if ($e['config'] === '') return $e;
        foreach ($entries as $e) if (!str_contains($e['config'], 'night')) return $e;
        return $entries[0];
    }

    private function pickXml(array $entries): array
    {
        foreach ($entries as $e) if (str_contains($e['config'], 'anydpi') && !str_contains($e['config'], 'night')) return $e;
        return $this->pickDefault($entries);
    }

    private static function parseResources(string $dump): array
    {
        $res = [];
        $cur = null;
        foreach (explode("\n", $dump) as $line) {
            if (preg_match('/^\s*resource (0x[0-9a-f]{8}) (\S+)/', $line, $m)) {
                $cur = $m[1];
                $res[$cur] = ['name' => $m[2], 'entries' => []];
            } elseif ($cur !== null && preg_match('/^\s+\(([^)]*)\) \(file\) (\S+)/', $line, $m)) {
                $res[$cur]['entries'][] = ['config' => $m[1], 'file' => $m[2]];
            } elseif ($cur !== null && preg_match('/^\s+\(([^)]*)\) (\S.*)$/', $line, $m)) {
                $res[$cur]['entries'][] = ['config' => $m[1], 'value' => trim($m[2])];
            } elseif (preg_match('/^\s*(type|Package) /', $line)) {
                $cur = null;
            }
        }
        return $res;
    }

    /** Convierte la salida de "aapt2 dump xmltree" en {tag, attrs, children}. */
    private static function parseXmlTree(string $dump): ?array
    {
        $root = null;
        $stack = []; // [indentación, &nodo]
        foreach (explode("\n", $dump) as $line) {
            if (!preg_match('/^(\s*)([EA]): (.*)$/', $line, $m)) continue;
            $indent = strlen($m[1]);
            if ($m[2] === 'E') {
                $node = ['tag' => preg_replace('/ \(line=\d+\)$/', '', $m[3]), 'attrs' => [], 'children' => []];
                while ($stack && end($stack)[0] >= $indent) array_pop($stack);
                if (!$stack) {
                    if ($root !== null) break;
                    $root = $node;
                    $stack[] = [$indent, &$root];
                } else {
                    $parent = &$stack[count($stack) - 1][1];
                    $parent['children'][] = $node;
                    $stack[] = [$indent, &$parent['children'][count($parent['children']) - 1]];
                    unset($parent);
                }
            } elseif ($stack && preg_match('/^(?:[^=]*:)?([A-Za-z_]+)\(0x[0-9a-f]+\)=(.*)$/', $m[3], $am)) {
                $stack[count($stack) - 1][1]['attrs'][$am[1]] = self::attrValue($am[2]);
            }
        }
        return $root;
    }

    private static function attrValue(string $raw): string
    {
        if (preg_match('/^"(.*)" \(Raw: /', $raw, $m)) return $m[1];
        if (preg_match('/^"(.*)"$/', $raw, $m)) return $m[1];
        return $raw;
    }
}
