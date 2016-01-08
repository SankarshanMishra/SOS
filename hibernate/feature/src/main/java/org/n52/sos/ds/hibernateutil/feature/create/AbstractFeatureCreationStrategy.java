/**
 * Copyright (C) 2012-2016 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.sos.ds.hibernateutil.feature.create;

import java.util.List;
import java.util.Locale;

import org.hibernate.Session;
import org.n52.sos.ds.I18NDAO;
import org.n52.sos.ds.hibernate.dao.DaoFactory;
import org.n52.sos.ds.hibernate.dao.FeatureOfInterestDAO;
import org.n52.sos.ds.hibernate.entities.FeatureOfInterest;
import org.n52.sos.i18n.I18NDAORepository;
import org.n52.sos.i18n.LocalizedString;
import org.n52.sos.i18n.metadata.I18NFeatureMetadata;
import org.n52.sos.ogc.gml.AbstractFeature;
import org.n52.sos.ogc.ows.OwsExceptionReport;
import org.n52.sos.service.ServiceConfiguration;
import org.n52.sos.util.CollectionHelper;
import org.n52.sos.util.GeometryHandler;
import org.n52.sos.util.JTSHelper;
import org.n52.sos.util.JavaHelper;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;

public abstract class AbstractFeatureCreationStrategy implements FeatureCreationStrategy {

    private int storageEPSG;
    private int storage3DEPSG;

    public AbstractFeatureCreationStrategy(int storageEPSG, int storage3DEPSG) {
        this.storageEPSG = storageEPSG;
        this.storage3DEPSG = storage3DEPSG;
        
    }

    protected void addNameAndDescription(Locale requestedLocale, FeatureOfInterest feature,
            AbstractFeature abstractFeature, FeatureOfInterestDAO featureDAO) throws OwsExceptionReport {
        I18NDAO<I18NFeatureMetadata> i18nDAO = I18NDAORepository.getInstance().getDAO(I18NFeatureMetadata.class);
        // set name as human readable identifier if set
        if (feature.isSetName()) {
            abstractFeature.setHumanReadableIdentifier(feature.getName());
        }
        if (i18nDAO == null) {
            // no i18n support
            abstractFeature.addName(featureDAO.getName(feature));
            abstractFeature.setDescription(featureDAO.getDescription(feature));
        } else {
            I18NFeatureMetadata i18n = i18nDAO.getMetadata(feature.getIdentifier());
            if (requestedLocale != null) {
                // specific locale was requested
                Optional<LocalizedString> name = i18n.getName().getLocalizationOrDefault(requestedLocale);
                if (name.isPresent()) {
                    abstractFeature.addName(name.get().asCodeType());
                }
                Optional<LocalizedString> description =
                        i18n.getDescription().getLocalizationOrDefault(requestedLocale);
                if (description.isPresent()) {
                    abstractFeature.setDescription(description.get().getText());
                }
            } else {
                if (ServiceConfiguration.getInstance().isShowAllLanguageValues()) {
                    for (LocalizedString name : i18n.getName()) {
                        abstractFeature.addName(name.asCodeType());
                    }
                } else {
                    Optional<LocalizedString> name = i18n.getName().getDefaultLocalization();
                    if (name.isPresent()) {
                        abstractFeature.addName(name.get().asCodeType());
                    }
                }
                // choose always the description in the default locale
                Optional<LocalizedString> description = i18n.getDescription().getDefaultLocalization();
                if (description.isPresent()) {
                    abstractFeature.setDescription(description.get().getText());
                }
            }
        }
    }
    
    /**
     * Get the geometry from featureOfInterest object.
     *
     * @param feature
     * @return geometry
     * @throws OwsExceptionReport
     */
    @Override
    public Geometry createGeometry(final FeatureOfInterest feature, Session session) throws OwsExceptionReport {
        if (feature.isSetGeometry()) {
            return GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(feature.getGeom());
        } else if (feature.isSetLongLat()) {
            int epsg = storageEPSG;
            if (feature.isSetSrid()) {
                epsg = feature.getSrid();
            }
            final String wktString =
                    GeometryHandler.getInstance().getWktString(feature.getLongitude(), feature.getLatitude(), epsg);
            final Geometry geom = JTSHelper.createGeometryFromWKT(wktString, epsg);
            if (feature.isSetAltitude()) {
                geom.getCoordinate().z = JavaHelper.asDouble(feature.getAltitude());
                if (geom.getSRID() == storage3DEPSG) {
                    geom.setSRID(storage3DEPSG);
                }
            }
            return geom;
            // return
            // GeometryHandler.getInstance().switchCoordinateAxisOrderIfNeeded(geom);
        } else {
            if (session != null) {
                List<Geometry> geometries = DaoFactory.getInstance().getObservationDAO().getSamplingGeometries(feature.getIdentifier(), session);
                int srid = GeometryHandler.getInstance().getStorageEPSG();
                if (!CollectionHelper.nullEmptyOrContainsOnlyNulls(geometries)) {
                    List<Coordinate> coordinates = Lists.newLinkedList();
                    Geometry lastGeoemtry = null;
                    for (Geometry geometry : geometries) {
                        if (geometry != null && (lastGeoemtry == null || !geometry.equalsTopo(lastGeoemtry))) {
                                coordinates.add(GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(geometry).getCoordinate());
                            lastGeoemtry = geometry;
                            if (geometry.getSRID() != srid) {
                                srid = geometry.getSRID();
                             }
                        }
                        if (geometry.getSRID() != srid) {
                           srid = geometry.getSRID();
                        }
                        if (!geometry.equalsTopo(lastGeoemtry)) {
                            coordinates.add(GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(geometry).getCoordinate());
                            lastGeoemtry = geometry;
                        }
                    }
                    GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), srid);
                    if (coordinates.size() == 1) {
                        return geometryFactory.createPoint(coordinates.iterator().next());
                    } else {
                        return geometryFactory.createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
                    }
                }
            }
        }
        return null;
    }
    
}
