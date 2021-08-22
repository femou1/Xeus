/*
 * Copyright (c) 2019.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders.metrics.routes;

import com.pinewoodbuilders.contracts.metrics.SparkRoute;
import com.pinewoodbuilders.metrics.PrometheusMetricsServlet;
import spark.Request;
import spark.Response;

public class GetMetrics extends SparkRoute {

    private static final PrometheusMetricsServlet metricsServlet = new PrometheusMetricsServlet();

    @Override
    public Object handle(Request request, Response response) throws Exception {
        return metricsServlet.servletGet(request.raw(), response.raw());
    }
}
