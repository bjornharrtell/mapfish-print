/*
 * Copyright (C) 2014  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.map.image;

import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.GridCoverageLayer;
import org.geotools.map.Layer;
import org.geotools.styling.Style;
import org.mapfish.print.attribute.map.MapTransformer;
import org.mapfish.print.map.geotools.AbstractGeotoolsLayer;
import org.mapfish.print.map.geotools.StyleSupplier;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Common implementation for layers that are represented as a single grid coverage image.
 *
 * @author Jesse on 4/10/2014.
 */
public abstract class AbstractSingleImageLayer extends AbstractGeotoolsLayer {

    private final StyleSupplier<GridCoverage2D> styleSupplier;
    private volatile GridCoverageLayer layer;

    /**
     * Constructor.
     *
     * @param executorService the thread pool for doing the rendering.
     * @param styleSupplier     the style to use when drawing the constructed grid coverage on the map.
     */
    protected AbstractSingleImageLayer(final ExecutorService executorService,
                                       final StyleSupplier<GridCoverage2D> styleSupplier) {
        super(executorService);
        this.styleSupplier = styleSupplier;
    }

    @Override
    protected final List<? extends Layer> getLayers(final ClientHttpRequestFactory httpRequestFactory,
                                                    final MapTransformer transformer,
                                                    final boolean isFirstLayer) {
        if (this.layer == null) {
            synchronized (this) {
                if (this.layer == null) {
                    BufferedImage image;
                    try {
                        image = loadImage(httpRequestFactory, transformer, isFirstLayer);
                    } catch (RuntimeException throwable) {
                        throw throwable;
                    } catch (Error e) {
                        throw e;
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }

                    final ReferencedEnvelope mapEnvelope = transformer.getBounds().toReferencedEnvelope(transformer.getPaintArea(),
                            transformer.getDPI());

                    GridCoverageFactory factory = CoverageFactoryFinder.getGridCoverageFactory(null);
                    GeneralEnvelope gridEnvelope = new GeneralEnvelope(mapEnvelope.getCoordinateReferenceSystem());
                    gridEnvelope.setEnvelope(mapEnvelope.getMinX(), mapEnvelope.getMinY(), mapEnvelope.getMaxX(), mapEnvelope.getMaxY());
                    final GridCoverage2D gridCoverage2D = factory.create(getClass().getSimpleName(), image, gridEnvelope, null, null,
                            null);
                    Style style = this.styleSupplier.load(httpRequestFactory, gridCoverage2D);
                    this.layer = new GridCoverageLayer(gridCoverage2D, style);
                }
            }
        }
        return Collections.singletonList(this.layer);
    }

    /**
     * Load the image at the requested size for the provided map bounds.
     *  @param requestFactory the factory to use for making http requests
     * @param transformer object containing map rendering information
     * @param isFirstLayer true indicates this layer is the first layer in the map (the first layer drawn, ie the base layer)
     */
    protected abstract BufferedImage loadImage(ClientHttpRequestFactory requestFactory,
                                               MapTransformer transformer,
                                               boolean isFirstLayer) throws Throwable;
}
