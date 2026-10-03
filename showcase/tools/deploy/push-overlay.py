#!/usr/bin/env python3
"""Push a new image = the deployed v13 image + one layer holding /app/server.js and /app/site/.

usage: push-overlay.py <deploy-dir> <new-tag>
<deploy-dir> holds server.js and site/ (required), and optionally:
  public/        files to add or replace under /app/public (e.g. a scrubbed steam.html)
  whiteouts.txt  one path per line, relative to /app, to DELETE from the image (OCI whiteout
                 entries, e.g. `public/shots` removes the old capture folder and everything in it)
Needs FLY_API_TOKEN. Prints the image ref to pass to `fly deploy --image`.
"""
import base64, gzip, hashlib, io, json, os, sys, tarfile, time, urllib.request, urllib.error

REG = 'https://registry.fly.io'
REPO = 'prismet-site-restless-horizon-217'
BASE_TAG = 'deployment-01KYE5MM868CFCAWKRR3N9CKB1'
OCI_MANIFEST = 'application/vnd.oci.image.manifest.v1+json'

deploy_dir, new_tag = sys.argv[1], sys.argv[2]
auth = 'Basic ' + base64.b64encode(f"x:{os.environ.get('FLY_API_TOKEN', '')}".encode()).decode()


def req(method, url, data=None, headers=None):
    h = {'Authorization': auth, **(headers or {})}
    r = urllib.request.Request(url if url.startswith('http') else REG + url, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=120) as resp:
            return resp.status, dict(resp.headers), resp.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read()


def sha(b):
    return 'sha256:' + hashlib.sha256(b).hexdigest()


DRY_RUN = bool(os.environ.get('DRY_RUN'))  # DRY_RUN=1: build the layer, list it, write <deploy-dir>/layer.tar.gz, push nothing

# 1. base manifest + config, straight from the registry
if not DRY_RUN:
    st, _, body = req('GET', f'/v2/{REPO}/manifests/{BASE_TAG}', headers={'Accept': OCI_MANIFEST})
    assert st == 200, (st, body[:200])
    manifest = json.loads(body)
    st, _, cfg_body = req('GET', f"/v2/{REPO}/blobs/{manifest['config']['digest']}")
    assert st == 200, st
    config = json.loads(cfg_body)

# 2. the new layer: app/server.js + app/site/** [+ app/public/** + whiteouts] (root-owned, world-readable, fixed mtime)
raw = io.BytesIO()
mtime = int(time.time())
with tarfile.open(fileobj=raw, mode='w', format=tarfile.PAX_FORMAT) as tar:
    def add(path, arc):
        ti = tar.gettarinfo(path, arcname=arc)
        ti.uid = ti.gid = 0
        ti.uname = ti.gname = 'root'
        ti.mtime = mtime
        ti.mode = 0o755 if ti.isdir() else 0o644
        if ti.isdir():
            tar.addfile(ti)
        else:
            with open(path, 'rb') as f:
                tar.addfile(ti, f)
    add(os.path.join(deploy_dir, 'server.js'), 'app/server.js')
    if os.path.exists(os.path.join(deploy_dir, 'edit-api.js')):
        add(os.path.join(deploy_dir, 'edit-api.js'), 'app/edit-api.js')
    site = os.path.join(deploy_dir, 'site')
    for root, dirs, files in os.walk(site):
        dirs.sort()
        rel = os.path.relpath(root, site)
        add(root, 'app/site' if rel == '.' else f'app/site/{rel}')
        for name in sorted(files):
            add(os.path.join(root, name), f'app/site/{name}' if rel == '.' else f'app/site/{rel}/{name}')
    public = os.path.join(deploy_dir, 'public')
    if os.path.isdir(public):
        for root, dirs, files in os.walk(public):
            dirs.sort()
            rel = os.path.relpath(root, public)
            if rel != '.':
                add(root, f'app/public/{rel}')
            for name in sorted(files):
                add(os.path.join(root, name), f'app/public/{name}' if rel == '.' else f'app/public/{rel}/{name}')
    wh = os.path.join(deploy_dir, 'whiteouts.txt')
    if os.path.exists(wh):
        for line in open(wh):
            rel = line.strip().strip('/')
            if not rel or rel.startswith('#'):
                continue
            parent, name = os.path.split(rel)
            ti = tarfile.TarInfo(f'app/{parent}/.wh.{name}' if parent else f'app/.wh.{name}')
            ti.size = 0; ti.mtime = mtime; ti.mode = 0o644; ti.uname = ti.gname = 'root'
            tar.addfile(ti)
            print('whiteout', rel, file=sys.stderr)
layer_raw = raw.getvalue()
layer_gz = gzip.compress(layer_raw, compresslevel=9, mtime=0)
if DRY_RUN:
    with tarfile.open(fileobj=io.BytesIO(layer_raw)) as t:
        names = t.getnames()
    print(f'dry run: {len(names)} entries, {len(layer_gz)} bytes gzipped; not pushed', file=sys.stderr)
    for n in names:
        if not n.startswith('app/site/'):
            print('  ', n, file=sys.stderr)
    open(os.path.join(deploy_dir, 'layer.tar.gz'), 'wb').write(layer_gz)
    sys.exit(0)
layer_desc = {'mediaType': 'application/vnd.oci.image.layer.v1.tar+gzip', 'digest': sha(layer_gz), 'size': len(layer_gz)}

# 3. config: one more diff_id and a history line saying what it is
config['rootfs']['diff_ids'].append(sha(layer_raw))
now = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
config['created'] = now
config.setdefault('history', []).append({'created': now, 'created_by': 'COPY server.js site/ [public/, whiteouts] # prismet.xyz (showcase/prismet-site, sagedeutschle/Bayzyl)'})
cfg_new = json.dumps(config, separators=(',', ':')).encode()
cfg_desc = {'mediaType': 'application/vnd.oci.image.config.v1+json', 'digest': sha(cfg_new), 'size': len(cfg_new)}


def upload(blob, digest):
    st, _, _ = req('HEAD', f'/v2/{REPO}/blobs/{digest}')
    if st == 200:
        return 'exists'
    st, h, body = req('POST', f'/v2/{REPO}/blobs/uploads/', data=b'')
    assert st == 202, (st, body[:300])
    loc = h.get('Location') or h.get('location')
    if loc.startswith('/'):
        loc = REG + loc
    sep = '&' if '?' in loc else '?'
    st, _, body = req('PUT', f'{loc}{sep}digest={digest}', data=blob, headers={'Content-Type': 'application/octet-stream', 'Content-Length': str(len(blob))})
    assert st == 201, (st, body[:300])
    return 'uploaded'


print('layer ', layer_desc['digest'], layer_desc['size'], upload(layer_gz, layer_desc['digest']))
print('config', cfg_desc['digest'], cfg_desc['size'], upload(cfg_new, cfg_desc['digest']))

# 4. manifest = base layers + ours, under the new tag
manifest['config'] = cfg_desc
manifest['layers'] = manifest['layers'] + [layer_desc]
manifest.pop('annotations', None)
man = json.dumps(manifest, separators=(',', ':')).encode()
st, h, body = req('PUT', f'/v2/{REPO}/manifests/{new_tag}', data=man, headers={'Content-Type': OCI_MANIFEST})
assert st == 201, (st, body[:300])
print('manifest', h.get('Docker-Content-Digest') or h.get('docker-content-digest'))
print(f'registry.fly.io/{REPO}:{new_tag}')
