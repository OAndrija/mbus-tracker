package com.mbus.app.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SidebarTest {

    @Test
    public void countsBusesInSlovene() {
        assertEquals("avtobus", Sidebar.buses(1));
        assertEquals("avtobusa", Sidebar.buses(2));
        assertEquals("avtobusi", Sidebar.buses(3));
        assertEquals("avtobusi", Sidebar.buses(4));
        assertEquals("avtobusov", Sidebar.buses(5));
        assertEquals("avtobusov", Sidebar.buses(11));
        assertEquals("avtobus", Sidebar.buses(101));
    }
}
