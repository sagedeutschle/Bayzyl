package com.bayzyl.gen;

import com.bayzyl.gen.generators.BasinGenerator;
import com.bayzyl.gen.generators.BoulderGenerator;
import com.bayzyl.gen.generators.CaveGenerator;
import com.bayzyl.gen.generators.CliffGenerator;
import com.bayzyl.gen.generators.DunesGenerator;
import com.bayzyl.gen.generators.ErodeGenerator;
import com.bayzyl.gen.generators.GenBrushGenerator;
import com.bayzyl.gen.generators.MesaGenerator;
import com.bayzyl.gen.generators.PeakGenerator;
import com.bayzyl.gen.generators.PlateauGenerator;
import com.bayzyl.gen.generators.RavineGenerator;
import com.bayzyl.gen.generators.RidgeGenerator;
import com.bayzyl.gen.generators.ScreeGenerator;
import com.bayzyl.gen.generators.ValleyGenerator;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lookup table from {@link GenBrushType} to the concrete generator.
 * Wired in {@link com.bayzyl.Bayzyl#onEnable} so the service can resolve
 * a type without each call doing a switch over every enum constant.
 */
public final class GenBrushRegistry {
    private final Map<GenBrushType, GenBrushGenerator> generators = new EnumMap<>(GenBrushType.class);

    public GenBrushRegistry() {
        register(GenBrushType.RIDGE, new RidgeGenerator());
        register(GenBrushType.PLATEAU, new PlateauGenerator());
        register(GenBrushType.VALLEY, new ValleyGenerator());
        register(GenBrushType.BASIN, new BasinGenerator());
        register(GenBrushType.ERODE, new ErodeGenerator());
        register(GenBrushType.RAVINE, new RavineGenerator());
        register(GenBrushType.CAVE, new CaveGenerator());
        register(GenBrushType.DUNES, new DunesGenerator());
        register(GenBrushType.MESA, new MesaGenerator());
        register(GenBrushType.PEAK, new PeakGenerator());
        register(GenBrushType.CLIFF, new CliffGenerator());
        register(GenBrushType.BOULDER, new BoulderGenerator());
        register(GenBrushType.SCREE, new ScreeGenerator());
    }

    public GenBrushGenerator get(GenBrushType type) {
        return generators.get(type);
    }

    private void register(GenBrushType type, GenBrushGenerator generator) {
        generators.put(type, generator);
    }
}
