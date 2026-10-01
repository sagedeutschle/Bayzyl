package com.bayzyl.detail;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DetailBrushCodeCodecSafetyTest {
    private final DetailBrushPresetRegistry registry = new DetailBrushPresetRegistry();
    private final DetailBrushSafety safety = new DetailBrushSafety(registry);
    private final DetailBrushCodeCodec codec = new DetailBrushCodeCodec(safety);

    @Test
    void validStrokeRoundTripsAndShortLegacyCodesFillTrailingDefaults() {
        DetailBrushSettings original = new DetailBrushSettings(
                "cloud", new DetailBrushParameters(Map.of("tint", "storm", "volume", "12")),
                DetailBrushMode.STROKE);
        DetailBrushSettings roundTrip = codec.decode(codec.encode(original));
        assertEquals(DetailBrushMode.STROKE, roundTrip.mode());
        assertEquals("storm", roundTrip.parameters().get("tint", ""));
        assertEquals("12", roundTrip.parameters().get("volume", ""));

        DetailBrushSettings canonicalRoundTrip = codec.decode(codec.encode(new DetailBrushSettings(
                "CLOUD", new DetailBrushParameters(Map.of("tint", "  STORM  ")), DetailBrushMode.STAMP)));
        assertEquals("storm", canonicalRoundTrip.parameters().get("tint", ""));

        DetailBrushSettings legacy = codec.decode("F1T(0.7,8)");
        assertEquals(DetailBrushMode.STROKE, legacy.mode());
        assertEquals("8", legacy.parameters().get("height", ""));
        assertEquals("3", legacy.parameters().get("width", ""));
    }

    @Test
    void encodeAndDecodeRejectInvalidSchemaAndNonfiniteOrUnknownValues() {
        DetailBrushSettings invalid = new DetailBrushSettings(
                "flame", new DetailBrushParameters(Map.of("heat", "NaN")), DetailBrushMode.STAMP);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(invalid));

        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("F2S(NaN,8,3,0.4,0,0,0.85)"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("F2S(Infinity,8,3,0.4,0,0,0.85)"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("C2S(6,0.6,0.7,0.35,0.72,invisible)"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("F2X(0.7,8,3,0.4,0,0,0.85)"));
    }

    @Test
    void decoderBoundsRawCodeAndPayloadBeforeMaterializingValues() {
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("F2S(" + "x".repeat(8_193) + ")"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("F2S(" + "x".repeat(DetailBrushSafety.PARAMETER_TEXT_MAX + 1) + ")"));
    }
}
