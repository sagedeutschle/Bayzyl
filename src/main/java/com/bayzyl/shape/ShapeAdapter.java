package com.bayzyl.shape;

import com.bayzyl.CylinderRequest;
import com.bayzyl.PyramidRequest;
import com.bayzyl.SphereRequest;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface ShapeAdapter {
    String getName();

    WorkEstimate estimateSphere(SphereRequest request);

    WorkEstimate estimateCylinder(CylinderRequest request);

    WorkEstimate estimatePyramid(PyramidRequest request);

    ShapeBounds sphereBounds(Location anchor, SphereRequest request);

    ShapeBounds cylinderBounds(Location anchor, CylinderRequest request);

    ShapeBounds pyramidBounds(Location anchor, PyramidRequest request);

    int createSphere(Player player, Location anchor, SphereRequest request) throws Exception;

    int createCylinder(Player player, Location anchor, CylinderRequest request) throws Exception;

    int createPyramid(Player player, Location anchor, PyramidRequest request) throws Exception;
}
