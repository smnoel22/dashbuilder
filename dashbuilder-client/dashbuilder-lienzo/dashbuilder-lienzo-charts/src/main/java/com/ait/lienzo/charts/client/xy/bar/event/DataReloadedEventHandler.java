package com.ait.lienzo.charts.client.xy.bar.event;

import com.google.gwt.event.shared.EventHandler;

public interface DataReloadedEventHandler extends EventHandler
{
    void onDataReloaded(DataReloadedEvent event);
}