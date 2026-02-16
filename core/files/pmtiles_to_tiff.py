#!/usr/bin/env python3
"""
Convert a PMTiles terrain-RGB archive (single zoom level) into 1°×1° GeoTIFF
files compatible with GraphHopper's GLO30Provider.

Output: Int16 GeoTIFF with DEFLATE + Predictor=2 (3600×3600 pixels per 1° tile).

Dependencies:  pip install Pillow numpy
Requires:      gdal_translate (from GDAL) in PATH

Usage:
  python pmtiles_to_tiff.py input.pmtiles output_dir/ --zoom 11
  python pmtiles_to_tiff.py input.pmtiles output_dir/ --zoom 11 --encoding mapbox

Then in GraphHopper:
  ElevationProvider ep = new GLO30Provider("output_dir/");
"""

import argparse, gzip, io, math, os, struct, subprocess, sys, tempfile, time
from PIL import Image

NO_DATA = -32768
TILE_SIZE = 3600  # GLO30Provider: 3600×3600 pixels per 1°×1° tile

# ═══════════════════════════════════════════════════════════════════════════
# Self-contained PMTiles v3 reader (no external dependency)
# ═══════════════════════════════════════════════════════════════════════════

def _read_varint(data, pos):
    result, shift = 0, 0
    while pos < len(data):
        b = data[pos]; pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            break
        shift += 7
    return result, pos


def _deserialize_entries(data):
    pos = 0
    n, pos = _read_varint(data, pos)
    if n == 0:
        return []
    tile_ids, last = [], 0
    for _ in range(n):
        v, pos = _read_varint(data, pos); last += v; tile_ids.append(last)
    run_lengths = []
    for _ in range(n):
        v, pos = _read_varint(data, pos); run_lengths.append(v)
    lengths = []
    for _ in range(n):
        v, pos = _read_varint(data, pos); lengths.append(v)
    offsets = []
    for i in range(n):
        v, pos = _read_varint(data, pos)
        offsets.append(offsets[i - 1] + lengths[i - 1] if v == 0 and i > 0 else v - 1)
    return list(zip(tile_ids, run_lengths, offsets, lengths))


def _xy_to_hilbert(order, x, y):
    d = 0
    s = 1 << (order - 1)
    while s > 0:
        rx = 1 if (x & s) else 0
        ry = 1 if (y & s) else 0
        d += s * s * ((3 * rx) ^ ry)
        if ry == 0:
            if rx == 1:
                x, y = s - 1 - x, s - 1 - y
            x, y = y, x
        s >>= 1
    return d


def _zxy_to_id(z, x, y):
    if z == 0:
        return 0
    base = ((1 << (2 * z)) - 1) // 3  # closed-form: (4^z - 1) / 3
    return base + _xy_to_hilbert(z, x, y)


class PMTiles:
    def __init__(self, path):
        self.f = open(path, 'rb')
        self._parse_header()
        self.root_dir = self._read_dir(self._root_off, self._root_len)
        self._leaf_cache = {}

    def _parse_header(self):
        buf = self._raw(0, 127)
        if buf[:7] != b'PMTiles' or buf[7] != 3:
            raise ValueError("Not a valid PMTiles v3 file")
        q = struct.unpack_from('<11Q', buf, 8)
        self._root_off, self._root_len = q[0], q[1]
        self._leaf_off = q[4]
        self._data_off = q[6]
        self._int_comp = buf[97]
        self.min_zoom, self.max_zoom = buf[100], buf[101]
        lons_lats = struct.unpack_from('<iiii', buf, 102)
        self.min_lon = lons_lats[0] / 1e7
        self.min_lat = lons_lats[1] / 1e7
        self.max_lon = lons_lats[2] / 1e7
        self.max_lat = lons_lats[3] / 1e7

    def _raw(self, off, n):
        self.f.seek(off)
        return self.f.read(n)

    def _read_dir(self, off, length):
        data = self._raw(off, length)
        if self._int_comp == 2:  # GZIP
            try:
                data = gzip.decompress(data)
            except Exception:
                pass
        return _deserialize_entries(data)

    def _leaf(self, off, length):
        if off not in self._leaf_cache:
            self._leaf_cache[off] = self._read_dir(self._leaf_off + off, length)
        return self._leaf_cache[off]

    def get(self, z, x, y):
        return self._find(_zxy_to_id(z, x, y), self.root_dir, 0)

    def _find(self, tid, d, depth):
        if not d or depth > 5:
            return None
        lo, hi, idx = 0, len(d) - 1, -1
        while lo <= hi:
            mid = (lo + hi) // 2
            if d[mid][0] <= tid:
                idx = mid; lo = mid + 1
            else:
                hi = mid - 1
        if idx < 0:
            return None
        tid0, rl, off, ln = d[idx]
        if rl > 0:
            return self._raw(self._data_off + off, ln) if tid < tid0 + rl else None
        return self._find(tid, self._leaf(off, ln), depth + 1)

    def close(self):
        self.f.close()


# ═══════════════════════════════════════════════════════════════════════════
# Terrain-RGB → elevation (short)
# ═══════════════════════════════════════════════════════════════════════════

def decode_terrain(r, g, b, encoding):
    if encoding == 'mapbox':
        return -10000.0 + (r * 65536 + g * 256 + b) * 0.1
    return r * 256.0 + g + b / 256.0 - 32768.0


# ═══════════════════════════════════════════════════════════════════════════
# Web-Mercator projection helpers
# ═══════════════════════════════════════════════════════════════════════════

def lon_to_tx(lon, zoom):
    """Longitude → fractional tile-X at given zoom."""
    return (lon + 180.0) / 360.0 * (1 << zoom)


def lat_to_ty(lat, zoom):
    """Latitude → fractional tile-Y at given zoom."""
    r = math.radians(lat)
    return (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0 * (1 << zoom)


# ═══════════════════════════════════════════════════════════════════════════
# GeoTIFF output via VRT + gdal_translate
# ═══════════════════════════════════════════════════════════════════════════

def tile_filename(cell_lat, cell_lon):
    """GLO30Provider-compatible filename."""
    ns = 'N' if cell_lat >= 0 else 'S'
    ew = 'E' if cell_lon >= 0 else 'W'
    return f"Copernicus_DSM_COG_10_{ns}{abs(cell_lat):02d}_00_{ew}{abs(cell_lon):03d}_00_DEM"


def write_geotiff(data_bytes, cell_lat, cell_lon, output_dir):
    """Write raw Int16 elevation data as compressed GeoTIFF via gdal_translate."""
    name = tile_filename(cell_lat, cell_lon)
    out_path = os.path.join(output_dir, f"{name}.tif")
    pixel_size = 1.0 / TILE_SIZE

    with tempfile.TemporaryDirectory() as tmpdir:
        raw_path = os.path.join(tmpdir, "raw.bin")
        vrt_path = os.path.join(tmpdir, "raw.vrt")

        with open(raw_path, 'wb') as f:
            f.write(data_bytes)

        with open(vrt_path, 'w') as f:
            f.write(f'''<VRTDataset rasterXSize="{TILE_SIZE}" rasterYSize="{TILE_SIZE}">
  <SRS>EPSG:4326</SRS>
  <GeoTransform>{cell_lon}, {pixel_size:.15f}, 0, {cell_lat + 1}, 0, {-pixel_size:.15f}</GeoTransform>
  <VRTRasterBand dataType="Int16" band="1" subClass="VRTRawRasterBand">
    <SourceFilename relativeToVRT="1">raw.bin</SourceFilename>
    <ImageOffset>0</ImageOffset>
    <PixelOffset>2</PixelOffset>
    <LineOffset>{TILE_SIZE * 2}</LineOffset>
    <ByteOrder>LSB</ByteOrder>
    <NoDataValue>{NO_DATA}</NoDataValue>
  </VRTRasterBand>
</VRTDataset>''')

        subprocess.run([
            'gdal_translate', '-q',
            '-of', 'GTiff',
            '-co', 'COMPRESS=DEFLATE',
            '-co', 'PREDICTOR=2',
            vrt_path, out_path
        ], check=True)

    return out_path


# ═══════════════════════════════════════════════════════════════════════════
# Elevation generation for one 1°×1° cell
# ═══════════════════════════════════════════════════════════════════════════

def generate_cell(pm, zoom, cell_lat, cell_lon, encoding, tile_cache):
    """Build a 3600×3600 little-endian Int16 array for one 1°×1° cell."""

    n = 1 << zoom
    step = 1.0 / TILE_SIZE

    # Precompute row → (tile_y, pixel_fraction_y)
    row_info = [None] * TILE_SIZE
    for row in range(TILE_SIZE):
        lat = (cell_lat + 1) - (row + 0.5) * step  # pixel center
        ty = lat_to_ty(lat, zoom)
        ity = max(0, min(n - 1, int(math.floor(ty))))
        row_info[row] = (ity, ty - ity)

    # Precompute col → (tile_x, pixel_fraction_x)
    col_info = [None] * TILE_SIZE
    for col in range(TILE_SIZE):
        lon = cell_lon + (col + 0.5) * step  # pixel center
        tx = lon_to_tx(lon, zoom)
        itx = max(0, min(n - 1, int(math.floor(tx))))
        col_info[col] = (itx, tx - itx)

    # Collect unique tile coordinates — O(TILE_SIZE) not O(TILE_SIZE²)
    unique_ty = set(ity for ity, _ in row_info)
    unique_tx = set(itx for itx, _ in col_info)

    tile_px = tile_cache.get('_px', 0)
    for ty in unique_ty:
        for tx in unique_tx:
            if (tx, ty) in tile_cache:
                continue
            raw = pm.get(zoom, tx, ty)
            if raw is None:
                tile_cache[(tx, ty)] = None
            else:
                img = Image.open(io.BytesIO(raw)).convert('RGB')
                if tile_px == 0:
                    tile_px = img.size[0]
                    tile_cache['_px'] = tile_px
                tile_cache[(tx, ty)] = (img.tobytes(), img.size[0])

    if tile_px == 0:
        return None, True, False

    nd_bytes = struct.pack('<h', NO_DATA)
    data = bytearray(nd_bytes * (TILE_SIZE * TILE_SIZE))
    tp = tile_px - 1
    max_elev = -float('inf')
    has_data = False

    for row in range(TILE_SIZE):
        ity, fy = row_info[row]
        py = max(0, min(tp, int(round(fy * tp))))
        off_row = row * TILE_SIZE * 2

        for col in range(TILE_SIZE):
            itx, fx = col_info[col]
            cached = tile_cache.get((itx, ity))
            if cached is None:
                continue
            raw_bytes, tw = cached
            px = max(0, min(tp, int(round(fx * tp))))
            off = (py * tw + px) * 3
            r, g, b = raw_bytes[off], raw_bytes[off + 1], raw_bytes[off + 2]
            e = decode_terrain(r, g, b, encoding)
            val = max(-32768, min(32767, int(round(e))))
            struct.pack_into('<h', data, off_row + col * 2, val)
            has_data = True
            if e > max_elev:
                max_elev = e

    sea_level = has_data and max_elev <= 0
    return bytes(data), not has_data, sea_level


try:
    import numpy as np
    HAS_NUMPY = True
except ImportError:
    HAS_NUMPY = False


def generate_cell_numpy(pm, zoom, cell_lat, cell_lon, encoding, tile_cache):
    """Numpy-accelerated version — ~20x faster than pure Python."""

    n = 1 << zoom
    half = 0.5 / TILE_SIZE

    # Lat/lon grids (pixel centers)
    lats = np.linspace(cell_lat + 1 - half, cell_lat + half, TILE_SIZE)
    lons = np.linspace(cell_lon + half, cell_lon + 1 - half, TILE_SIZE)

    # Tile coordinates
    txf = (lons + 180.0) / 360.0 * n
    tile_xs = np.clip(np.floor(txf).astype(np.int32), 0, n - 1)
    frac_xs = txf - tile_xs

    lat_r = np.radians(lats)
    tyf = (1.0 - np.log(np.tan(lat_r) + 1.0 / np.cos(lat_r)) / np.pi) / 2.0 * n
    tile_ys = np.clip(np.floor(tyf).astype(np.int32), 0, n - 1)
    frac_ys = tyf - tile_ys

    # Load needed tiles as numpy arrays
    utx, uty = np.unique(tile_xs), np.unique(tile_ys)
    tile_px = tile_cache.get('_px', 0)
    for ty in uty:
        for tx in utx:
            k = (int(tx), int(ty))
            if k in tile_cache:
                continue
            raw = pm.get(zoom, k[0], k[1])
            if raw is None:
                tile_cache[k] = None
            else:
                img = Image.open(io.BytesIO(raw)).convert('RGB')
                tile_cache[k] = np.array(img, dtype=np.uint16)
                if tile_px == 0:
                    tile_px = img.size[0]
                    tile_cache['_px'] = tile_px

    if tile_px == 0:
        return None, True, False

    tp = tile_px - 1
    pxs = np.clip(np.round(frac_xs * tp).astype(np.int32), 0, tp)
    pys = np.clip(np.round(frac_ys * tp).astype(np.int32), 0, tp)

    elev = np.full((TILE_SIZE, TILE_SIZE), NO_DATA, dtype=np.float64)

    for ty_val in uty:
        row_mask = tile_ys == ty_val
        rows = np.where(row_mask)[0]
        if len(rows) == 0:
            continue
        py_vals = pys[rows]
        for tx_val in utx:
            col_mask = tile_xs == tx_val
            cols = np.where(col_mask)[0]
            if len(cols) == 0:
                continue
            arr = tile_cache.get((int(tx_val), int(ty_val)))
            if arr is None:
                continue
            px_vals = pxs[cols]
            sub = arr[np.ix_(py_vals, px_vals)]  # shape: (R, C, 3)
            r = sub[:, :, 0].astype(np.float64)
            g = sub[:, :, 1].astype(np.float64)
            b = sub[:, :, 2].astype(np.float64)
            if encoding == 'mapbox':
                e = -10000.0 + (r * 65536 + g * 256 + b) * 0.1
            else:
                e = r * 256.0 + g + b / 256.0 - 32768.0
            elev[np.ix_(rows, cols)] = e

    result = np.clip(np.round(elev), -32768, 32767).astype(np.int16)
    has_data = np.any(elev != NO_DATA)
    sea_level = has_data and np.max(elev[elev != NO_DATA]) <= 0
    # Native byte order (little-endian on x86) — matches VRT ByteOrder=LSB
    return result.tobytes(), not has_data, sea_level


# ═══════════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description='Convert PMTiles terrain-RGB to GeoTIFF files for GraphHopper GLO30Provider')
    parser.add_argument('input', help='Input .pmtiles file')
    parser.add_argument('output_dir', help='Output directory for .tif files')
    parser.add_argument('--zoom', type=int, default=11, help='Zoom level to extract (default: 11)')
    parser.add_argument('--encoding', choices=['mapbox', 'terrarium'], default='terrarium',
                        help='Terrain-RGB encoding (default: terrarium)')
    args = parser.parse_args()

    # Check for gdal_translate
    try:
        subprocess.run(['gdal_translate', '--version'], capture_output=True, check=True)
    except FileNotFoundError:
        print("ERROR: gdal_translate not found. Install GDAL:")
        print("  apt install gdal-bin   # Debian/Ubuntu")
        print("  brew install gdal      # macOS")
        sys.exit(1)

    os.makedirs(args.output_dir, exist_ok=True)

    pm = PMTiles(args.input)
    print(f"PMTiles: zoom {pm.min_zoom}-{pm.max_zoom}, "
          f"bounds [{pm.min_lon:.4f}, {pm.min_lat:.4f}] to [{pm.max_lon:.4f}, {pm.max_lat:.4f}]")
    print(f"Extracting zoom={args.zoom}, encoding={args.encoding}")
    if HAS_NUMPY:
        print("Using numpy-accelerated processing")
    else:
        print("WARNING: numpy not found — falling back to pure Python (much slower)")
        print("  Install with: pip install numpy")

    gen_fn = generate_cell_numpy if HAS_NUMPY else generate_cell

    # Determine 1°×1° cells that overlap the PMTiles bounds
    lat_lo = int(math.floor(pm.min_lat))
    lat_hi = int(math.floor(pm.max_lat))
    lon_lo = int(math.floor(pm.min_lon))
    lon_hi = int(math.floor(pm.max_lon))
    total = (lat_hi - lat_lo + 1) * (lon_hi - lon_lo + 1)
    print(f"Generating {total} tile(s): lat [{lat_lo},{lat_hi}] lon [{lon_lo},{lon_hi}]")

    tile_cache = {}
    count = 0
    total_bytes = 0
    written = 0
    skipped = 0
    t_start = time.monotonic()

    for cell_lat in range(lat_lo, lat_hi + 1):
        for cell_lon in range(lon_lo, lon_hi + 1):
            name = tile_filename(cell_lat, cell_lon)
            count += 1
            print(f"  [{count}/{total}] {name}.tif ...", end=' ', flush=True)

            t_cell = time.monotonic()
            data, no_data, sea_level = gen_fn(pm, args.zoom, cell_lat, cell_lon, args.encoding, tile_cache)
            cell_sec = time.monotonic() - t_cell

            # Free decoded tiles after each cell to keep memory bounded
            px_val = tile_cache.get('_px', 0)
            tile_cache.clear()
            if px_val:
                tile_cache['_px'] = px_val

            if no_data:
                skipped += 1
                print(f"skipped (no data)  [{cell_sec:.1f}s]")
                continue
            if sea_level:
                skipped += 1
                print(f"skipped (sea level)  [{cell_sec:.1f}s]")
                continue

            t_write = time.monotonic()
            out_path = write_geotiff(data, cell_lat, cell_lon, args.output_dir)
            write_sec = time.monotonic() - t_write

            file_bytes = os.path.getsize(out_path)
            total_bytes += file_bytes
            written += 1
            size_mb = file_bytes / (1024 * 1024)

            elapsed = time.monotonic() - t_start
            avg = elapsed / count
            eta = avg * (total - count)
            eta_min, eta_sec = divmod(int(eta), 60)

            print(f"{size_mb:.1f} MB  [{cell_sec:.1f}s + {write_sec:.1f}s]  "
                  f"total: {total_bytes / (1024*1024):.1f} MB  "
                  f"ETA: {eta_min}m{eta_sec:02d}s")

    elapsed_total = time.monotonic() - t_start
    em, es = divmod(int(elapsed_total), 60)
    pm.close()
    print(f"\nDone in {em}m{es:02d}s! "
          f"{written} files ({total_bytes / (1024*1024):.1f} MB), "
          f"{skipped} skipped")
    print(f"Output: {args.output_dir}/")
    print(f'Use in GraphHopper:  new GLO30Provider("{args.output_dir}")')


if __name__ == '__main__':
    main()
