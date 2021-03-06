/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dashbuilder.renderer.lienzo.client;

import com.ait.lienzo.charts.client.core.xy.line.LineChart;
import com.ait.lienzo.client.core.animation.AnimationTweener;

public class LienzoLineChartDisplayerView
        extends LienzoXYChartDisplayerView<LienzoLineChartDisplayer,LineChart>
        implements LienzoLineChartDisplayer.View {

    @Override
    protected LineChart _createChart() {
        LineChart chart = new LineChart();
        chart.setData(createChartData());
        return chart;
    }

    @Override
    protected void _reloadChart(LineChart chart) {
        chart.reload(createChartData(), AnimationTweener.LINEAR, ANIMATION_DURATION);
    }
}
