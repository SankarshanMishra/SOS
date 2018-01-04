/*
 * Copyright (C) 2012-2018 52°North Initiative for Geospatial Open Source
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
package org.n52.sos.ds.hibernate.cache.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.sos.ds.hibernate.cache.AbstractThreadableDatasourceCacheUpdate;
import org.n52.sos.ds.hibernate.dao.DaoFactory;
import org.n52.sos.ds.hibernate.dao.observation.AbstractObservationDAO;
import org.n52.sos.ds.hibernate.util.TimeExtrema;

/**
 *
 * @author <a href="mailto:c.autermann@52north.org">Christian Autermann</a>
 *
 * @since 4.0.0
 */
public class ObservationTimeCacheUpdate extends AbstractThreadableDatasourceCacheUpdate {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationTimeCacheUpdate.class);

    private final DaoFactory daoFactory;

    public ObservationTimeCacheUpdate(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    @Override
    public void execute() {
        LOGGER.debug("Executing ObservationTimeCacheUpdate");
        startStopwatch();
        try {
            AbstractObservationDAO observationDAO = daoFactory.getObservationDAO();
            TimeExtrema timeExtrema = observationDAO.getObservationTimeExtrema(getSession());
            if (timeExtrema != null) {
                getCache().setMinPhenomenonTime(timeExtrema.getMinPhenomenonTime());
                getCache().setMaxPhenomenonTime(timeExtrema.getMaxPhenomenonTime());
                getCache().setMinResultTime(timeExtrema.getMinResultTime());
                getCache().setMaxResultTime(timeExtrema.getMaxResultTime());
            }
        } catch (OwsExceptionReport ce) {
            getErrors().add(ce);
        }
        LOGGER.debug("Finished executing ObservationTimeCacheUpdate ({})", getStopwatchResult());
    }

}
