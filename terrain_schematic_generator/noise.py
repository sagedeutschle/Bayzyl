import math
from typing import Tuple


def _hash2d(x: int, z: int, seed: int) -> int:
    n = x * 374761393 + z * 668265263 + seed * 1442695040888963407
    n = (n ^ (n >> 13)) * 1274126177
    return n ^ (n >> 16)


def _rand2d(x: int, z: int, seed: int) -> float:
    return (_hash2d(x, z, seed) & 0xFFFFFFFF) / 0xFFFFFFFF


def _fade(t: float) -> float:
    return t * t * t * (t * (t * 6 - 15) + 10)


def _lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def value_noise(x: float, z: float, seed: int) -> float:
    x0 = math.floor(x)
    z0 = math.floor(z)
    x1 = x0 + 1
    z1 = z0 + 1

    sx = _fade(x - x0)
    sz = _fade(z - z0)

    n00 = _rand2d(x0, z0, seed)
    n10 = _rand2d(x1, z0, seed)
    n01 = _rand2d(x0, z1, seed)
    n11 = _rand2d(x1, z1, seed)

    ix0 = _lerp(n00, n10, sx)
    ix1 = _lerp(n01, n11, sx)
    return _lerp(ix0, ix1, sz) * 2.0 - 1.0


def fractal_noise(x: float, z: float, seed: int, octaves: int, scale: float) -> float:
    value = 0.0
    amplitude = 1.0
    frequency = 1.0
    max_amp = 0.0
    for i in range(octaves):
        value += value_noise(x * scale * frequency, z * scale * frequency, seed + i * 1013) * amplitude
        max_amp += amplitude
        amplitude *= 0.5
        frequency *= 2.0
    if max_amp == 0:
        return 0.0
    return value / max_amp


def value_noise_3d(x: float, y: float, z: float, seed: int) -> float:
    x0 = math.floor(x)
    y0 = math.floor(y)
    z0 = math.floor(z)
    x1 = x0 + 1
    y1 = y0 + 1
    z1 = z0 + 1

    sx = _fade(x - x0)
    sy = _fade(y - y0)
    sz = _fade(z - z0)

    def r(ix: int, iy: int, iz: int) -> float:
        return (_hash2d(ix + iy * 131, iz + seed * 17, seed) & 0xFFFFFFFF) / 0xFFFFFFFF

    n000 = r(x0, y0, z0)
    n100 = r(x1, y0, z0)
    n010 = r(x0, y1, z0)
    n110 = r(x1, y1, z0)
    n001 = r(x0, y0, z1)
    n101 = r(x1, y0, z1)
    n011 = r(x0, y1, z1)
    n111 = r(x1, y1, z1)

    ix00 = _lerp(n000, n100, sx)
    ix10 = _lerp(n010, n110, sx)
    ix01 = _lerp(n001, n101, sx)
    ix11 = _lerp(n011, n111, sx)
    iy0 = _lerp(ix00, ix10, sy)
    iy1 = _lerp(ix01, ix11, sy)
    return _lerp(iy0, iy1, sz) * 2.0 - 1.0


def fractal_noise_3d(x: float, y: float, z: float, seed: int, octaves: int, scale: float) -> float:
    value = 0.0
    amplitude = 1.0
    frequency = 1.0
    max_amp = 0.0
    for i in range(octaves):
        value += value_noise_3d(x * scale * frequency, y * scale * frequency, z * scale * frequency, seed + i * 1013) * amplitude
        max_amp += amplitude
        amplitude *= 0.5
        frequency *= 2.0
    if max_amp == 0:
        return 0.0
    return value / max_amp
